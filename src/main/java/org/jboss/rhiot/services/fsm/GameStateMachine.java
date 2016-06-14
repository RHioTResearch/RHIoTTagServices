package org.jboss.rhiot.services.fsm;

import org.squirrelframework.foundation.component.SquirrelProvider;
import org.squirrelframework.foundation.fsm.AnonymousCondition;
import org.squirrelframework.foundation.fsm.DotVisitor;
import org.squirrelframework.foundation.fsm.annotation.State;
import org.squirrelframework.foundation.fsm.annotation.States;
import org.squirrelframework.foundation.fsm.annotation.Transit;
import org.squirrelframework.foundation.fsm.annotation.Transitions;
import org.squirrelframework.foundation.fsm.impl.AbstractStateMachine;

/**
 * The state machine for the laser shoot a RHIoTTag game
 */
@States({
   @State(name="IDLE", initialState=true),
   @State(name="SHOOTING"),
   @State(name="RESETTING"),
   @State(name="GUN_EMPTY"),
   @State(name="REPLACE_TARGET"),
   @State(name="GAMEOVER")
})
@Transitions({
   @Transit(from = "IDLE", to = "SHOOTING", on = "LEFT_RIGHT_PRESSED", callMethod = "onStart"),
   @Transit(from = "GAMEOVER", to = "SHOOTING", on = "LEFT_RIGHT_PRESSED", callMethod = "onStart"),
   @Transit(from = "SHOOTING", to = "GUN_EMPTY", on = "CLIP_EMPTY", callMethod = "onEmpty"),
   @Transit(from = "SHOOTING", to = "REPLACE_TARGET", on = "WINDOW_TIMEOUT", callMethod = "onEndShootingWindow"),
   @Transit(from = "SHOOTING", to = "RESETTING", on = "HIT_DETECTED", callMethod = "onHitDetected"),
   @Transit(from = "SHOOTING", to = "GAMEOVER", on = "GAME_TIMEOUT", callMethod = "onEnd"),
   @Transit(from = "RESETTING", to = "SHOOTING", on = "LS_RESET", callMethod = "onLSReset"),
   @Transit(from = "RESETTING", to = "GAMEOVER", on = "GAME_TIMEOUT", callMethod = "onEnd"),
   @Transit(from = "GUN_EMPTY", to = "SHOOTING", on = "RIGHT_PRESSED", callMethod = "onRefill"),
   @Transit(from = "GUN_EMPTY", to = "GAMEOVER", on = "GAME_TIMEOUT", callMethod = "onEnd"),
   @Transit(from = "REPLACE_TARGET", to = "SHOOTING", on = "LEFT_PRESSED", callMethod = "enterShooting"),
   @Transit(from = "REPLACE_TARGET", to = "GAMEOVER", on = "GAME_TIMEOUT", callMethod = "onEnd"),
})
public class GameStateMachine extends AbstractStateMachine<GameStateMachine, GameStateMachine.GameState, GameStateMachine.GameEvent, GameModel> {
   public enum GameState {
      IDLE, SHOOTING, RESETTING, GUN_EMPTY, REPLACE_TARGET, GAMEOVER
   }
   public enum GameEvent {
      LEFT_RIGHT_PRESSED, CHECK_SHOT, HIT_DETECTED, LS_RESET, CLIP_EMPTY, RIGHT_PRESSED, LEFT_PRESSED, WINDOW_TIMEOUT, GAME_TIMEOUT, NOOP
   }

   /**
    * Writes a graphviz dot file of the state machine
    */
   public void export() {
      DotVisitor visitor = SquirrelProvider.getInstance().newInstance(DotVisitor.class);
      this.accept(visitor);
      visitor.convertDotFile("TagGameSM");
   }

   /**
    * Generates and returns a graphviz dot digraph structure of the state machines as text
    * @return digraph structure of the state machines as text
    */
   public String exportAsString() {
      DigraphGenerator visitor = new DigraphGenerator();
      this.accept(visitor);
      return visitor.getDigraphString();
   }

   public static class ContinueShootingCondition extends AnonymousCondition<GameModel> {
      @Override
      public boolean isSatisfied(GameModel context) {
         boolean shootingWindowExpired = context.isShootingWindowExpired();
         boolean gameExpired = context.isGameExpired();
         System.out.printf("ContinueShootingCondition, shootingWindowExpired=%s, gameExpired=%s\n", shootingWindowExpired, gameExpired);
         return !shootingWindowExpired && !gameExpired;
      }
   }

   public GameModel getGameModel() {
      return gameModel;
   }

   public void setGameModel(GameModel gameModel) {
      this.gameModel = gameModel;
   }

   // Convenience methods brought up from GameModel
   public boolean isShootingWindowExpired() {
      return gameModel.isShootingWindowExpired();
   }
   public boolean isGameExpired() {
      return gameModel.isGameExpired();
   }
   public int tookShot() {
      return gameModel.tookShot();
   }
   public boolean isClipEmpty() {
      return gameModel.getShotsLeft() <= 0;
   }
   public int getGameTimeLeft() {
      return gameModel.getGameTimeLeft();
   }
   public int getShotsLeft() {
      return gameModel.getShotsLeft();
   }
   public int getShootingTimeLeft() {
      return gameModel.getShootingTimeLeft();
   }
   public int recordHit(int luxReading) {
      return gameModel.recordHit(luxReading);
   }
   public int getHitScore() {
      return gameModel.getHitScore();
   }
   public int getHitRingsOffCenter() {
      return gameModel.getHitRingsOffCenter();
   }
   public int getScore() {
      return gameModel.getScore();
   }
   public int getHits() {
      return gameModel.getHits();
   }


   @Override
   public void fire(GameEvent event) {
      super.fire(event, gameModel);
   }

   protected void enterShooting(GameStateMachine.GameState from, GameStateMachine.GameState to, GameStateMachine.GameEvent event, GameModel model) {
      System.out.printf("GameStateMachine.enterShooting(%s,%s,%s)\n", from, to, event);
      model.startShootingWindow();
   }
   protected void onStart(GameStateMachine.GameState from, GameStateMachine.GameState to, GameStateMachine.GameEvent event, GameModel model) {
      System.out.printf("GameStateMachine.onStart(%s,%s,%s)\n", from, to, event);
      model.startGame();
   }
   protected void onCheckShot(GameStateMachine.GameState from, GameStateMachine.GameState to, GameStateMachine.GameEvent event, GameModel model) {
      System.out.printf("GameStateMachine.onCheckShot(%s,%s,%s)\n", from, to, event);
   }
   protected void onHitDetected(GameStateMachine.GameState from, GameStateMachine.GameState to, GameStateMachine.GameEvent event, GameModel model) {
      System.out.printf("GameStateMachine.onHitDetected(%s,%s,%s)\n", from, to, event);
   }
   protected void onLSReset(GameStateMachine.GameState from, GameStateMachine.GameState to, GameStateMachine.GameEvent event, GameModel model) {
      System.out.printf("GameStateMachine.onLSReset(%s,%s,%s)\n", from, to, event);
   }
   protected void onEndShootingWindow(GameStateMachine.GameState from, GameStateMachine.GameState to, GameStateMachine.GameEvent event, GameModel model) {
      System.out.printf("GameStateMachine.onEndShootingWindow(%s,%s,%s)\n", from, to, event);
   }
   protected void onEmpty(GameStateMachine.GameState from, GameStateMachine.GameState to, GameStateMachine.GameEvent event, GameModel model) {
      System.out.printf("GameStateMachine.onEmpty(%s,%s,%s)\n", from, to, event);
   }
   protected void onRefill(GameStateMachine.GameState from, GameStateMachine.GameState to, GameStateMachine.GameEvent event, GameModel model) {
      System.out.printf("GameStateMachine.onRefill(%s,%s,%s)\n", from, to, event);
      model.reload();
   }
   protected void onEnd(GameStateMachine.GameState from, GameStateMachine.GameState to, GameStateMachine.GameEvent event, GameModel model) {
      System.out.printf("GameStateMachine.onEnd(%s,%s,%s)\n", from, to, event);
   }

   private GameModel gameModel;
}
