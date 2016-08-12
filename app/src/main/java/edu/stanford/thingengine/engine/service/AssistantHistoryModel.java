package edu.stanford.thingengine.engine.service;

import java.util.AbstractList;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by gcampagn on 8/11/16.
 */
public class AssistantHistoryModel extends AbstractList<AssistantMessage> {
    private final List<AssistantMessage> store = new ArrayList<>();
    private final List<Listener> listeners = new LinkedList<>();

    public interface Listener {
        void onClear();
        void onAdded(AssistantMessage msg);
    }

    public void addListener(Listener l) {
        listeners.add(l);
    }

    public void removeListener(Listener l) {
        listeners.remove(l);
    }

    public boolean add(AssistantMessage msg) {
        if (!store.add(msg))
            return false;
        for (Listener l : listeners)
            l.onAdded(msg);
        return true;
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
