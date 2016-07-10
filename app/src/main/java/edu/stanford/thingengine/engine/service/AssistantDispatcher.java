package edu.stanford.thingengine.engine.service;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Created by gcampagn on 7/10/16.
 */
public class AssistantDispatcher implements Handler.Callback {
    private static final int MSG_ASSISTANT_MESSAGE = 1;

    private final Deque<AssistantMessage> history = new LinkedList<>();
    private final Context ctx;
    private final Handler assistantHandler;

    private AssistantOutput output;
    private volatile AssistantCommandHandler cmdHandler;

    public AssistantDispatcher(Context ctx) {
        this.ctx = ctx;

        assistantHandler = new Handler(Looper.getMainLooper(), this);
    }

    // to be called from the main thread
    void setAssistantOutput(AssistantOutput output) {
        this.output = output;
    }

    // to be called from the JS thread
    void setCommandHandler(AssistantCommandHandler cmdHandler) {
        this.cmdHandler = cmdHandler;
    }

    private static <E> void reverseList(List<E> list) {
        int n = list.size();
        for (int i = 0; i < n/2; i++) {
            E tmp = list.get(i);
            list.set(i, list.get(n-i-1));
            list.set(n-i-1, tmp);
        }
    }

    public List<AssistantMessage> getHistory(int maxElements) {
        ArrayList<AssistantMessage> list = new ArrayList<>();
        list.ensureCapacity(maxElements);

        Iterator<AssistantMessage> iter = history.descendingIterator();
        while (iter.hasNext() && list.size() < maxElements)
            list.add(iter.next());

        reverseList(list);
        return list;
    }

    @Override
    public boolean handleMessage(Message m) {
        if (m.what != MSG_ASSISTANT_MESSAGE)
            return false;

        AssistantMessage msg = (AssistantMessage) m.obj;
        history.addLast(msg);

        maybeNotify(msg);
        maybeInformUI(msg);

        return true;
    }

    private void maybeNotify(AssistantMessage msg) {
        // TODO FILLME
    }

    private void maybeInformUI(AssistantMessage msg) {
        if (output != null)
            output.display(msg);
    }

    public void dispatch(AssistantMessage msg) {
        Message osMsg = Message.obtain();
        osMsg.what = MSG_ASSISTANT_MESSAGE;
        osMsg.obj = msg;
        assistantHandler.sendMessage(osMsg);
    }
}
