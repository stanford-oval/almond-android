// This file is part of Almond
//
// Copyright 2016-2017 The Board of Trustees of the Leland Stanford Junior University
//
// See COPYING for details
//
package edu.stanford.thingengine.engine.ui;

import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import edu.stanford.thingengine.engine.R;

public class CheatsheetActivity extends Activity {

    private WebView webview;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_cheatsheet);

        webview = (WebView) findViewById(R.id.cheatsheet_webview);
        webview.setWebViewClient(new WebViewClient());
        webview.loadUrl("https://thingengine.stanford.edu/thingpedia/cheatsheet");

        //TODO: hide the navigation bar from the web page instead of action bar of the app
        getActionBar().hide();
    }

}
