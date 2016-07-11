package edu.stanford.thingengine.engine.ui;

import android.app.ActionBar;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.IBinder;
import android.support.v4.app.NavUtils;
import android.util.Log;
import android.view.MenuItem;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import edu.stanford.thingengine.engine.R;
import edu.stanford.thingengine.engine.service.ControlBinder;

public class OAuthActivity extends Activity {
    public static final String ACTION = "edu.stanford.thingengine.engine.OAUTH2";

    private final EngineServiceConnection mEngine;
    private Map<String, String> mSession = new HashMap<>();
    private String kind;
    private String mClass;
    private boolean started;

    public OAuthActivity() {
        mEngine = new EngineServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {
                super.onServiceConnected(name, service);
                if (!started)
                    startOAuth2();
            }
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_oauth);

        WebView view = (WebView)findViewById(R.id.oauth_webview);
        view.getSettings().setJavaScriptEnabled(true);
        view.setWebChromeClient(new UIWebChromeClient());
        view.setWebViewClient(new UIWebViewClient());

        ActionBar actionBar = getActionBar();
        if (actionBar != null)
            actionBar.setDisplayHomeAsUpEnabled(true);
    }

    @Override
    public void onStart() {
        super.onStart();

        Intent intent = getIntent();

        if (intent == null || !ACTION.equals(intent.getAction())) {
            finish();
            return;
        }

        started = false;
        kind = intent.getStringExtra("extra.KIND");
        mClass = intent.getStringExtra("extra.CLASS");
        if (mClass == null)
            mClass = "physical";
        String title = intent.getStringExtra("extra.TITLE");
        if (title != null) {
            setTitle(title);
        } else {
            switch (mClass) {
                case "online":
                    setTitle(R.string.create_account_title);
                    break;
                case "data":
                    setTitle(R.string.create_datasource_title);
                    break;
                case "physical":
                    setTitle(R.string.create_device_title);
                    break;
            }
        }
    }

    private static Map<String, String> jsonToMap(JSONObject o) throws JSONException {
        Map<String, String> map = new HashMap<>();
        Iterator<String> iter = o.keys();
        while (iter.hasNext()) {
            String key = iter.next();
            map.put(key, o.getString(key));
        }

        return map;
    }

    private static class StartOAuth2Result {
        public final Exception exception;
        public final JSONArray result;

        public StartOAuth2Result(Exception e, JSONArray a) {
            exception = e;
            result = a;
        }
    }

    private class StartOAuth2Task extends AsyncTask<String, Void, StartOAuth2Result> {
        @Override
        public StartOAuth2Result doInBackground(String... params) {
            ControlBinder control = mEngine.getControl();
            if (control == null)
                return null;

            try {
                return new StartOAuth2Result(null, control.startOAuth2(params[0]));
            } catch(Exception e) {
                return new StartOAuth2Result(e, null);
            }
        }

        @Override
        public void onPostExecute(StartOAuth2Result result) {
            if (result == null)
                return;

            if (result.exception != null) {
                DialogUtils.showFailureDialog(OAuthActivity.this, "Failed to begin OAuth2 flow: " + result.exception.getMessage());
                return;
            }

            if (result.result == null) {
                setResult(RESULT_OK);
                finish();
            } else {
                try {
                    final String redirect = result.result.getString(0);
                    final Map<String, String> session = jsonToMap(result.result.getJSONObject(1));
                    continueStartOAuth2(redirect, session);
                } catch(JSONException e) {
                    Log.e(MainActivity.LOG_TAG, "Unexpected JSON exception unpacking OAuth2 result", e);
                }
            }
        }
    }

    private void startOAuth2() {
        started = true;
        new StartOAuth2Task().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, kind);
    }

    private void continueStartOAuth2(String redirect, Map<String, String> session) {
        mSession.putAll(session);
        WebView view = (WebView)findViewById(R.id.oauth_webview);
        if (view == null)
            throw new RuntimeException("Missing webview");
        view.loadUrl(redirect);
    }

    @Override
    protected void onResume() {
        super.onResume();
        mEngine.start(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        mEngine.stop(this);
    }

    private class UIWebChromeClient extends WebChromeClient {
        @Override
        public boolean onJsConfirm(WebView view, String url, String message, final JsResult result)
        {
            DialogUtils.showConfirmDialog(OAuthActivity.this, message, new AlertDialog.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    result.confirm();
                }
            }, new AlertDialog.OnClickListener() {
                public void onClick(DialogInterface dialog, int which) {
                    result.cancel();
                }
            });

            return true;
        }

        @Override
        public boolean onJsAlert(WebView view, String url, String message, final JsResult result)
        {
            DialogUtils.showAlertDialog(OAuthActivity.this, message, new AlertDialog.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int which)
                    {
                        result.confirm();
                    }
                });
            return true;
        }
    }

    private class UIWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, final String url) {
            if (url.startsWith("http://127.0.0.1:3000/devices/oauth2/callback/")) {
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        handleCallback(Uri.parse(url));
                    }
                });
                return true;
            }

            return false;
        }
    }

    private static JSONObject mapToJson(Map<String, String> map) throws JSONException {
        JSONObject o = new JSONObject();

        for (Map.Entry<String, String> e : map.entrySet())
            o.put(e.getKey(), e.getValue());

        return o;
    }

    private static JSONObject queryToJson(Uri uri) throws JSONException {
        Collection<String> names = uri.getQueryParameterNames();
        JSONObject o = new JSONObject();

        for (String name : names) {
            List<String> values = uri.getQueryParameters(name);

            if (values.size() == 1) {
                o.put(name, values.get(0));
            } else {
                JSONArray a = new JSONArray();
                for (String value : values)
                    a.put(value);
                o.put(name, a);
            }
        }

        return o;
    }

    private void handleCallback(Uri url) {
        try {
            String kind = url.getLastPathSegment();

            JSONObject req = new JSONObject();
            // there is no actual http request going on, so the values are fake
            // oauth modules should not rely on these anyway
            req.put("httpVersion", "1.0");
            req.put("headers", new JSONArray());
            req.put("rawHeaders", new JSONArray());

            req.put("method", "GET");
            req.put("url", url.toString());
            // body is always empty for a GET request!
            req.put("query", queryToJson(url));
            req.put("session", mapToJson(mSession));

            ControlBinder control = mEngine.getControl();
            if (control != null) {
                try {
                    control.handleOAuth2Callback(kind, req);
                    setResult(RESULT_OK);
                    finish();
                } catch (Exception e) {
                    DialogUtils.showFailureDialog(this, "Failed to complete OAuth2 flow: " + e.getMessage());
                }
            }
        } catch(JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private void navigateUp() {
        Intent intent = getParentActivityIntent();
        if (intent != null)
            intent.putExtra("extra.CLASS", mClass);
        NavUtils.navigateUpTo(this, intent);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                navigateUp();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }
}
