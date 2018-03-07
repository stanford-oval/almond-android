// This file is part of Almond
//
// Copyright 2016-2017 The Board of Trustees of the Leland Stanford Junior University
//
// See COPYING for details
//
package edu.stanford.thingengine.engine.jsapi;

import android.app.Notification;
import android.app.NotificationManager;
import android.content.Context;

import edu.stanford.thingengine.engine.R;

/**
 * Created by gcampagn on 11/7/15.
 */
public class NotifyAPI extends JavascriptAPI {
    private final Context context;

    public NotifyAPI(Context context) {
        super("Notify");

        this.context = context;

        registerSync("showMessage", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                showMessage(args[0].toString(), args[1].toString());
                return null;
            }
        });
    }

    private void showMessage(String title, String msg) {
        Notification notification = new Notification.Builder(context)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle(title)
                .setContentText(msg)
                .build();
        NotificationManager manager = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        manager.notify(0, notification);
    }
}
