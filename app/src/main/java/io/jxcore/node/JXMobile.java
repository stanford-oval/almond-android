// License information is available from LICENSE file

package io.jxcore.node;

import android.annotation.SuppressLint;
import android.content.Context;
import android.util.Log;

import java.util.ArrayList;

import io.jxcore.node.jxcore.JXcoreCallback;

public class JXMobile {
  public static void Initialize(final Context context) {
    jxcore.RegisterMethod("OnError", new JXcoreCallback() {
      @SuppressLint("NewApi")
      @Override
      public void Receiver(ArrayList<Object> params, String callbackId) {
        String message = (String) params.get(0);
        String stack;
        if (params.size() > 1)
          stack = (String) params.get(1);
        else
          stack = "";

        Log.e("jxcore", "Error!: " + message + "\nStack: " + stack);
      }
    });

    jxcore.RegisterMethod("GetDocumentsPath", new JXcoreCallback() {
      @SuppressLint("NewApi")
      @Override
      public void Receiver(ArrayList<Object> params, String callbackId) {
        String path = context.getFilesDir().getAbsolutePath();
        jxcore.CallJSMethod(callbackId, "\"" + path + "\"");
      }
    });

    jxcore.RegisterMethod("GetCachePath", new JXcoreCallback() {
      @Override
      public void Receiver(ArrayList<Object> params, String callbackId) {
        String path = context.getCacheDir().getAbsolutePath();
        jxcore.CallJSMethod(callbackId, "\"" + path + "\"");
      }
    });

    jxcore.RegisterMethod("Exit", new JXcoreCallback() {
      @Override
      public void Receiver(ArrayList<Object> params, String callbackId) {
        jxcore.QuitLoop();
      }
    });
  }
}