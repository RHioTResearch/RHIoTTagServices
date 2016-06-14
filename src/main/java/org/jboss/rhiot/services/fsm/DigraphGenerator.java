package org.jboss.rhiot.services.fsm;

import org.squirrelframework.foundation.fsm.HistoryType;
import org.squirrelframework.foundation.fsm.ImmutableState;
import org.squirrelframework.foundation.fsm.ImmutableTransition;
import org.squirrelframework.foundation.fsm.StateMachine;
import org.squirrelframework.foundation.fsm.Visitor;

import java.util.LinkedList;
import java.util.Queue;

/**
 * Created by sstark on 6/12/16.
 */
public class DigraphGenerator implements Visitor {
   private final StringBuilder buffer = new StringBuilder();
   private final StringBuilder transBuf = new StringBuilder();

   @Override
   public void visitOnEntry(StateMachine<?, ?, ?, ?> visitable) {
      writeLine("digraph {\ncompound=true;");
      writeLine("subgraph cluster_StateMachine {\nlabel=\""+visitable.getClass().getName()+"\";");
   }

   @Override
   public void visitOnExit(StateMachine<?, ?, ?, ?> visitable) {
      buffer.append(transBuf);
      writeLine("}}");
   }

   @Override
   public void visitOnEntry(ImmutableState<?, ?, ?, ?> visitable) {
      String stateId = visitable.getStateId().toString();
      if(visitable.hasChildStates()) {
         writeLine("subgraph cluster_"+stateId+" {\nlabel=\""+stateId+"\";");
         if(visitable.getHistoryType()== HistoryType.DEEP) {
            writeLine(stateId+"History"+" [label=\"\"];");
         } else if (visitable.getHistoryType()==HistoryType.SHALLOW) {
            writeLine(stateId+"History"+" [label=\"\"];");
         }
      } else {
         writeLine(stateId+" [label=\""+stateId+"\"];");
      }
   }

   @Override
   public void visitOnExit(ImmutableState<?, ?, ?, ?> visitable) {
      if(visitable.hasChildStates()) {
         writeLine("}");
      }
   }

   @Override
   public void visitOnEntry(ImmutableTransition<?, ?, ?, ?> visitable) {
      ImmutableState<?, ?, ?, ?> sourceState = visitable.getSourceState();
      ImmutableState<?, ?, ?, ?> targetState = visitable.getTargetState();
      String sourceStateId = sourceState.getStateId().toString();
      String targetStateId = targetState.getStateId().toString();
      boolean sourceIsCluster=sourceState.hasChildStates();
      boolean targetIsCluster=targetState.hasChildStates();
      String source=(sourceIsCluster)?"cluster_"+sourceStateId:null;
      String target=(targetIsCluster)?"cluster_"+targetStateId:null;
      String realStart=(sourceIsCluster)? getSimpleChildOf(sourceState).getStateId().toString():sourceStateId;
      String realEnd=(targetIsCluster)? getSimpleChildOf(targetState).getStateId().toString():targetStateId;
      String edgeLabel = visitable.getEvent().toString();
      String ltail=(source!=null)?"ltail=\""+source+"\"":null;
      String lhead=(target!=null)?"lhead=\""+target+"\"":null;
      transBuf.append("\n"+realStart+" -> "+realEnd+" ["+((ltail!=null)?ltail+",":"")+((lhead!=null)?lhead+",":"")+" label=\""+edgeLabel+"\"];");
   }

   public ImmutableState<?, ?, ?, ?> getSimpleChildOf(ImmutableState<?, ?, ?, ?> sourceState) {
      Queue<ImmutableState<?, ?, ?, ?>> list=new LinkedList<ImmutableState<?, ?, ?, ?>>();
      list.add(sourceState);
      while(!list.isEmpty()) {
         ImmutableState<?, ?, ?, ?> x=list.poll();
         int l=x.getChildStates().size();
         for (int i=0; i<l; i++) {
            ImmutableState<?, ?, ?, ?> c = x.getChildStates().get(i);
            if (c.hasChildStates()) list.add(c);
            else return c;
         }
      }
      return sourceState;
   }

   @Override
   public void visitOnExit(ImmutableTransition<?, ?, ?, ?> visitable) {
   }

   public String getDigraphString() {
      return buffer.toString();
   }

   protected void writeLine(final String msg) {
      buffer.append(msg).append("\n");
   }

   protected String quoteName(final String id) {
      return "\"" + id + "\"";
   }

}
