package edu.stanford.thingengine.engine;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;

public class TestActivity extends AppCompatActivity {

    private final EngineServiceConnection engine;

    public TestActivity() {
        engine = new EngineServiceConnection();
    }

    @Override
    public void onResume() {
        super.onResume();
        engine.start(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        engine.stop(this);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        AutoStarter.startService(this);

        setContentView(R.layout.activity_test);

        findViewById(R.id.clear_sync).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                clearSyncInfoClicked();
            }
        });
    }

    private void clearSyncInfoClicked() {
        SharedPreferences.Editor editor = getSharedPreferences("thingengine", Context.MODE_PRIVATE).edit();

        for (String a : new String[]{"device", "keyword", "app"}) {
            for (String b : new String[]{"server", "cloud"}) {
                String k = "syncdb-time-" + a + "-" + b;
                editor.putString(k, "0");
            }
        }

        editor.commit();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_test, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }
}
