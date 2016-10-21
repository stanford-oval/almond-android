package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.view.PagerAdapter;
import android.support.v4.view.ViewPager;
import android.util.Log;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.TextView;

import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

import edu.stanford.thingengine.engine.Config;
import edu.stanford.thingengine.engine.R;
import edu.stanford.thingengine.engine.service.ControlBinder;
import edu.stanford.thingengine.engine.service.DeviceInfo;

public class MyStuffActivity extends Activity {
    private static final int REQUEST_CREATE_DEVICE = 2;

    private MainServiceConnection mEngine;
    private final Runnable mReadyCallback = new Runnable() {
        @Override
        public void run() {
            refresh();
        }
    };
    private FragmentEmbedder mListener;

    private ArrayAdapter<DeviceInfo> mDevices;

    public MyStuffActivity() {}

    private class RefreshDevicesTask extends AsyncTask<Void, Void, List<DeviceInfo>> {
        @Override
        public List<DeviceInfo> doInBackground(Void... params) {
            try {
                ControlBinder control = mEngine.getControl();
                if (control == null)
                    return Collections.emptyList();

                return control.getDeviceInfos();
            } catch(Exception e) {
                Log.e(MainActivity.LOG_TAG, "Failed to retrieve device list", e);
                return Collections.emptyList();
            }
        }

        @Override
        public void onPostExecute(List<DeviceInfo> devices) {
            processDevices(devices);
        }
    }

    private class DeviceArrayAdapter extends ArrayAdapter<DeviceInfo> {
        public DeviceArrayAdapter() {
            super(MyStuffActivity.this, 0);
        }

        private String getIcon(DeviceInfo device) {
            return Config.S3_CLOUDFRONT_HOST + "/icons/" + device.kind + ".png";
        }

        private boolean tryConvert(View convertView, DeviceInfo device) {
            if (!(convertView instanceof LinearLayout))
                return false;

            LinearLayout linearLayout = (LinearLayout)convertView;
            View firstChild = linearLayout.getChildAt(0);
            if (!(firstChild instanceof ImageView))
                return false;
            ImageView icon = (ImageView)firstChild;

            View secondChild = linearLayout.getChildAt(1);
            if (!(secondChild instanceof TextView))
                return false;

            TextView name = (TextView)secondChild;

            LoadImageTask.load(MyStuffActivity.this, icon, getIcon(device));
            name.setText(device.name);
            return true;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            DeviceInfo device = getItem(position);
            if (tryConvert(convertView, device))
                return convertView;

            LinearLayout linearLayout = new LinearLayout(getContext());
            linearLayout.setOrientation(LinearLayout.VERTICAL);

            ImageView icon = new ImageView(getContext());
            int sixty_dp = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 60.f, getResources().getDisplayMetrics()));
            LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(sixty_dp, sixty_dp);
            iconParams.gravity = Gravity.CENTER_HORIZONTAL;
            int ten_dp = Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, 10.f, getResources().getDisplayMetrics()));
            iconParams.bottomMargin = ten_dp;
            icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
            linearLayout.addView(icon, iconParams);

            TextView text = new TextView(getContext());
            LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            text.setGravity(Gravity.CENTER_HORIZONTAL);
            textParams.gravity = Gravity.CENTER_HORIZONTAL;
            linearLayout.addView(text, textParams);

            LoadImageTask.load(MyStuffActivity.this, icon, getIcon(device));
            text.setText(device.name);

            return linearLayout;
        }
    }

    private class OnCreateButtonClicked implements View.OnClickListener {
        private final String _class;

        public OnCreateButtonClicked(String _class) {
            this._class = _class;
        }

        @Override
        public void onClick(View view) {
            Intent intent = new Intent(MyStuffActivity.this, DeviceConfigureChooseKindActivity.class);
            intent.setAction(DeviceConfigureChooseKindActivity.ACTION);
            intent.putExtra("extra.CLASS", _class);

            startActivityForResult(intent, REQUEST_CREATE_DEVICE);
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_my_stuff);
        mEngine = MainActivity.engine;

        mDevices = new DeviceArrayAdapter();

        ListAdapter[] adapters = new ListAdapter[] { mDevices };
        int[] view_ids = new int[] { R.id.my_devices_view };
        for (int i = 0; i < view_ids.length; i++) {
            ListAdapter adapter = adapters[i];
            GridView view = (GridView) this.findViewById(view_ids[i]);

            view.setAdapter(adapter);
            view.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                @Override
                public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                    DeviceInfo device = (DeviceInfo) parent.getAdapter().getItem(position);

                    String _class = device.isDataSource ? "data" : (device.isOnlineAccount ? "online" : "physical");
                    Intent intent = new Intent(MyStuffActivity.this, DeviceDetailsActivity.class);
                    intent.setAction(DeviceDetailsActivity.ACTION);
                    intent.putExtra("extra.INFO", device);
                    intent.putExtra("extra.CLASS", _class);

                    startActivity(intent);
                }
            });
        }

        int[] button_ids = new int[] { R.id.btn_create_device, R.id.btn_create_account };
        String[] classes = new String[] { "physical", "online" };

        for (int i = 0; i < classes.length; i++) {
            Button btn = (Button) findViewById(button_ids[i]);
            String _class = classes[i];

            btn.setOnClickListener(new OnCreateButtonClicked(_class));
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        mEngine.addEngineReadyCallback(mReadyCallback);
        refresh();
    }

    @Override
    public void onPause() {
        super.onPause();

        mEngine.removeEngineReadyCallback(mReadyCallback);
    }

    public void refresh() {
        new RefreshDevicesTask().executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void processDevices(Collection<DeviceInfo> devices) {
        mDevices.clear();

        for (DeviceInfo device : devices) {
            if (device.isThingEngine)
                continue;
            mDevices.add(device);
        }

        mDevices.sort(new Comparator<DeviceInfo>() {
            @Override
            public int compare(DeviceInfo lhs, DeviceInfo rhs) {
                return lhs.name.compareTo(rhs.name);
            }
        });
    }

    /*
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_my_stuff, container, false);
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
                    + " must implement FragmentEmbedder");
        }
    }

    @Override
    public void onDetach() {
        super.onDetach();
        mListener = null;
        mEngine = null;
    }*/

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        super.onActivityResult(requestCode, resultCode, intent);

        if (requestCode == REQUEST_CREATE_DEVICE) {
            // do something with it
        }
    }

    /**
     * Created by silei on 9/22/16.
     */
    public static class IntroductionActivity extends Activity{

        String commands[];
        String descriptions[];
        LayoutInflater inflater;
        ViewPager vp;

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.activity_introduction);

            Button btn = (Button) findViewById(R.id.start_sabrina);
            btn.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    SharedPreferences prefs = getSharedPreferences("edu.stanford.thingengine.engine", MODE_PRIVATE);
                    if (prefs.getBoolean("first-run", true))
                        prefs.edit().putBoolean("first-run", false).apply();
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
            vp.addOnPageChangeListener(new ViewPager.OnPageChangeListener() {
                @Override
                public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {}
                @Override
                public void onPageScrollStateChanged(int state) {}
                @Override
                public void onPageSelected(int position) {
                    LinearLayout indicator = (LinearLayout) findViewById(R.id.page_indicators);
                    for (int i = 0; i < commands.length; i++) {
                        ImageView dot = (ImageView) indicator.getChildAt(i);
                        if (i == position)
                            dot.setImageResource(R.drawable.page_indicator_current);
                        else
                            dot.setImageResource(R.drawable.page_indicator);
                    }
                    if (position == commands.length - 1) {
                        Button btn = (Button) findViewById(R.id.start_sabrina);
                        btn.setVisibility(View.VISIBLE);
                    }
                }
            });

            LinearLayout indicator = (LinearLayout) findViewById(R.id.page_indicators);
            ImageView currentDot = new ImageView(this);
            currentDot.setImageResource(R.drawable.page_indicator_current);
            indicator.addView(currentDot);
            for (int i = 1; i < commands.length; i++) {
                ImageView dot = new ImageView(this);
                dot.setImageResource(R.drawable.page_indicator);
                indicator.addView(dot);
            }
        }

        class HighlightAdapter extends PagerAdapter {
            @Override
            public int getCount() {
                return commands.length;
            }

            @Override
            public Object instantiateItem(ViewGroup container, int position) {
                View page = inflater.inflate(R.layout.layout_highlight, null);
                ((TextView)page.findViewById(R.id.highlight_cmd)).setText(commands[position]);
                ((TextView)page.findViewById(R.id.highlight_description)).setText(descriptions[position]);
                container.addView(page, 0);
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
}
