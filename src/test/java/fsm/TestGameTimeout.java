package fsm;

import org.jboss.rhiot.services.fsm.GameModel;
import org.jboss.rhiot.services.fsm.GameStateMachine;
import org.squirrelframework.foundation.fsm.StateMachineBuilder;
import org.squirrelframework.foundation.fsm.StateMachineBuilderFactory;
import org.squirrelframework.foundation.fsm.StateMachineConfiguration;

import static org.jboss.rhiot.services.fsm.GameStateMachine.GameEvent.CLIP_EMPTY;
import static org.jboss.rhiot.services.fsm.GameStateMachine.GameEvent.GAME_TIMEOUT;
import static org.jboss.rhiot.services.fsm.GameStateMachine.GameEvent.LEFT_RIGHT_PRESSED;
import static org.jboss.rhiot.services.fsm.GameStateMachine.GameEvent.WINDOW_TIMEOUT;

/**
 * Created by sstark on 6/9/16.
 */
public class TestGameTimeout {
   public static void main(String[] args) throws InterruptedException {
      GameModel gameModel = new GameModel();

      StateMachineBuilder<GameStateMachine, GameStateMachine.GameState, GameStateMachine.GameEvent, GameModel> builder =
         StateMachineBuilderFactory.create(GameStateMachine.class, GameStateMachine.GameState.class, GameStateMachine.GameEvent.class, GameModel.class);
      builder.setStateMachineConfiguration(StateMachineConfiguration.create().enableRemoteMonitor(true).enableDebugMode(false));

      GameStateMachine controller = builder.newStateMachine(GameStateMachine.GameState.IDLE);
      controller.export();
      controller.start(gameModel);
      controller.fire(LEFT_RIGHT_PRESSED, gameModel);
      controller.fire(CLIP_EMPTY, gameModel);
      controller.fire(GAME_TIMEOUT, gameModel);
      GameStateMachine.GameState state = controller.getCurrentState();
      assert state == GameStateMachine.GameState.GAMEOVER : "State was not GAMEOVER";

      controller.fire(LEFT_RIGHT_PRESSED, gameModel);
      assert controller.getCurrentState() == GameStateMachine.GameState.SHOOTING : "Should be SHOOTING";
      controller.fire(WINDOW_TIMEOUT, gameModel);
      controller.fire(GAME_TIMEOUT, gameModel);
      assert state == controller.getCurrentState() : "State was not GAMEOVER";
   }

}
