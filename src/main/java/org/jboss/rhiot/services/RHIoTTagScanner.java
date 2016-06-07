package org.jboss.rhiot.services;

import org.jboss.rhiot.ble.GAP_UUIDs;
import org.jboss.rhiot.ble.bluez.AdEventInfo;
import org.jboss.rhiot.ble.bluez.AdStructure;
import org.jboss.rhiot.ble.bluez.HCIDump;
import org.jboss.rhiot.ble.bluez.IAdvertEventCallback;
import org.jboss.rhiot.ble.bluez.RHIoTTag;
import org.jboss.rhiot.services.api.IGatewayTagConfig;
import org.jboss.rhiot.services.api.IRHIoTTagScanner;
import org.osgi.framework.ServiceReference;
import org.osgi.service.component.ComponentContext;
import org.osgi.service.component.ComponentException;
import org.eclipse.kura.cloud.CloudClient;
import org.eclipse.kura.cloud.CloudClientListener;
import org.eclipse.kura.cloud.CloudService;
import org.eclipse.kura.configuration.ConfigurableComponent;
import org.eclipse.kura.message.KuraPayload;
import org.osgi.service.http.HttpContext;
import org.osgi.service.http.HttpService;
import org.osgi.service.prefs.PreferencesService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteOrder;
import java.util.Date;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.jboss.rhiot.ble.bluez.RHIoTTag.SERVICE_DATA_PREFIX;

/**
 * The main entry point for the scanner facade on top of the HCIDump general scanner that extracts RHIoTTag specific
 * advertising events and
 */
public class RHIoTTagScanner implements ConfigurableComponent, CloudClientListener, IRHIoTTagScanner, IAdvertEventCallback {
	private static final Logger log = LoggerFactory.getLogger(RHIoTTagScanner.class);

    private static final String APP_ID = "org.jboss.rhiot.services.RHIoTTagScanner";
	private static final String   PUBLISH_TOPIC_PROP_NAME  = "publish.semanticTopic";
	private static final String   PUBLISH_QOS_PROP_NAME    = "publish.qos";
	private static final String   PUBLISH_RETAIN_PROP_NAME = "publish.retain";

	private Map<String, Object> properties;
	private Map<String, String> addressToNameMap;
	private BlockingDeque<RHIoTTag> eventQueue;
    private volatile boolean scannerInitialized;
	/** */
	private volatile int gameDurationSecs;
	private CloudService cloudService;
	private CloudClient cloudClient;
	private AtomicReference<IGatewayTagConfig> gatewayTagConfig = new AtomicReference<>();
	private PreferencesService preferencesService;
	private RHIoTServlet servlet;

	public void setCloudService(CloudService cloudService) {
		this.cloudService = cloudService;
		log("setCloudService, cs=%s\n", cloudService);
	}

	public void unsetCloudService(CloudService cloudService) {
		this.cloudService = null;
		log("unsetCloudService\n");
	}

	public void unsetHttpService(HttpService httpService) {
		httpService.unregister("/rhiot");
	}

	public void setHttpService(HttpService httpService) {
		servlet = new RHIoTServlet(this, preferencesService);
		try {
			HttpContext ctx = httpService.createDefaultHttpContext();
			httpService.registerServlet("/rhiot", servlet, null, ctx);
			log("Listening at /rhiot for REST requests");
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	public void setGatewayTagConfig(IGatewayTagConfig config) {
		this.gatewayTagConfig.set(config);
	}
	public void unsetGatewayTagConfig(IGatewayTagConfig config) {
		this.gatewayTagConfig.set(null);
	}

	public void setPreferencesService(PreferencesService preferencesService) {
		this.preferencesService = preferencesService;
		if(servlet != null)
			servlet.setPreferencesService(preferencesService);
	}
	public void unsetPreferencesService(PreferencesService preferencesService) {
		this.preferencesService = null;
	}

	public void updateTagInfo(String address, String name) {
		addressToNameMap.put(address, name);
	}
	public Map<String,String> getTags() {
		return addressToNameMap;
	}

    @Override
	public void onConnectionEstablished() {
		log("onConnectionEstablished\n");
	}

	@Override
	public void onConnectionLost() {
		log("onConnectionLost\n");
		
	}

	@Override
	public void onControlMessageArrived(String deviceId, String appTopic, KuraPayload msg, int qos, boolean retain) {
		log("onControlMessageArrived(devicdId=%s, appTopic=%s, qos=%d, retain=%s, msg=%s\n", deviceId, appTopic, qos, retain, msg);		
	}

	@Override
	public void onMessageArrived(String deviceId, String appTopic, KuraPayload msg, int qos, boolean retain) {
		log("onMessageArrived(devicdId=%s, appTopic=%s, qos=%d, retain=%s, msg=%s\n", deviceId, appTopic, qos, retain, msg);
	}

	@Override
	public void onMessageConfirmed(int messageId, String appTopic) {
		log("onMessageConfirmed(%s,%s)\n", messageId, appTopic);
	}

	@Override
	public void onMessagePublished(int messageId, String appTopic) {
		log("onMessagePublished(%s,%s)\n", messageId, appTopic);
	}

	@Override
	public boolean advertEvent(AdEventInfo info) {
		if(log.isDebugEnabled())
			log.debug(String.format("+++ advertEvent(%s), count=%d, rssi=%d, time=%s\n", info.getBDaddrAsString(), info.getCount(), info.getRssi(), new Date(info.getTime())));
		if(log.isTraceEnabled()) {
			for(AdStructure ads : info.getData()) {
				log.trace(ads.toString());
			}
		}
		RHIoTTag tag = RHIoTTag.create(info);
		if(tag != null) {
			String key = tag.getAddressString();
			String name = addressToNameMap != null ? addressToNameMap.get(key) : null;
			log("(%s: %s\n", name, tag.toFullString());
		}
		return false;
	}

	protected void activate(ComponentContext componentContext, Map<String, Object> properties) {
        log("RHIoTTagScanner.activate; Bundle " + APP_ID + " has started with: %s\n", properties.entrySet());
		ServiceReference prefRef = componentContext.getBundleContext().getServiceReference(PreferencesService.class);
		if(prefRef != null)
			log("Found PreferencesService ref in bundle: %d", prefRef.getBundle().getBundleId());
		else
			log("Failed to find PreferencesService");
		this.properties = properties;
		this.eventQueue = new LinkedBlockingDeque<>();
		Object tagNames = properties.get("rhiot.tag.name");
		Object tagAddresses = properties.get("rhiot.tag.address");

		System.out.printf("hciDev=%s\n", properties.get("hciDev"));
		System.out.printf("tagNames=%s; tagAddresses=%s\n", tagNames, tagAddresses);

		if(addressToNameMap == null) {
			addressToNameMap = new ConcurrentHashMap<>();
		}
		// get the mqtt client for this application
		try  {
			
			// Acquire a Cloud Application Client for this Application
			if(cloudService != null) {
				log("Getting CloudClient for %s...", APP_ID);
				cloudClient = cloudService.newCloudClient(APP_ID);
				cloudClient.addCloudClientListener(this);
			} else {
				log("No CloudService found\n");
			}
			
			// Don't subscribe because these are handled by the default 
			// subscriptions and we don't want to get messages twice			
			//doUpdate(false);
		}
		catch (Exception e) {
			log("Error during component activation, %s", e);
			throw new ComponentException(e);
		}

        updated(properties);
    }

    protected void deactivate(ComponentContext componentContext) {
        HCIDump.setAdvertEventCallback(null);
        HCIDump.freeScanner();
        scannerInitialized = false;
		eventQueue.clear();
		addressToNameMap = null;
		eventQueue = null;
        System.out.printf("RHIoTTagScanner.deactivate; Bundle " + APP_ID + " has stopped!\n");
    }

    protected void updated(Map<String, Object> properties) {
        System.out.printf("RHIoTTagScanner.updated; Bundle " + APP_ID + " has updated!\n");
        String hciDev = (String) properties.get("hciDev");
        if(hciDev == null)
        	hciDev = "hci1";
        boolean debugMode = false;
		if(properties.get("hcidumpDebugMode") != null)
			debugMode = (Boolean) properties.get("hcidumpDebugMode");
        System.out.printf("hciDev=%s\n", hciDev);
        HCIDump.loadLibrary();
        this.properties = properties;
        if(properties != null && !properties.isEmpty()) {
            Iterator<Map.Entry<String, Object>> it = properties.entrySet().iterator();
            while(it.hasNext()) {
                Map.Entry<String, Object> entry = it.next();
				String key = entry.getKey();
				Object value = entry.getValue();
				String type = value != null ? value.getClass().getName() : "none";
                System.out.printf("New property - %s = %s of type: %s\n", key, value, type);
            }
        }
        // Setup scanner
        HCIDump.enableDebugMode(debugMode);
        HCIDump.setAdvertEventCallback(this);
        if(scannerInitialized == false) {
            HCIDump.initScanner(hciDev, 512, ByteOrder.BIG_ENDIAN);
			System.out.printf("Initialized scanner\n");
        } else {
			HCIDump.freeScanner();
			HCIDump.initScanner(hciDev, 512, ByteOrder.BIG_ENDIAN);
            System.out.printf("Reinitialized scanner\n");
        }
        scannerInitialized = true;
    }
    
    private void log(String format, Object ...args) {
		String msg = String.format(format, args);
    	log.info(msg);
    }

	private void handleTagEvents() {
		while (scannerInitialized) {
			try {
				RHIoTTag tag = eventQueue.poll(10, TimeUnit.MILLISECONDS);
				// TODO: See if there is a hit
				// TODO: See if the game restart
				// TODO: See if the shooter is out of bullets
				doPublish(tag);

			} catch (InterruptedException e) {
			}
		}
	}

	private void doPublish(RHIoTTag tag)
	{
		// fetch the publishing configuration from the publishing properties
		String  topicRoot  = (String) properties.get(PUBLISH_TOPIC_PROP_NAME);
		String topic = topicRoot + "/" + tag.getAddressString();
		Integer qos    = (Integer) properties.get(PUBLISH_QOS_PROP_NAME);
		Boolean retain = (Boolean) properties.get(PUBLISH_RETAIN_PROP_NAME);

		// Allocate a new payload
		KuraPayload payload = new KuraPayload();

		// Timestamp the message
		payload.setTimestamp(new Date());

		payload.addMetric("rhiotTag.temperature", tag.getTempC());
		payload.addMetric("rhiotTag.keys", (int) tag.getKeys());
		payload.addMetric("rhiotTag.lux", (int) tag.getLux());

		// Publish the message
		try {
			cloudClient.publish(topic, payload, qos, retain);
			log("Published to: %s message: %s", topic, payload);
		}
		catch (Exception e) {
			log("Cannot publish topic: %s\n", topic, e);
		}
	}

}
