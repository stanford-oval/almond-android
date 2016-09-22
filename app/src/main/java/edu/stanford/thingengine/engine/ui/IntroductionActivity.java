package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.Button;

import edu.stanford.thingengine.engine.R;

/**
 * Created by silei on 9/22/16.
 */
public class IntroductionActivity extends Activity{
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_introduction);

        Button btn = (Button) findViewById(R.id.start_sabrina);
        btn.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View view) {

                Intent intent = new Intent(IntroductionActivity.this, MainActivity.class);
                startActivity(intent);
                finish();
            }
        });
    }
}
