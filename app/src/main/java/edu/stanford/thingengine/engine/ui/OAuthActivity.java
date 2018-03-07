// This file is part of Almond
//
// Copyright 2016-2017 The Board of Trustees of the Leland Stanford Junior University
//
// See COPYING for details
//
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

import com.google.android.gms.auth.api.Auth;
import com.google.android.gms.auth.api.signin.GoogleSignInAccount;
import com.google.android.gms.auth.api.signin.GoogleSignInOptions;
import com.google.android.gms.auth.api.signin.GoogleSignInResult;
import com.google.android.gms.common.Scopes;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.Scope;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import edu.stanford.thingengine.engine.R;
import edu.stanford.thingengine.engine.service.ControlBinder;

public class OAuthActivity extends Activity {
    private static final String GOOGLE_CLIENT_ID = "739906609557-o52ck15e1ge7deb8l0e80q92mpua1p55.apps.googleusercontent.com";
    public static final String ACTION = "edu.stanford.thingengine.engine.OAUTH2";

    public static final int REQUEST_GOOGLE = 2003;
    public static final int REQUEST_GMAIL = 2004;
    public static final int REQUEST_YOUTUBE = 2005;
    public static final int REQUEST_GOOGLE_DRIVE = 2006;

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

    private void startGoogle() {
        GoogleSignInOptions.Builder builder = new GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
                .requestEmail()
                .requestServerAuthCode(GOOGLE_CLIENT_ID, true)
                .requestId();

        int requestCode;
        if (kind.equals("com.google.drive")) {
            requestCode = REQUEST_GOOGLE_DRIVE;
            builder.requestScopes(new Scope("https://www.googleapis.com/auth/drive"),
                    new Scope(Scopes.DRIVE_FILE), new Scope(Scopes.DRIVE_APPFOLDER));
        } else if (kind.equals("com.youtube")) {
            requestCode = REQUEST_YOUTUBE;
            builder.requestScopes(new Scope("https://www.googleapis.com/auth/youtube.force-ssl"),
                    new Scope("https://www.googleapis.com/auth/youtube"),
                    new Scope("https://www.googleapis.com/auth/youtube.readonly"),
                    new Scope("https://www.googleapis.com/auth/youtube.upload"));
        } else if (kind.equals("com.gmail")) {
            requestCode = REQUEST_GMAIL;
            builder.requestScopes(new Scope("https://mail.google.com/"));
        } else {
            requestCode = REQUEST_GOOGLE;
        }

        GoogleApiClient client = new GoogleApiClient.Builder(this)
                .addApi(Auth.GOOGLE_SIGN_IN_API, builder.build())
                .build();
        startActivityForResult(Auth.GoogleSignInApi.getSignInIntent(client), requestCode);
    }

    private void startOAuth2() {
        if (started)
            return;
        started = true;
        if (isGoogle()) {
            startGoogle();
            return;
        }

        new StartOAuth2Task().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, kind);
    }

    private static boolean isGoogleRequestCode(int requestCode) {
        switch (requestCode) {
            case REQUEST_GMAIL:
            case REQUEST_GOOGLE:
            case REQUEST_YOUTUBE:
            case REQUEST_GOOGLE_DRIVE:
                return true;
            default:
                return false;
        }
    }

    private void completeGoogleSignIn(Intent intent, int requestCode) {
        GoogleSignInResult result = Auth.GoogleSignInApi.getSignInResultFromIntent(intent);
        if (!result.isSuccess()) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }

        String kind;
        switch (requestCode) {
            case REQUEST_GMAIL:
                kind = "com.gmail";
                break;
            case REQUEST_GOOGLE:
                kind = "com.google";
                break;
            case REQUEST_YOUTUBE:
                kind = "com.youtube";
                break;
            case REQUEST_GOOGLE_DRIVE:
                kind = "com.google.drive";
                break;
            default:
                throw new AssertionError();
        }
        GoogleSignInAccount account = result.getSignInAccount();
        if (account == null) {
            setResult(RESULT_CANCELED);
            finish();
            return;
        }
        String code = account.getServerAuthCode();

        String url = "https://thingengine.stanford.edu/devices/oauth2/callback/" + kind
                + "?code=" + Uri.encode(code);

        handleCallback(Uri.parse(url));
    }

    private boolean isGoogle() {
        return kind.equals("com.google") || kind.equals("com.youtube") ||
                kind.equals("com.google.drive") || kind.equals("com.gmail");
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
            if (url.startsWith("http://127.0.0.1:3000/devices/oauth2/callback/") ||
                    url.startsWith("https://thingengine.stanford.edu/devices/oauth2/callback/")) {
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

    private void handleCallback(Uri url) {
        new DeviceHandleOAuth2Callback(mEngine, mSession) {
            protected void onPostExecute(Exception e) {
                if (e == null) {
                    setResult(RESULT_OK);
                    finish();
                } else {
                    DialogUtils.showFailureDialog(OAuthActivity.this, "Failed to complete OAuth2 flow: " + e.getMessage());
                }
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR, url);
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

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (isGoogleRequestCode(requestCode)) {
            completeGoogleSignIn(intent, requestCode);
        }
    }
}
