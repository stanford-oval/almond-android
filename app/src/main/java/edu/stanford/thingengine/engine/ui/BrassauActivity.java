package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebView;

import net.hockeyapp.android.CrashManager;
import net.hockeyapp.android.UpdateManager;
import net.hockeyapp.android.metrics.MetricsManager;

import org.json.JSONObject;
import org.json.JSONTokener;

import edu.stanford.thingengine.engine.AutoStarter;
import edu.stanford.thingengine.engine.BuildConfig;
import edu.stanford.thingengine.engine.R;
import edu.stanford.thingengine.engine.service.AssistantDispatcher;
import edu.stanford.thingengine.engine.service.BrassauOutput;
import edu.stanford.thingengine.engine.service.ControlBinder;

public class BrassauActivity extends Activity implements BrassauOutput {

    private final Runnable mReadyCallback = new Runnable() {
        @Override
        public void run() {
            ControlBinder control = engine.getControl();
            if (control == null)
                return;

            AssistantDispatcher assistant = control.getAssistant();
            assistant.setBrassauOutput(BrassauActivity.this);
        }
    };

    private final MainServiceConnection engine;

    public BrassauActivity() {
        engine = new MainServiceConnection();
    }

    private class WebChromeClient extends android.webkit.WebChromeClient {
        @Override
        public boolean onJsConfirm(WebView view, String url, String message, final JsResult result)
        {
            new AlertDialog.Builder(BrassauActivity.this)
                    .setTitle("Confirm")
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok,
                            new AlertDialog.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    result.confirm();
                                }
                            })
                    .setNegativeButton(android.R.string.cancel,
                            new AlertDialog.OnClickListener() {
                                public void onClick(DialogInterface dialog, int which) {
                                    result.cancel();
                                }
                            })
                    .setCancelable(false)
                    .create()
                    .show();

            return true;
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, final JsResult result)
        {
            new AlertDialog.Builder(BrassauActivity.this)
                    .setTitle("Alert")
                    .setMessage(message)
                    .setPositiveButton(android.R.string.ok,
                            new AlertDialog.OnClickListener()
                            {
                                public void onClick(DialogInterface dialog, int which)
                                {
                                    result.confirm();
                                }
                            })
                    .setCancelable(false)
                    .create()
                    .show();

            return true;
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_ACTION_BAR);
        setContentView(R.layout.activity_brassau);

        WebView view = (WebView)findViewById(R.id.brassau_web_view);
        view.addJavascriptInterface(this, "AndroidApi");
        view.getSettings().setJavaScriptEnabled(true);
        view.getSettings().setDomStorageEnabled(true);
        view.setWebChromeClient(new WebChromeClient());
        view.loadUrl("file:///android_asset/index.html");

        if (!BuildConfig.DEBUG) {
            MetricsManager.register(this, getApplication());
        }
        AutoStarter.startService(this);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);
        engine.onActivityResult(requestCode, resultCode, intent);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_brassau, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        if (id == R.id.cheatsheet) {
            Intent intent = new Intent(this, CheatsheetActivity.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.my_stuff) {
            Intent intent = new Intent(this, MyStuffActivity.class);
            startActivity(intent);
            return true;
        }

        if (id == R.id.action_nobrassau) {
            Intent intent = new Intent(this, MainActivity.class);
            intent.setFlags(Intent.FLAG_ACTIVITY_TASK_ON_HOME | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
        }

        return super.onOptionsItemSelected(item);
    }

    private String stringEscape(Object o) {
        if (o == null)
            return "null";
        String str = o.toString();
        return "\"" + str.replace("\"", "\\\"").replace("\n", "\\n").replace("\t", "\\t") + "\"";
    }

    private void replyJSCallback(int callback, Exception error, JSONObject result) {
        final String js = "AndroidApi._callback(1," + callback + "," + stringEscape(error) + "," + result + ");";
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((WebView)findViewById(R.id.brassau_web_view)).evaluateJavascript(js, null);
            }
        });
    }

    @Override
    public void onResume() {
        super.onResume();
        engine.start(this);
        engine.addEngineReadyCallback(mReadyCallback);
        mReadyCallback.run();
        CrashManager.register(this);
        UpdateManager.register(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        ControlBinder control = engine.getControl();
        if (control != null) {
            AssistantDispatcher assistant = control.getAssistant();
            assistant.setBrassauOutput(null);
        }
        engine.removeEngineReadyCallback(mReadyCallback);
        engine.stop(this);
        UpdateManager.unregister();
    }

    @Override
    public void notify(JSONObject object) {
        final String js = "AndroidApi._callback(0,0,null," + object + ");";
        // we're already on the UI thread, so we don't need the same as replyJSCallback
        ((WebView)findViewById(R.id.brassau_web_view)).evaluateJavascript(js, null);
    }

    @JavascriptInterface
    void connect() {
        Log.i("brassau", "connect");
        final ControlBinder control = engine.getControl();
        if (control == null)
            throw new RuntimeException("The engine died unexpectedly");
        control.getAssistant().brassauReady();
    }

    @JavascriptInterface
    void createApp(final int callbackId, final String json) {
        final ControlBinder control = engine.getControl();
        if (control == null)
            throw new RuntimeException("The engine died unexpectedly");
        Log.i("brassau", "createApp: " + json);

        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    JSONObject object = (JSONObject)(new JSONTokener(json).nextValue());
                    replyJSCallback(callbackId, null, control.createApp(object));
                } catch (Exception e) {
                    replyJSCallback(callbackId, e, null);
                }
            }
        });;
    }

    @JavascriptInterface
    void deleteApp(String uniqueId) {
        final ControlBinder control = engine.getControl();
        if (control == null)
            throw new RuntimeException("The engine died unexpectedly");

        control.deleteApp(uniqueId);
    }

    @JavascriptInterface
    void parseCommand(final int callbackId, final String command) {
        final ControlBinder control = engine.getControl();
        if (control == null)
            throw new RuntimeException("The engine died unexpectedly");

        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                try {
                    replyJSCallback(callbackId, null, control.parseCommand(command));
                } catch (Exception e) {
                    replyJSCallback(callbackId, e, null);
                }
            }
        });
    }
}
