package org.jboss.rhiot.services.fsm;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;

/**
 * The game context information used by the state machine
 */
public class GameModel {
   private static final Logger log = LoggerFactory.getLogger(GameModel.class);
   /** The maximum recorded raw lux value with a direct hit by laser on sensor */
   private static final int LUX_AT_BULLSEYE = 49151;
   /** The points awarded for a bullseye */
   private static final int POINTS_PER_HIT = 1000;
   /** The clip capacity */
   private static final int SHOT_CAPACITY = 6;

   /** How many shots are remaining */
   private int shotsLeft = SHOT_CAPACITY;
   /** Start time of the current shooting window */
   private long beginShootingWindow;
   /** End time of the current shooting window */
   private long endShootingWindow;
   /** The shooting window duration in ms */
   private long shootingWindowDuration = TimeUnit.MILLISECONDS.convert(5, TimeUnit.SECONDS);
   /** The start time of the game */
   private long beginGame;
   /** The end time of the game */
   private long endGame;
   /** The game duration in ms */
   private long gameDuration = TimeUnit.MILLISECONDS.convert(15, TimeUnit.SECONDS);
   /** The raw lux value for a direct hit by laser pointer */
   private int luxAtBullseye = LUX_AT_BULLSEYE;
   /** The raw lux value with the sensor fully exposed to the ambient room light */
   private int luxAtBackground = 1500;
   /** The score of the last hit */
   private int hitScore;
   /** How many rings was the hit from the center bullseye */
   private int hitRingsOffCenter;
   /** The number of hits recorded during the game */
   private int hits;
   /** The cumulative game score */
   private int score;

   /**
    * Start a game. This sets the game starting and ending time.
    */
   public void startGame() {
      beginGame = System.currentTimeMillis();
      endGame = beginGame + gameDuration;
      score = 0;
      hits = 0;
      startShootingWindow();
   }

   /**
    * Record that a shot was taken and decrement the shots remaining
    * @return the number of shots remaining or 0 if empty
    */
   public int tookShot() {
      if(shotsLeft > 0)
         shotsLeft --;
      return shotsLeft;
   }
   public int getShotsLeft() {
      return shotsLeft;
   }
   public void reload() {
      shotsLeft = SHOT_CAPACITY;
      log.debug("+++ Reloaded");
   }

   /**
    * Get the number hits recorded in the game
    * @return cumulative number of hits in game
    */
   public int getHits() {
      return hits;
   }

   /**
    * Start a new shooting window and reload the clip
    */
   public void startShootingWindow() {
      beginShootingWindow = System.currentTimeMillis();
      endShootingWindow = beginShootingWindow + shootingWindowDuration;
      reload();
   }

   /**
    * @return system time the current shooting window began
    */
   public long getBeginShootingWindow() {
      return beginShootingWindow;
   }

   /**
    * @return system time the current shooting window ends
    */
   public long getEndShootingWindow() {
      return endShootingWindow;
   }

   public int getShootingTimeLeft() {
      int timeLeft = (int) (endShootingWindow - System.currentTimeMillis());
      if(timeLeft < 0)
         timeLeft = 0;
      return timeLeft;
   }
   /**
    * @return the duration in ms of the shooting window
    */
   public long getShootingWindowDuration() {
      return shootingWindowDuration;
   }

   /**
    *
    * @param shootingWindowDurationInSecs
    */
   public void setShootingWindowDuration(long shootingWindowDurationInSecs) {
      this.shootingWindowDuration = TimeUnit.MILLISECONDS.convert(shootingWindowDurationInSecs, TimeUnit.SECONDS);
   }

   /**
    * @return system time the current game began
    */
   public long getBeginGame() {
      return beginGame;
   }
   /**
    * @return system time the current game ends
    */
   public long getEndGame() {
      return endGame;
   }

   public int getGameTimeLeft() {
      int timeLeft = (int) (endGame - System.currentTimeMillis());
      if(timeLeft < 0)
         timeLeft = 0;
      return timeLeft;
   }

   public long getGameDuration() {
      return gameDuration;
   }

   /**
    *
    * @param gameDurationInSecs
    */
   public void setGameDuration(long gameDurationInSecs) {
      this.gameDuration = TimeUnit.MILLISECONDS.convert(gameDurationInSecs, TimeUnit.SECONDS);
   }

   /**
    * Is the current time past the end of the current shooting window.
    * @see #getEndShootingWindow()
    * @return true if current system time is past end of current shooting window, false otherwise.
    */
   public boolean isShootingWindowExpired() {
      long now = System.currentTimeMillis();
      return now > endShootingWindow;
   }

   /**
    * Is the current time past the end of the current game.
    * @see #getEndGame()
    * @return true if current system time is past end of current game, false otherwise.
    */
   public boolean isGameExpired() {
      long now = System.currentTimeMillis();
      return now > endGame;
   }

   /**
    * Assign points to the hit based on the lux reading. A perfect hit has a lux value of LUX_AT_BULLSEYE. A set
    * of 8 zones is
    * @param luxReading - the raw sensor lux reading
    * @return the score of the hit
    */
   public int recordHit(int luxReading) {
      int ringWidth = (luxAtBullseye - luxAtBackground) / 8;
      hitRingsOffCenter = (luxAtBullseye - luxReading) / ringWidth;
      if(hitRingsOffCenter < 0)
         hitRingsOffCenter = 0;
      hitScore = (int) (POINTS_PER_HIT * (1.0 - hitRingsOffCenter*0.125));
      score += hitScore;
      hits ++;
      return hitScore;
   }

   /**
    * Get the score of the last hit as determined by {@link #recordHit(int)}
    * @return last hit score
    */
   public int getHitScore() {
      return hitScore;
   }

   /**
    * Get the count of rings off-center of the last hit as determined by {@link #recordHit(int)}
    * @return how many rings off-center the last hit was
    */
   public int getHitRingsOffCenter() {
      return hitRingsOffCenter;
   }

   /**
    * Get the current game score
    * @return score of game
    */
   public int getScore() {
      return score;
   }
}
