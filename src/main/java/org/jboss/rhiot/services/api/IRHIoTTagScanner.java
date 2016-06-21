package org.jboss.rhiot.services.api;

/**
 * Created by sstark on 5/25/16.
 */
public interface IRHIoTTagScanner {
   // Metrics keys
   // Set 1, the tag data sent on every advertisement event
   static final String TAG_TEMP = "rhiotTag.temperature";
   static final String TAG_KEYS = "rhiotTag.keys";
   static final String TAG_LUX = "rhiotTag.lux";
   // Set 2, the game state information sent on an event such as a key press or state timeout
   static final String TAG_PREV_STATE = "rhiotTag.prevState";
   static final String TAG_NEW_STATE = "rhiotTag.newState";
   static final String TAG_EVENT = "rhiotTag.event";
   // Set 3, the game progress information sent while the game is active.
   static final String TAG_GAME_NAME = "rhiotTag.gameTagName";
   static final String TAG_GAME_ADDRESS = "rhiotTag.gameTagAddress";
   static final String TAG_GAME_TIME_LEFT = "rhiotTag.gameTimeLeft";
   static final String TAG_GAME_SCORE = "rhiotTag.gameScore";
   static final String TAG_GAME_HITS = "rhiotTag.gameHits";
   static final String TAG_SHOOTING_TIME_LEFT = "rhiotTag.shootingTimeLeft";
   static final String TAG_SHOTS_LEFT = "rhiotTag.shotsLeft";
   // Set 4, the information about a hit on the light sensor sent when a sensor reading above a threshold value is detected
   static final String TAG_HIT_SCORE = "rhiotTag.hitScore";
   static final String TAG_HIT_RINGS_OFF_CENTER = "rhiotTag.hitRingsOffCenter";
   // Set 5, the information about the game scores sent on each game end
   static final String GW_LAST_GAME_TAG_NAME = "rhiotTagGW.tagName";
   static final String GW_LAST_GAME_SCORE = "rhiotTagGW.score";
   static final String GW_LAST_GAME_SCORE_HITS = "rhiotTagGW.hits";
   static final String GW_LAST_GAME_SCORE_TAG_ADDRESS = "rhiotTagGW.scoreTagAddress";
   static final String GW_LAST_GAME_NEW_HIGH_SCORE = "rhiotTagGW.isNewHighScore";


   // REST endpoints
   /** */
   String CLOUD_PW_PATH = "/cloud-password";
   /** */
   String TAG_INFO_PATH = "/tags";
   /** */
   String GAMESM_DIGRAPH_PATH = "/gamesm-digraph";
   /** */
   String GAMESM_INFO_PATH = "/gamesm";
   /** */
   String INJECT_TAG_DATA_PATH = "/inject-tag-data";
}
