/*
 *******************************************************************************
 *
 * Copyright (C) 2016 Dialog Semiconductor, unpublished work. This computer
 * program includes Confidential, Proprietary Information and is a Trade
 * Secret of Dialog Semiconductor. All use, disclosure, and/or reproduction
 * is prohibited unless authorized in writing. All Rights Reserved.
 *
 * bluetooth.support@diasemi.com
 *
 *******************************************************************************
 */

package com.dialog_semiconductor.dsps;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

public class SplashActivity extends Activity {

    private static final int SPLASH_TIME = 3000;
    private static final String TAG = "SplashActivity";
    private Handler mHandler = new Handler();

    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_splash);
        // Set font
        TextView appName = (TextView) findViewById(R.id.appName);
        appName.setTypeface(Typeface.createFromAsset(getAssets(), "www/fonts/MyriadPro-Light.otf"));
        // Dismiss timer
        Runnable exitRunnable = new Runnable() {
            @Override
            public void run() {
                dismissSplashScreen();
            }
        };
        mHandler.postDelayed(exitRunnable, SPLASH_TIME);
        // Dismiss on click
        findViewById(R.id.splash_view).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                dismissSplashScreen();
            }
        });
    }

    @Override
    public void onDestroy() {
        mHandler.removeCallbacksAndMessages(null);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        finish();
    }

    public void dismissSplashScreen() {
        Intent intent = new Intent(this, DSPS.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);
        finish();
    }
}