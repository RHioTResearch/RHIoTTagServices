package org.jboss.rhiot.services;

import java.util.Date;

/**
 * Information about the high score game
 */
public class HighScore {
   private String tagAddress;
   private int score;
   private long timestamp;
   private int hits;

   public HighScore(String tagAddress, int score, long timestamp, int hits) {
      this.tagAddress = tagAddress;
      this.score = score;
      this.timestamp = timestamp;
      this.hits = hits;
   }

   public boolean isStillHighScore(int newScore) {
      return score > newScore;
   }

   public String getTagAddress() {
      return tagAddress;
   }

   public int getScore() {
      return score;
   }

   public long getTimestamp() {
      return timestamp;
   }

   public int getHits() {
      return hits;
   }

   @Override
   public String toString() {
      return String.format("HighScore{tagAddress=%s, score=%d, hits=%d, timestamp=%s}", tagAddress, score, hits, new Date(timestamp));
   }
}
