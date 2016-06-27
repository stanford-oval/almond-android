package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.webkit.JavascriptInterface;
import android.webkit.JsResult;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.webkit.WebViewFragment;

import edu.stanford.thingengine.engine.CloudAuthInfo;
import edu.stanford.thingengine.engine.service.ControlBinder;

/**
 * Created by gcampagn on 6/27/16.
 */
public class LegacyWebUIFragment extends WebViewFragment {
    private final static int RESULT_OAUTH2 = 1;
    private final static int RESULT_CREATE_DEVICE = 2;

    private CloudAuthInfo authInfo;
    private FragmentEmbedder mListener;
    private EngineServiceConnection mEngine;

    public LegacyWebUIFragment() {}

    public static LegacyWebUIFragment newInstance() {
        LegacyWebUIFragment fragment = new LegacyWebUIFragment();
        Bundle args = new Bundle();
        fragment.setArguments(args);
        return fragment;
    }

    private class UIWebChromeClient extends WebChromeClient {
        @Override
        public boolean onJsConfirm(WebView view, String url, String message, final JsResult result)
        {
            DialogUtils.showConfirmDialog(getActivity(), message, new AlertDialog.OnClickListener() {
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
            DialogUtils.showAlertDialog(getActivity(), message, new AlertDialog.OnClickListener() {
                public void onClick(DialogInterface dialog, int which)
                {
                    result.confirm();
                }
            });
            return true;
        }
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    private void runOAuth2(Uri url) {
        String kind = url.getLastPathSegment();

        Intent intent = new Intent(getActivity(), OAuthActivity.class);
        intent.setAction(OAuthActivity.ACTION);
        intent.putExtra("extra.KIND", kind);

        startActivityForResult(intent, RESULT_OAUTH2);
    }

    private void runDeviceConfigure(Uri url) {
        String _class = url.getQueryParameter("class");
        if (_class == null)
            _class = "physical";

        Intent intent = new Intent(getActivity(), DeviceConfigureChooseKindActivity.class);
        intent.setAction(DeviceConfigureChooseKindActivity.ACTION);
        intent.putExtra("extra.CLASS", _class);

        startActivityForResult(intent, RESULT_CREATE_DEVICE);
    }

    private void deleteDevice(final String uniqueId) {
        AsyncTask.THREAD_POOL_EXECUTOR.execute(new Runnable() {
            @Override
            public void run() {
                ControlBinder control = mEngine.getControl();
                if (control == null)
                    return;

                try {
                    control.deleteDevice(uniqueId);
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            refresh();
                        }
                    });
                } catch(final Exception e) {
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            DialogUtils.showAlertDialog(getActivity(), "Failed to delete device: " + e.getMessage(), new DialogInterface.OnClickListener() {
                                @Override
                                public void onClick(DialogInterface dialog, int which) {
                                    // do nothing
                                }
                            });
                        }
                    });
                }
            }
        });
    }

    private void runDeviceDelete(Uri url) {
        final String uniqueId = url.getLastPathSegment();

        DialogUtils.showConfirmDialog(getActivity(), "Are you sure?", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int which) {
                deleteDevice(uniqueId);
            }
        }, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                // do nothing
            }
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == RESULT_OAUTH2 || requestCode == RESULT_CREATE_DEVICE) {
            refresh();
        }
    }

    private class UIWebViewClient extends WebViewClient {
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, final String url) {
            if (url.startsWith("http://127.0.0.1:3000/devices/oauth2/")) {
                runOAuth2(Uri.parse(url));
                return true;
            }
            if (url.startsWith("http://127.0.0.1:3000/devices/create")) {
                runDeviceConfigure(Uri.parse(url));
                return true;
            }
            if (url.startsWith("http://127.0.0.1:3000/devices/delete/")) {
                runDeviceDelete(Uri.parse(url));
                return true;
            }
            return false;
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        WebView view = getWebView();
        view.addJavascriptInterface(this, "Android");
        view.getSettings().setJavaScriptEnabled(true);
        view.setWebChromeClient(new UIWebChromeClient());
        view.setWebViewClient(new UIWebViewClient());
    }

    private void refresh() {
        getWebView().loadUrl("http://127.0.0.1:3000/apps");
    }

    @Override
    public void onResume() {
        super.onResume();
        refresh();
    }

    private void showConfirmDialog(boolean success) {
        new AlertDialog.Builder(getActivity())
                .setMessage(success ? "Congratulations, you're now all set to use ThingEngine!"
                        : "Sorry, that did not work")
                .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        dialog.dismiss();
                    }
                })
                .setIcon(android.R.drawable.ic_dialog_info)
                .show();
    }

    @JavascriptInterface
    public void setCloudId(String cloudId, String authToken) {
        ControlBinder control = mEngine.getControl();
        if (control == null)
            return;
        CloudAuthInfo oldInfo = this.authInfo;
        if (oldInfo != null && oldInfo.getCloudId().equals(cloudId) && oldInfo.getAuthToken().equals(authToken))
            return;

        CloudAuthInfo newInfo = new CloudAuthInfo(cloudId, authToken);
        final boolean ok = control.setCloudId(newInfo);
        if (ok)
            authInfo = newInfo;

        getActivity().runOnUiThread(new Runnable() {
            @Override
            public void run() {
                showConfirmDialog(ok);
            }
        });
    }

    // this version of onAttach is deprecated but it's required
    // on APIs older than 23 because otherwise onAttach is never called
    @Override
    public void onAttach(Activity activity) {
        super.onAttach(activity);
        if (activity instanceof FragmentEmbedder) {
            mListener = (FragmentEmbedder) activity;
            mEngine = mListener.getEngine();
        } else {
            throw new RuntimeException(activity.toString()
                    + " must implement AssistantFragmentEmbedder");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
    }
}
