package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import edu.stanford.thingengine.engine.R;

/**
 * Created by silei on 9/22/16.
 */
public class IntroductionActivity extends Activity{

    String commands[];
    String descriptions[];
    LayoutInflater inflater;
    ViewPager vp;

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

        commands = getResources().getStringArray(R.array.sabrina_highlights);
        descriptions = getResources().getStringArray(R.array.sabrina_highlights_description);
        inflater = (LayoutInflater) getSystemService(Context.LAYOUT_INFLATER_SERVICE);
        vp = (ViewPager)findViewById(R.id.sabrina_highlights);
        vp.setAdapter(new HighlightAdapter());
    }

    class HighlightAdapter extends PagerAdapter {
        @Override
        public int getCount() {
            return commands.length;
        }

        @Override
        public Object instantiateItem(ViewGroup container, int position) {
            View page = inflater.inflate(R.layout.highlight, null);
            ((TextView)page.findViewById(R.id.highlight_cmd)).setText(commands[position]);
            ((TextView)page.findViewById(R.id.highlight_description)).setText(descriptions[position]);
            container.addView(page, 0);
            if (position == getCount() - 1) {
                Button btn = (Button) findViewById(R.id.start_sabrina);
                btn.setVisibility(View.VISIBLE);
            }
            return page;
        }

        @Override
        public boolean isViewFromObject(View arg0, Object arg1) {
            return arg0 == arg1;
        }

        @Override
        public void destroyItem(ViewGroup container, int position, Object object) {
            container.removeView((View) object);
            object = null;
        }
    }
}
