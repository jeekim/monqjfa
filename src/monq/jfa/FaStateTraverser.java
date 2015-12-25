package monq.jfa;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Set;

import monq.jfa.FaState.IterType;

/**
 * traverses all states reachable from a given {@link FaState} and applies a
 * visitor.
 * 
 * @param <D> is the type of fixed object passed to every visit call of the
 *        visitor.
 */
class FaStateTraverser<D> {

  private final D data;
  private final Set<FaState> visited =
      Collections.newSetFromMap(new IdentityHashMap<FaState,Boolean>());
  private final List<FaState> stack = new ArrayList<>(100);
  private final IterType iType;

  public FaStateTraverser(IterType iType, D data) {
    this.iType = iType;
    this.data = data;
  }

  public void traverse(FaState state, StateVisitor<D> stateVis) {
    stack.add(state);
    while (!stack.isEmpty()) {
      state = stack.remove(stack.size()-1);
      if (visited.contains(state)) {
        continue;
      }
      visited.add(state);
      stackNewChildren(state);
      stateVis.visit(state, data);      
    }
  }

  private void stackNewChildren(FaState state) {
    for(Iterator<FaState> it=state.getChildIterator(iType); it.hasNext();) {
      FaState child = it.next();
      if (!visited.contains(child)) {
        stack.add(child);
      }
    }
  }

  public static interface StateVisitor<Data> {
    void visit(FaState state, Data d);
  }
}
