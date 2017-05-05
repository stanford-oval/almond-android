package edu.stanford.thingengine.engine.jsapi;

import android.Manifest;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.ContactsContract;
import android.support.v4.content.ContextCompat;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import edu.stanford.thingengine.engine.service.ControlChannel;
import edu.stanford.thingengine.engine.service.EngineService;
import edu.stanford.thingengine.engine.ui.InteractionCallback;

/**
 * Created by gcampagn on 8/8/16.
 */
public class ContactAPI extends JavascriptAPI {
    private final EngineService ctx;

    public ContactAPI(EngineService ctx, ControlChannel control) {
        super("Contacts", control);

        this.ctx = ctx;

        registerAsync("lookup", new GenericCall() {
            @Override
            public Object run(Object... args) throws Exception {
                return lookup((String)args[0], (String)args[1]);
            }
        });
    }

    private void requestPermission() throws InterruptedException {
        InteractionCallback callback = ctx.getInteractionCallback();
        if (callback == null)
            return;

        callback.requestPermission(Manifest.permission.READ_CONTACTS, InteractionCallback.REQUEST_CONTACTS);
    }

    private JSONObject contactToJson(String dataType, Cursor cursor) throws JSONException {
        JSONObject obj = new JSONObject();

        String value;
        switch (dataType) {
            case "phone_number":
                value = cursor.getString(3);
                break;
            case "email_address":
                value = cursor.getString(1);
                break;
            case "contact":
                String mimeType = cursor.getString(0);
                switch (mimeType) {
                    case ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE:
                        value = "phone:" + cursor.getString(3);
                        break;
                    case ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE:
                        value = "email:" + cursor.getString(1);
                        break;
                    default:
                        return null;
                }
                break;
            default:
                throw new AssertionError();
        }

        obj.put("value", value);
        obj.put("type", cursor.getInt(1));
        obj.put("displayName", cursor.getString(4));
        obj.put("alternativeDisplayName", cursor.getString(5));
        obj.put("isPrimary", cursor.getInt(6) != 0);
        obj.put("starred", cursor.getInt(7) != 0);
        obj.put("timesContacted", cursor.getInt(8));
        return obj;
    }

    private JSONArray lookup(String dataType, String search) throws InterruptedException {
        Uri table;

        switch (dataType) {
            case "phone_number":
                table = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
                break;
            case "email_address":
                table = ContactsContract.CommonDataKinds.Email.CONTENT_URI;
                break;
            case "contact":
                table = ContactsContract.Data.CONTENT_URI;
                break;
            default:
                throw new IllegalArgumentException("Invalid data type " + dataType);
        }

        int permissionCheck = ContextCompat.checkSelfPermission(ctx, Manifest.permission.READ_CONTACTS);
        if (permissionCheck != PackageManager.PERMISSION_GRANTED)
            requestPermission();

        try (Cursor cursor = ctx.getContentResolver().query(table,
                new String[] {
                        ContactsContract.Data.MIMETYPE,
                        ContactsContract.Data.DATA1,
                        ContactsContract.Data.DATA2,
                        ContactsContract.Data.DATA4,
                        ContactsContract.Contacts.DISPLAY_NAME,
                        ContactsContract.Contacts.DISPLAY_NAME_ALTERNATIVE,
                        ContactsContract.Data.IS_PRIMARY,
                        ContactsContract.Contacts.STARRED,
                        ContactsContract.Contacts.TIMES_CONTACTED },
                ContactsContract.Contacts.DISPLAY_NAME + " like ? or " + ContactsContract.Contacts.DISPLAY_NAME_ALTERNATIVE + " like ? "
                + " and (" + ContactsContract.Data.MIMETYPE + " == '" + ContactsContract.CommonDataKinds.Email.CONTENT_ITEM_TYPE + "' or "
                + ContactsContract.Data.MIMETYPE + " == '" + ContactsContract.CommonDataKinds.Phone.CONTENT_ITEM_TYPE + "')",
                new String[] { "%" + search.toLowerCase() + "%", "%" + search.toLowerCase() + "%" },
                null)) {
            if (cursor == null || !cursor.moveToFirst())
                return new JSONArray();

            JSONArray ret = new JSONArray();
            while (!cursor.isAfterLast()) {
                try {
                    JSONObject obj = contactToJson(dataType, cursor);
                    if (obj == null) {
                        cursor.moveToNext();
                        continue;
                    }
                    ret.put(obj);
                } catch(JSONException e) {
                    Log.e(EngineService.LOG_TAG, "Unexpected JSON exception in marshalling contact", e);
                }
                cursor.moveToNext();
            }

            return ret;
        }
    }
}
