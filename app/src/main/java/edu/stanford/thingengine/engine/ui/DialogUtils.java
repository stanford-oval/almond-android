package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;

/**
 * Created by gcampagn on 6/27/16.
 */
public class DialogUtils {
    private DialogUtils() {}

    public static void showConfirmDialog(Activity activity, String message, DialogInterface.OnClickListener onconfirm, DialogInterface.OnClickListener ondismiss) {
        new AlertDialog.Builder(activity)
                .setTitle("Confirm")
                .setMessage(message)
                .setPositiveButton(android.R.string.ok, onconfirm)
                .setNegativeButton(android.R.string.cancel, ondismiss)
                .setCancelable(false)
                .create()
                .show();
    }

    public static void showAlertDialog(Activity activity, String message, AlertDialog.OnClickListener onclick) {
        new AlertDialog.Builder(activity)
                .setTitle("Alert")
                .setMessage(message)
                .setCancelable(false)
                .setPositiveButton(android.R.string.ok, onclick)
                .create().show();
    }

    public static void showFailureDialog(final Activity activity, String message) {
        showAlertDialog(activity, message,
                new AlertDialog.OnClickListener()
                {
                    public void onClick(DialogInterface dialog, int which)
                    {
                        activity.setResult(Activity.RESULT_CANCELED);
                        activity.finish();
                    }
                });
    }
}
