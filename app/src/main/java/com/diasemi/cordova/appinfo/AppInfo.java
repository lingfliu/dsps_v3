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

package com.diasemi.cordova.appinfo;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;

import android.app.Activity;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;

public class AppInfo extends CordovaPlugin {

	private CordovaInterface cordova;

	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		this.cordova = cordova;
	}

	@Override
	public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
		if (action.equals("getVersion")) {
			getVersion(callbackContext);
			return true;
		}
		return false;
	}

	private void getVersion(CallbackContext callbackContext) {
		Activity activity = cordova.getActivity();
		PackageInfo info;
		try {
			info = activity.getPackageManager().getPackageInfo(activity.getPackageName(), 0);
		} catch (PackageManager.NameNotFoundException e) {
			callbackContext.error("getVersion error: "+e.getMessage());
			return;
		}
		callbackContext.success(info.versionName);
	}
}
