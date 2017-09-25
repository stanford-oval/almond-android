package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;

import edu.stanford.thingengine.engine.AutoStarter;

/**
 * Created by silei on 9/20/16.
 */
public class SplashActivity extends Activity {

    SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        AutoStarter.startService(this);

        Intent intent;
        prefs = getSharedPreferences("edu.stanford.thingengine.engine", MODE_PRIVATE);
        if (prefs.getBoolean("first-run", true) || prefs.getBoolean("landing-page", false)) {
            intent = new Intent(this, IntroductionActivity.class);
        } else {
            intent = new Intent(this, BrassauActivity.class);
        }
        startActivity(intent);

        finish();
    }
}