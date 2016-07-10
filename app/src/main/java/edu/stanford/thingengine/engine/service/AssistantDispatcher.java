package edu.stanford.thingengine.engine.service;

import android.content.Context;
import android.os.AsyncTask;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

import edu.stanford.thingengine.engine.BuildConfig;

/**
 * Created by gcampagn on 7/10/16.
 */
public class AssistantDispatcher implements Handler.Callback {
    private static final int MSG_ASSISTANT_MESSAGE = 1;

    private final Deque<AssistantMessage> history = new LinkedList<>();
    private final Context ctx;
    private final Handler assistantHandler;
    private final AssistantCommandHandler cmdHandler;

    private AssistantOutput output;

    public AssistantDispatcher(Context ctx, AssistantCommandHandler cmdHandler) {
        this.ctx = ctx;
        this.cmdHandler = cmdHandler;
        assistantHandler = new Handler(Looper.getMainLooper(), this);
    }

    // to be called from the main thread
    public void setAssistantOutput(AssistantOutput output) {
        this.output = output;
    }

    public AssistantMessage.Text handleCommand(final String command) {
        if (BuildConfig.DEBUG) {
`            if (command.startsWith("\\r ")) {
                String json = command.substring(3);
                handleParsedCommand(json);
                return null;
            }
        }

        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                cmdHandler.handleCommand(command);
            }
        });

        AssistantMessage.Text text = new AssistantMessage.Text(AssistantMessage.Direction.FROM_USER, command);
        history.addLast(text);
        return text;
    }

    public void handleParsedCommand(final String json) {
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                cmdHandler.handleParsedCommand(json);
            }
        });
    }

    public AssistantMessage.Picture handlePicture(final String url) {
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                cmdHandler.handlePicture(url);
            }
        });

        AssistantMessage.Picture pic = new AssistantMessage.Picture(AssistantMessage.Direction.FROM_USER, url);
        history.addLast(pic);
        return pic;
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

        if (!maybeInformUI(msg))
            maybeNotify(msg);

        return true;
    }

    private void maybeNotify(AssistantMessage msg) {
        // TODO FILLME
    }

    private boolean maybeInformUI(AssistantMessage msg) {
        if (output != null) {
            output.display(msg);
            return true;
        } else {
            return false;
        }
    }

    // to be called from any thread
    public void dispatch(AssistantMessage msg) {
        Message osMsg = Message.obtain();
        osMsg.what = MSG_ASSISTANT_MESSAGE;
        osMsg.obj = msg;
        assistantHandler.sendMessage(osMsg);
    }
}
