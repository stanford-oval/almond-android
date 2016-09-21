package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;

/**
 * Created by silei on 9/20/16.
 */
public class SplashActivity extends Activity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent intent = new Intent(this, MainActivity.class);
        startActivity(intent);
        finish();
    }
}