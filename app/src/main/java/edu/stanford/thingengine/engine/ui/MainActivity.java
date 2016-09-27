package edu.stanford.thingengine.engine.ui;


import android.app.ActionBar;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentManager;
import android.app.FragmentTransaction;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.v13.app.FragmentPagerAdapter;
import android.support.v4.app.ActivityCompat;
import android.support.v4.view.ViewPager;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Toast;
import android.view.Window;
import android.widget.Toolbar;

import net.hockeyapp.android.CrashManager;
import net.hockeyapp.android.UpdateManager;
import net.hockeyapp.android.metrics.MetricsManager;

import edu.stanford.thingengine.engine.AutoStarter;
import edu.stanford.thingengine.engine.BuildConfig;
import edu.stanford.thingengine.engine.R;
import edu.stanford.thingengine.engine.service.ControlBinder;

public class MainActivity extends Activity implements ActionBar.TabListener, FragmentEmbedder, ActivityCompat.OnRequestPermissionsResultCallback {
    public static final String LOG_TAG = "thingengine.UI";

    private SectionsPagerAdapter mSectionsPagerAdapter;
    private ViewPager mViewPager;

    private final MainServiceConnection engine;

    public class SectionsPagerAdapter extends FragmentPagerAdapter {
        private final static int SECTION_CHAT = 0;
        private final static int SECTION_MYSTUFF = 1;
        private final static int SECTION_RULES = 2;

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            switch (position) {
                case SECTION_CHAT:
                    return AssistantFragment.newInstance();
                case SECTION_MYSTUFF:
                    return MyStuffFragment.newInstance();
                case SECTION_RULES:
                    return RulesFragment.newInstance();
                default:
                    return null;
            }
        }

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case SECTION_CHAT:
                    return getString(R.string.chat_tab);
                case SECTION_MYSTUFF:
                    return getString(R.string.mystuff_tab);
                case SECTION_RULES:
                    return getString(R.string.myrules_tab);
                default:
                    throw new IllegalArgumentException("Invalid fragment position");
            }
        }
    }

    public MainActivity() {
        engine = new MainServiceConnection();
    }

    @Override
    public MainServiceConnection getEngine() {
        return engine;
    }

    @Override
    public void switchToMyGoods() {
        mViewPager.setCurrentItem(SectionsPagerAdapter.SECTION_MYSTUFF, true);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_ACTION_BAR);

        setContentView(R.layout.activity_main);

        mSectionsPagerAdapter = new SectionsPagerAdapter(getFragmentManager());

        mViewPager = (ViewPager) findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);

        ActionBar actionBar = getActionBar();
        if (actionBar == null) {
            Toolbar toolbar = new Toolbar(this);
            setActionBar(toolbar);
            actionBar = getActionBar();
        }
        if (actionBar != null) {
            actionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_TABS);

            final ActionBar finalActionBar = actionBar;
            mViewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
                @Override
                public void onPageSelected(int position) {
                    finalActionBar.setSelectedNavigationItem(position);
                }
            });

            for (int i = 0; i < mSectionsPagerAdapter.getCount(); i++) {
                actionBar.addTab(actionBar.newTab()
                        .setText(mSectionsPagerAdapter.getPageTitle(i))
                        .setTabListener(this));
            }
        }

        UpdateManager.register(this);
        if (!BuildConfig.DEBUG) {
            MetricsManager.register(this, getApplication());
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        engine.start(this);
        CrashManager.register(this);
    }

    @Override
    public void onPause() {
        super.onPause();
        engine.stop(this);
        UpdateManager.unregister();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.action_settings) {
            startActivity(new Intent(this, SettingsActivity.class));
            return true;
        }

        if (id == R.id.action_clear_chat) {
            ControlBinder control = engine.getControl();
            if (control == null)
                return true;

            control.getAssistant().handleClear();
            Toast.makeText(getApplicationContext(), "The conversation has been reset.",
                    Toast.LENGTH_LONG).show();
            mViewPager.setCurrentItem(SectionsPagerAdapter.SECTION_CHAT, true);
            return true;
        }

        if (id == R.id.action_help) {
            ControlBinder control = engine.getControl();
            if (control == null)
                return true;

            control.getAssistant().handleHelp();
            mViewPager.setCurrentItem(SectionsPagerAdapter.SECTION_CHAT, true);
            return true;
        }

        if (id == R.id.action_train) {
            ControlBinder control = engine.getControl();
            if (control == null)
                return true;

            control.getAssistant().handleTrain();
            mViewPager.setCurrentItem(SectionsPagerAdapter.SECTION_CHAT, true);
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        Fragment fragment = getFragmentManager().findFragmentByTag("android:switcher:" + R.id.container + ":0");

        if (fragment instanceof ActivityCompat.OnRequestPermissionsResultCallback)
            ((ActivityCompat.OnRequestPermissionsResultCallback)fragment).onRequestPermissionsResult(requestCode, permissions, grantResults);

        engine.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        engine.onActivityResult(requestCode, resultCode, intent);
    }

    @Override
    public void onTabSelected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
        mViewPager.setCurrentItem(tab.getPosition());
        View focus = getCurrentFocus();
        if (focus != null) {
            hiddenKeyboard(focus);
        }
    }

    @Override
    public void onTabUnselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    @Override
    public void onTabReselected(ActionBar.Tab tab, FragmentTransaction fragmentTransaction) {
    }

    private void hiddenKeyboard(View v) {
        InputMethodManager keyboard = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
        keyboard.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }
}
