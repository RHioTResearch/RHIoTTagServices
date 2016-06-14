package fsm;

import org.jboss.rhiot.services.fsm.GameModel;
import org.jboss.rhiot.services.fsm.GameStateMachine;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.StateMachineConfiguration;
import org.squirrelframework.foundation.fsm.StateMachineLogger;

import static org.jboss.rhiot.services.fsm.GameStateMachine.GameEvent.CLIP_EMPTY;
import static org.jboss.rhiot.services.fsm.GameStateMachine.GameEvent.HIT_DETECTED;
import static org.jboss.rhiot.services.fsm.GameStateMachine.GameEvent.RIGHT_PRESSED;
import static org.jboss.rhiot.services.fsm.GameStateMachine.GameEvent.GAME_TIMEOUT;
import static org.jboss.rhiot.services.fsm.GameStateMachine.GameEvent.LEFT_PRESSED;
import static org.jboss.rhiot.services.fsm.GameStateMachine.GameEvent.LEFT_RIGHT_PRESSED;
import static org.jboss.rhiot.services.fsm.GameStateMachine.GameEvent.WINDOW_TIMEOUT;

/**
 * Test the shoot a RHIoTTag game state machine
 */
public class TestTagGame {
   public static void main(String[] args) throws InterruptedException {
      GameModel gameModel = new GameModel();

      StateMachineBuilder<GameStateMachine, GameStateMachine.GameState, GameStateMachine.GameEvent, GameModel> builder =
         StateMachineBuilderFactory.create(GameStateMachine.class, GameStateMachine.GameState.class, GameStateMachine.GameEvent.class, GameModel.class);
      builder.setStateMachineConfiguration(StateMachineConfiguration.create().enableRemoteMonitor(true).enableDebugMode(false));

      GameStateMachine controller = builder.newStateMachine(GameStateMachine.GameState.IDLE);
      //StateMachineLogger fsmLogger = new StateMachineLogger(controller);
      //fsmLogger.startLogging();
      controller.export();
      controller.setGameModel(gameModel);
      controller.start(gameModel);
      controller.fire(LEFT_RIGHT_PRESSED);
      GameStateMachine.GameState lastState = controller.getCurrentState();
      while(lastState != GameStateMachine.GameState.GAMEOVER) {
         System.out.printf("State: %s\n", lastState);
         switch (lastState) {
            case IDLE:
               break;
            case SHOOTING:
               if(gameModel.isGameExpired()) {
                  controller.fire(GAME_TIMEOUT);
               }
               else if(gameModel.tookShot() <= 0) {
                  System.out.printf("+++ Clip is empty!\n");
                  controller.fire(CLIP_EMPTY);
               } else if(gameModel.isShootingWindowExpired()) {
                  controller.fire(WINDOW_TIMEOUT);
               } else {
                  // Wait for a second to allow window to expire before bullets run out
                  if(gameModel.getShotsLeft() % 2 != 0)
                     controller.fire(HIT_DETECTED);
                  Thread.sleep(1000);
               }
               break;
            case GUN_EMPTY:
               controller.fire(RIGHT_PRESSED);
               break;
            case REPLACE_TARGET:
               controller.fire(LEFT_PRESSED);
               break;
            case GAMEOVER:

               break;
         }
         Thread.sleep(1000);
         lastState = controller.getCurrentState();
      }
      System.out.printf("GAMEOVER\n");

      controller.terminate();
   }
}
