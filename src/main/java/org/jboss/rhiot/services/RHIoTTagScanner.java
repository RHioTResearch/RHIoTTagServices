package org.jboss.rhiot.services;

import org.jboss.rhiot.ble.bluez.AdEventInfo;
import org.jboss.rhiot.ble.bluez.AdStructure;
import org.jboss.rhiot.ble.bluez.HCIDump;
import org.jboss.rhiot.ble.bluez.IAdvertEventCallback;
import org.jboss.rhiot.ble.bluez.RHIoTTag;
import org.jboss.rhiot.services.api.IGatewayTagConfig;
import org.jboss.rhiot.services.api.IRHIoTTagScanner;
import org.jboss.rhiot.services.fsm.GameModel;
import org.jboss.rhiot.services.fsm.GameStateMachine;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentException;
import org.eclipse.kura.cloud.CloudClient;
import org.eclipse.kura.cloud.CloudClientListener;
import org.eclipse.kura.cloud.CloudService;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.message.KuraPayload;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.StateMachineConfiguration;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.ByteOrder;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * The main entry point for the scanner facade on top of the HCIDump general scanner that extracts RHIoTTag specific
 * advertising events and
 */
public class RHIoTTagScanner implements ConfigurableComponent, CloudClientListener, IRHIoTTagScanner, IAdvertEventCallback {
   private static final Logger log = LoggerFactory.getLogger(RHIoTTagScanner.class);

   private static final String APP_ID = "org.jboss.rhiot.services.RHIoTTagScanner";
   private static final String PUBLISH_TOPIC_PROP_NAME = "publish.semanticTopic";
   private static final String PUBLISH_QOS_PROP_NAME = "publish.qos";
   private static final String PUBLISH_RETAIN_PROP_NAME = "publish.retain";
   private static final int MAX_TAGS = 9;
   /** The configuration properties passed in during activation */
   private Map<String, Object> properties;
   /** The mapping from the tag BLE address to a user assigned name */
   private Map<String, String> addressToNameMap;
   /** The game state machine for each tag associated with the gateway */
   private Map<String, GameStateMachine> tagStateMachines;
   /** The ExecutorService for the RHIoTTag event processing */
   private ExecutorService publisher;
   /** Flag indicating if the scanner has been initialized */
   private volatile boolean scannerInitialized;
   /** The length of the game in seconds */
   private int gameDurationSecs;
   /** The length of the shooting window in seconds */
   private int shootingWindowSecs;
   /** */
   private HighScore highScore;
   /** The minimum raw lux value needed for a hit */
   private int luxHitThreshold = 20000;
   /** The maximum raw lux value the sensor needs to fall below to reset the last hit */
   private int luxResetThreshold = 10000;
   /** ESF cloud service */
   private CloudService cloudService;
   /** Client connection to the cloud service */
   private CloudClient cloudClient;
   private IGatewayTagConfig tagConfig;
   /** Servlet used for REST and debugging */
   private RHIoTServlet servlet;
   private FileWriter debugWriter;
   private String debugAddress = "A0:E6:F8:AD:2E:82";

   public void setCloudService(CloudService cloudService) {
      this.cloudService = cloudService;
      info("setCloudService, cs=%s\n", cloudService);
   }

   public void unsetCloudService(CloudService cloudService) {
      if(cloudClient != null)
         cloudClient.release();
      this.cloudService = null;
      this.cloudClient = null;
      info("unsetCloudService\n");
   }

   public void unsetHttpService(HttpService httpService) {
      httpService.unregister("/rhiot");
   }

   public void setHttpService(HttpService httpService) {
      servlet = new RHIoTServlet(this);
      try {
         HttpContext ctx = httpService.createDefaultHttpContext();
         httpService.registerServlet("/rhiot", servlet, null, ctx);
         info("Listening at /rhiot for REST requests");
      } catch (Exception e) {
         e.printStackTrace();
      }
   }

   public void setGatewayTagConfig(IGatewayTagConfig tagConfig) {
      this.tagConfig = tagConfig;
      info("setGatewayTagConfig");
   }
   public void unsetGatewayTagConfig(IGatewayTagConfig tagConfig) {
      this.tagConfig = null;
      info("unsetGatewayTagConfig");
   }
   public void updatedGatewayTagConfig(IGatewayTagConfig tagConfig) {
      info("updatedGatewayTagConfig");
      this.tagConfig = tagConfig;
      if(tagConfig != null) {
         info("Populating tag mappings from tagConfig");
         for(int n = 0; n < 9; n ++) {
            String address = tagConfig.getTagAddress(n);
            String name = tagConfig.getTagName(n);
            if(address != null && name != null)
               updateTagInfo(address, name);
         }
      }
   }

   public void updateTagInfo(String address, String name) {
      addressToNameMap.put(address, name);
      info("Updated name for: %s to: %s", address, name);
   }

   public Map<String, String> getTags() {
      return addressToNameMap;
   }

   public String getTagInfo(String address) {
      return addressToNameMap.get(address);
   }

   /**
    * Gets the current game state for the
    * @param tagAddress
    * @return name of current game state
    */
   public String getAndPublishGameSMInfo(String tagAddress) {
      GameStateMachine.GameState state = publishGameState(tagAddress);
      return state.name();
   }

   public GameStateMachine getGameSM(String tagAddress) {
      GameStateMachine gsm = tagStateMachines.get(tagAddress);
      if(gsm == null) {
         gsm = newStateMachine();
         tagStateMachines.put(tagAddress, gsm);
      }

      return gsm;
   }

   @Override
   public void onConnectionEstablished() {
      info("onConnectionEstablished\n");
   }

   @Override
   public void onConnectionLost() {
      info("onConnectionLost\n");
   }

   @Override
   public void onControlMessageArrived(String deviceId, String appTopic, KuraPayload msg, int qos, boolean retain) {
      info("onControlMessageArrived(devicdId=%s, appTopic=%s, qos=%d, retain=%s, msg=%s\n", deviceId, appTopic, qos, retain, msg.metrics());
   }

   @Override
   public void onMessageArrived(String deviceId, String appTopic, KuraPayload msg, int qos, boolean retain) {
      info("onMessageArrived(devicdId=%s, appTopic=%s, qos=%d, retain=%s, msg=%s\n", deviceId, appTopic, qos, retain, msg);
   }

   @Override
   public void onMessageConfirmed(int messageId, String appTopic) {
      debug("onMessageConfirmed(%s,%s)\n", messageId, appTopic);
   }

   @Override
   public void onMessagePublished(int messageId, String appTopic) {
      debug("onMessagePublished(%s,%s)\n", messageId, appTopic);
   }

   /**
    * Called in response to a BLE advertising event being seen on the stack. Any advertising event that corresponds
    * to the RHIoTTag event that has a name assigned to it is forwarded to the event queue for analysis of game
    * events.
    *
    * @param info - the advertising event information
    * @return true if the scanning should stop, this always returns false
    * @see #handleTag(RHIoTTag)
    */
   @Override
   public boolean advertEvent(AdEventInfo info) {
      boolean debug = log.isDebugEnabled();
      if (debug)
         debug("+++ advertEvent(%s), count=%d, rssi=%d, time=%s\n", info.getBDaddrAsString(), info.getCount(), info.getRssi(), new Date(info.getTime()));
      if (log.isTraceEnabled()) {
         // Dump out all AD structures
         for (AdStructure ads : info.getData()) {
            log.trace(ads.toString());
         }
      }
      RHIoTTag tag = RHIoTTag.create(info);
      if (tag != null) {
         // Get the user assigned name
         String key = tag.getAddressString();
         String name = addressToNameMap != null ? addressToNameMap.get(key) : null;
         tag.setName(name);
         if(debug)
            debug("%s", tag.toFullString());
         if (name != null) {
            CompletableFuture<GameStateMachine.GameState> future = CompletableFuture.supplyAsync(() -> handleTag(tag), publisher);
         } else if(debug) {
            debug("No name for: %s", tag);
         }
      }
      return false;
   }

   /**
    * Called to activate the scanner service
    *
    * @param componentContext - the OSGi component context
    * @param properties       - the service's configurable properties
    */
   protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
      info("RHIoTTagScanner.activate; Bundle has started with: %s\n", properties.entrySet());
      this.properties = properties;
      info("hciDev=%s\n", properties.get("hciDev"));

      addressToNameMap = new ConcurrentHashMap<>();
      tagStateMachines = new ConcurrentHashMap<>();

      try {
         // Acquire a Cloud Application Client for this Application
         if (cloudService != null) {
            info("Getting CloudClient for %s...", APP_ID);
            cloudClient = cloudService.newCloudClient(APP_ID);
            cloudClient.addCloudClientListener(this);
            cloudClient.subscribe("control/#", 1);
            info("Subscribed to control/#");
            debugWriter = new FileWriter("/tmp/tag.debug");
            debugWriter.write("address=");
            debugWriter.write(debugAddress);
            debugWriter.write('\n');
            info("Created /tmp/tag.debug");
         } else {
            info("No CloudService found\n");
         }
      } catch (Exception e) {
         info("Error during component activation, %s", e);
         throw new ComponentException(e);
      }

      // Create an executor to handle tag events
      publisher = Executors.newSingleThreadExecutor();

      if(tagConfig != null) {
         info("Populating tag mappings from tagConfig");
         for(int n = 0; n < MAX_TAGS; n ++) {
            String address = tagConfig.getTagAddress(n);
            String name = tagConfig.getTagName(n);
            if(address != null && name != null)
               updateTagInfo(address, name);
         }
      }

      // Update the properties
      updated(properties);
   }

   protected void deactivate(ComponentContext componentContext) {
      HCIDump.setAdvertEventCallback(null);
      HCIDump.freeScanner();
      scannerInitialized = false;
      addressToNameMap = null;
      tagStateMachines = null;
      info("RHIoTTagScanner.deactivate; Bundle " + APP_ID + " has stopped!\n");
   }

   /**
    * Called to update the service configurable properties
    * @param properties
    */
   protected void updated(Map<String, Object> properties) {
      info("RHIoTTagScanner.updated; Bundle " + APP_ID + " has updated!\n");
      String hciDev = (String) properties.get("hciDev");
      if (hciDev == null)
         hciDev = "hci0";
      boolean debugMode = false;
      if (properties.get("hcidumpDebugMode") != null)
         debugMode = (Boolean) properties.get("hcidumpDebugMode");
      boolean skipJniInitialization = false;
      if (properties.get("skipJniInitialization") != null)
         skipJniInitialization = (Boolean) properties.get("skipJniInitialization");
      info("hciDev=%s\n", hciDev);

      luxHitThreshold = (int) properties.get("game.hitThreshold");
      info("Using luxHitThreshold=%d", luxHitThreshold);
      luxResetThreshold = (int) properties.get("game.resetThreshold");
      info("Using luxResetThreshold=%d", luxResetThreshold);
      debugAddress = (String) properties.get("debug.address");

      gameDurationSecs = (int) properties.get("game.duration");
      shootingWindowSecs = (int) properties.get("game.shootingWindow");
      // Clear any games
      tagStateMachines.clear();

      this.properties = properties;
      if (properties != null && !properties.isEmpty()) {
         Iterator<Map.Entry<String, Object>> it = properties.entrySet().iterator();
         while (it.hasNext()) {
            Map.Entry<String, Object> entry = it.next();
            String key = entry.getKey();
            Object value = entry.getValue();
            String type = value != null ? value.getClass().getName() : "none";
            info("New property - %s = %s of type: %s\n", key, value, type);
         }
      }
      // Setup scanner
      if(!skipJniInitialization) {
         HCIDump.loadLibrary();
         HCIDump.enableDebugMode(debugMode);
         HCIDump.setAdvertEventCallback(this);
         if (scannerInitialized == false) {
            HCIDump.initScanner(hciDev, 512, ByteOrder.BIG_ENDIAN);
            info("Initialized scanner\n");
         } else {
            HCIDump.freeScanner();
            HCIDump.initScanner(hciDev, 512, ByteOrder.BIG_ENDIAN);
            info("Reinitialized scanner\n");
         }
      }
      scannerInitialized = true;
   }

   private void debug(String format, Object... args) {
      String msg = String.format(format, args);
      log.debug(msg);
   }
   private void info(String format, Object... args) {
      String msg = String.format(format, args);
      log.info(msg);
   }

   /**
    * Create a new game state machine
    * @return
    */
   private GameStateMachine newStateMachine() {
      GameModel gameModel = new GameModel();
      gameModel.setGameDuration(gameDurationSecs);
      gameModel.setShootingWindowDuration(shootingWindowSecs);

      StateMachineBuilder<GameStateMachine, GameStateMachine.GameState, GameStateMachine.GameEvent, GameModel> builder =
         StateMachineBuilderFactory.create(GameStateMachine.class, GameStateMachine.GameState.class, GameStateMachine.GameEvent.class, GameModel.class);
      builder.setStateMachineConfiguration(StateMachineConfiguration.create().enableRemoteMonitor(true).enableDebugMode(false));

      GameStateMachine gsm = builder.newStateMachine(GameStateMachine.GameState.IDLE);
      gsm.setGameModel(gameModel);
      return gsm;
   }

   /**
    * Determine the game event from the tag and current state. This advances the state machine to the next
    * state
    * @param gsm - the game state machine
    * @param tag - the tag ble event information
    * @return the state machine event
    */
   private GameStateMachine.GameEvent determineEvent(GameStateMachine gsm, RHIoTTag tag) {
      GameStateMachine.GameEvent event = GameStateMachine.GameEvent.NOOP;

      GameStateMachine.GameState state = gsm.getCurrentState();
      debug("determineEvent([%s]: state=%s, keyState=%s", tag.getAddressString(), state, tag.getKeyState());

      // Check game expiration first of all
      if(state != GameStateMachine.GameState.GAMEOVER && state != GameStateMachine.GameState.IDLE) {
         if(gsm.isGameExpired())
            return GameStateMachine.GameEvent.GAME_TIMEOUT;
      }

      // In shooting state, check for hits, timeout and empty clip
      if(state == GameStateMachine.GameState.SHOOTING) {
         if(gsm.isShootingWindowExpired())
            return GameStateMachine.GameEvent.WINDOW_TIMEOUT;
         if(gsm.isClipEmpty())
            return GameStateMachine.GameEvent.CLIP_EMPTY;
         if(tag.isLightSensorAbove(luxHitThreshold)) {
            // Decrement the shots left and update the game score
            gsm.tookShot();
            gsm.recordHit(tag.getLux());
            if(tag.getAddressString().equals(debugAddress)) {
               String msg = String.format("%s: lux=%d, hs=%d, s=%d\n", tag.getName(), tag.getLux(), gsm.getHitScore(), gsm.getScore());
               try {
                  debugWriter.write(msg);
                  debugWriter.flush();
               } catch (Exception e) {
                  e.printStackTrace();
               }
            }
            info("hit, shotsLeft=%s", gsm.getShotsLeft());
            return GameStateMachine.GameEvent.HIT_DETECTED;
         }
      }
      // In reset must wait for light sensor to drop back down
      if(state == GameStateMachine.GameState.RESETTING) {
         if(!tag.isLightSensorAbove(luxResetThreshold))
            return GameStateMachine.GameEvent.LS_RESET;
         return GameStateMachine.GameEvent.NOOP;
      }

      // Check key states next
      switch (tag.getKeyState()) {
         case NONE:
         case REED:
            break;
         case LEFT:
            event = GameStateMachine.GameEvent.LEFT_PRESSED;
            break;
         case RIGHT:
            event = GameStateMachine.GameEvent.RIGHT_PRESSED;
            break;
         case LEFT_AND_RIGHT:
            event = GameStateMachine.GameEvent.LEFT_RIGHT_PRESSED;
            break;
      }

      return event;
   }

   /**
    * Handle the tag ble event information asynchronously
    * @param tag - ble event information
    * @return the future for the handleTag result
    * @see #handleTag(RHIoTTag)
    */
   public CompletableFuture<GameStateMachine.GameState> handleTagAsync(RHIoTTag tag) {
      CompletableFuture<GameStateMachine.GameState> future = CompletableFuture.supplyAsync(() -> handleTag(tag), publisher);
      return future;
   }

   /**
    * Handle the tag ble event information. This finds or creates a game state machine for the tag and then
    * determines the game event and advances the game state machine.
    * @param tag - the ble event information
    * @return the current state of the tag's game
    */
   GameStateMachine.GameState handleTag(RHIoTTag tag) {
      // Check the tag state machine
      String tagKey = tag.getAddressString();
      GameStateMachine gsm = tagStateMachines.get(tagKey);
      if(gsm == null) {
         gsm = newStateMachine();
         gsm.start();
         tagStateMachines.put(tagKey, gsm);
      }

      // Check for an event based on the tag data and game model
      GameStateMachine.GameState state = gsm.getCurrentState();
      GameStateMachine.GameEvent event = determineEvent(gsm, tag);
      // Advance the state machine if there is a new event
      if(event != GameStateMachine.GameEvent.NOOP) {
         gsm.fire(event);
      }
      GameStateMachine.GameState newState = gsm.getCurrentState();
      if(tagKey.equals(debugAddress)) {
         String msg = String.format("%s,keys=%d,lux=%d,state=%s,event=%s,newState=%s\n", tag.getName(), tag.getKeys(), tag.getLux(), state, event, newState);
         try {
            debugWriter.write(msg);
            debugWriter.flush();
         } catch (IOException e) {
            e.printStackTrace();
         }
      }

      // Publish the tag data and game state
      doPublish(tag, state, newState, event, gsm);
      return newState;
   }

   /**
    * Publish the RHIoTTag temp, keys state and light sensor reading as well as game state changes. The game state
    * is not included for event of type NOOP.
    *  @param tag - the advertisement
    * @param state - the game state before the event
    * @param newState - the game state after the event
    * @param event - the event associated with the advertisement
    * @param gsm - the game state machine associated with the tag
    * @param gsm
    */
   private void doPublish(RHIoTTag tag, GameStateMachine.GameState state, GameStateMachine.GameState newState,
                          GameStateMachine.GameEvent event, GameStateMachine gsm) {
      // fetch the publishing configuration from the publishing properties
      String topicRoot = (String) properties.get(PUBLISH_TOPIC_PROP_NAME);
      String topic = topicRoot + "/" + tag.getAddressString();
      Integer qos = (Integer) properties.get(PUBLISH_QOS_PROP_NAME);
      Boolean retain = (Boolean) properties.get(PUBLISH_RETAIN_PROP_NAME);

      // Allocate a new payload
      KuraPayload payload = new KuraPayload();

      // Timestamp the message
      long timestamp = System.currentTimeMillis();
      payload.setTimestamp(new Date(timestamp));

      payload.addMetric(TAG_TEMP, tag.getTempC());
      payload.addMetric(TAG_KEYS, (int) tag.getKeys());
      payload.addMetric(TAG_LUX, tag.getLux());
      int gameTimeLeft = gsm.getGameTimeLeft();
      // General game information
      int shotsLeft = gsm.getShotsLeft();
      int shootingTimeLeft = gsm.getShootingTimeLeft();
      if (gameTimeLeft <= 0)
         shootingTimeLeft = 0;
      int score = gsm.getScore();
      if(event != GameStateMachine.GameEvent.NOOP) {
         info("%s; from: %s to: %s on: %s", tag.getAddressString(), state, newState, event);
         payload.addMetric(TAG_PREV_STATE, state.name());
         payload.addMetric(TAG_NEW_STATE, newState.name());
         payload.addMetric(TAG_EVENT, event.name());
         int hits = gsm.getHits();
         String tagAddress = tag.getAddressString();

         // Add hit information if this was a hit
         if(event == GameStateMachine.GameEvent.HIT_DETECTED) {
            payload.addMetric(TAG_HIT_SCORE, gsm.getHitScore());
            payload.addMetric(TAG_HIT_RINGS_OFF_CENTER, gsm.getHitRingsOffCenter());
         }

         // Add game score information if this is the end of the game
         if(event == GameStateMachine.GameEvent.GAME_TIMEOUT) {
            boolean isNewHighScore = false;
            if(highScore == null || highScore.isStillHighScore(score) == false) {
               // Update the high score
               highScore = new HighScore(tagAddress, score, timestamp, hits);
               isNewHighScore = true;
               info("New high score: %s", highScore);
            }
            // Publish scores separately to a distinct topic with higher qos
            publishGameScore(tag.getName(), tagAddress, score, hits, isNewHighScore);
         } else {
            // Also publish to the gateway active games topic
            publishGameInfo(tag.getName(), tagAddress, score, hits, gameTimeLeft, shotsLeft, shootingTimeLeft);
         }
      }
      payload.addMetric(TAG_GAME_TIME_LEFT, gameTimeLeft);
      payload.addMetric(TAG_GAME_SCORE, score);
      payload.addMetric(TAG_SHOOTING_TIME_LEFT, shootingTimeLeft);
      payload.addMetric(TAG_SHOTS_LEFT, shotsLeft);

      // Publish the message
      try {
         cloudClient.publish(topic, payload, qos, retain);
         if(log.isDebugEnabled())
            debug("Published to: %s message: %s", topic, payload);
      } catch (Exception e) {
         info("Cannot publish topic: %s\n", topic, e);
      }
   }

   /**
    * Publish the game state for the given tag address
    * @param tagAddress - BLE address string of the RHIoTTag
    * @return the state of the game
    */
   private GameStateMachine.GameState publishGameState(String tagAddress) {
      GameStateMachine gsm = tagStateMachines.get(tagAddress);
      if(gsm == null) {
         gsm = newStateMachine();
         gsm.start();
         tagStateMachines.put(tagAddress, gsm);
      }
      GameStateMachine.GameState state = gsm.getCurrentState();

      String topicRoot = (String) properties.get(PUBLISH_TOPIC_PROP_NAME);
      String topic = topicRoot + "/" + tagAddress;
      Integer qos = 1;
      Boolean retain = (Boolean) properties.get(PUBLISH_RETAIN_PROP_NAME);

      // Allocate a new payload
      KuraPayload payload = new KuraPayload();

      // Timestamp the message
      payload.setTimestamp(new Date());
      payload.addMetric(TAG_PREV_STATE, state.name());
      payload.addMetric(TAG_NEW_STATE, state.name());
      payload.addMetric(TAG_EVENT, GameStateMachine.GameEvent.NOOP.name());
      int gameTimeLeft = gsm.getGameTimeLeft();
      int shotsLeft = gsm.getShotsLeft();
      int shootingTimeLeft = gsm.getShootingTimeLeft();
      if(gameTimeLeft <= 0)
         shootingTimeLeft = 0;
      int gameScore = gsm.getScore();
      payload.addMetric(TAG_GAME_TIME_LEFT, gameTimeLeft);
      payload.addMetric(TAG_GAME_SCORE, gameScore);
      payload.addMetric(TAG_SHOOTING_TIME_LEFT, shootingTimeLeft);
      payload.addMetric(TAG_SHOTS_LEFT, shotsLeft);

      // Publish the message
      try {
         cloudClient.publish(topic, payload, qos, retain);
         if(log.isDebugEnabled())
            debug("Published to: %s message: %s", topic, payload);
         info("Resent game state for tag: %s", tagAddress);
      } catch (Exception e) {
         info("Failed to publish game state for tag: %s on topic: %s\n", tagAddress, topic, e);
      }

      return state;
   }

   /**
    * Publish in progress game information to gateway gameInfo topic
    * @param name
    * @param tagAddress
    * @param score
    * @param hits
    * @param gameTimeLeft
    * @param shotsLeft
    * @param shootingTimeLeft
    */
   private void publishGameInfo(String name, String tagAddress, int score, int hits, int gameTimeLeft, int shotsLeft, int shootingTimeLeft) {
      String topic = "gameInfo";
      Integer qos = 0;

      KuraPayload payload = new KuraPayload();
      payload.setTimestamp(new Date());
      payload.addMetric(TAG_GAME_NAME, name);
      payload.addMetric(TAG_GAME_ADDRESS, tagAddress);
      payload.addMetric(TAG_GAME_TIME_LEFT, gameTimeLeft);
      payload.addMetric(TAG_GAME_SCORE, score);
      payload.addMetric(TAG_GAME_HITS, hits);
      payload.addMetric(TAG_SHOOTING_TIME_LEFT, shootingTimeLeft);
      payload.addMetric(TAG_SHOTS_LEFT, shotsLeft);
      try {
         cloudClient.publish(topic, payload, qos, Boolean.TRUE);
         if(log.isDebugEnabled())
            debug("Published to: %s message: %s", topic, payload);
      } catch (Exception e) {
         info("Failed to publish high score: %s on topic: %s\n", highScore, topic, e);
      }
   }

   /**
    * Publish a new game score with qos=1 and retain=true to the gateway gameScores node
    * @param name - name associated with the tag
    * @param tagAddress - address of game RHIoTTag
    * @param score - game score
    * @param hits - number of target hits in the game
    * @param isHighScore - new high score flag
    */
   private void publishGameScore(String name, String tagAddress, int score, int hits, boolean isHighScore) {
      String topic = "gameScores";
      Integer qos = 1;

      KuraPayload payload = new KuraPayload();
      payload.setTimestamp(new Date());
      payload.addMetric(GW_LAST_GAME_TAG_NAME, name);
      payload.addMetric(GW_LAST_GAME_SCORE, score);
      payload.addMetric(GW_LAST_GAME_SCORE_HITS, hits);
      payload.addMetric(GW_LAST_GAME_SCORE_TAG_ADDRESS, tagAddress);
      payload.addMetric(GW_LAST_GAME_NEW_HIGH_SCORE, isHighScore);
      try {
         cloudClient.publish(topic, payload, qos, Boolean.TRUE);
         if(log.isDebugEnabled())
            debug("Published to: %s message: %s", topic, payload);
      } catch (Exception e) {
         info("Failed to publish high score: %s on topic: %s\n", highScore, topic, e);
      }
   }
}
