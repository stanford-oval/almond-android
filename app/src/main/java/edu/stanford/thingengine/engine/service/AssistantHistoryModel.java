package edu.stanford.thingengine.engine.service;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;

/**
 * Created by gcampagn on 8/11/16.
 */
public class AssistantHistoryModel extends AbstractList<AssistantMessage> {
    private final List<AssistantMessage> store = new ArrayList<>();
    private final List<Listener> listeners = new LinkedList<>();

    public interface Listener {
        void onClear();
        void onAdded(AssistantMessage msg, int idx);
        void onRemoved(AssistantMessage msg, int idx);
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    private void notifyAdd(AssistantMessage msg, int idx) {
        for (Listener l : listeners)
            l.onAdded(msg, idx);
    }

    private boolean isFiltered(AssistantMessage msg) {
        if (msg.type == AssistantMessage.Type.ASK_SPECIAL) {
            AssistantMessage.AskSpecial askSpecial = (AssistantMessage.AskSpecial) msg;
            return !askSpecial.what.isChooser();
        } else {
            return false;
        }
    }

    public boolean add(AssistantMessage msg) {
        if (isFiltered(msg))
            return false;

        if (!store.add(msg))
            return false;
        notifyAdd(msg, store.size()-1);
        return true;
    }

    private void notifyRemove(AssistantMessage msg, int idx) {
        for (Listener l : listeners)
            l.onRemoved(msg, idx);
    }

    public void removeButtons() {
        ListIterator<AssistantMessage> lit = store.listIterator(store.size());

        while (lit.hasPrevious()) {
            int idx = lit.previousIndex();
            AssistantMessage msg = lit.previous();
            if (!msg.type.isInteraction())
                break;

            lit.remove();
            notifyRemove(msg, idx);
        }
    }

    @Override
    public void clear() {
        store.clear();
        for (Listener l : listeners)
            l.onClear();
    }

    @Override
    public AssistantMessage get(int location) {
        return store.get(location);
    }

    @Override
    public int size() {
        return store.size();
    }
}
