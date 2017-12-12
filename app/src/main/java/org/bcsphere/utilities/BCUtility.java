/*
	Copyright 2013-2014, JUMA Technology

	Licensed under the Apache License, Version 2.0 (the "License");
	you may not use this file except in compliance with the License.
	You may obtain a copy of the License at

		http://www.apache.org/licenses/LICENSE-2.0

	Unless required by applicable law or agreed to in writing, software
	distributed under the License is distributed on an "AS IS" BASIS,
	WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
	See the License for the specific language governing permissions and
	limitations under the License.
*/

/*
 * Modified from original source file by Dialog Semiconductor.
 */

package org.bcsphere.utilities;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.util.Log;

public class BCUtility extends CordovaPlugin{

	private CallbackContext AppManageCallback;
	private CallbackContext removeCallback;
	private Context mContext;
	
	@Override
	public void initialize(CordovaInterface cordova, CordovaWebView webView) {
		super.initialize(cordova, webView);
		mContext = cordova.getActivity().getApplicationContext();
	}
	
	@Override
	public boolean execute(String action, JSONArray args,CallbackContext callbackContext) throws JSONException {
		if (action.equals("redirectToApp")) {
			String url = args.getJSONObject(0).getString("url");
			String deviceAddress = args.getJSONObject(0).getString("deviceaddress");
			String deviceType = args.getJSONObject(0).getString("devicetype");
			String deviceName = args.getJSONObject(0).getString("devicename");
			String appName = args.getJSONObject(0).getString("appname");
			String smallIcon = args.getJSONObject(0).getString("smallicon");
			String isTemporary  =  args.getJSONObject(0).getString("istemporary");
			String ak = args.getJSONObject(0).getString("ak");
			Intent urlIntent = new Intent();
			urlIntent.setAction("OPEN_APPLICATION");
			urlIntent.putExtra("url", url);
			urlIntent.putExtra("deviceAddress", deviceAddress);
			urlIntent.putExtra("deviceType", deviceType);
			urlIntent.putExtra("deviceName", deviceName);
			urlIntent.putExtra("appName", appName);
			urlIntent.putExtra("smallIcon", smallIcon);
			urlIntent.putExtra("isTemporary", isTemporary);
			urlIntent.putExtra("ak", ak);
			mContext.sendBroadcast(urlIntent);
			callbackContext.success();
		}else if (action.equals("openApp")){
			//String appid = args.getJSONObject(0).getString("appid");
			//Intent appidIntent = new Intent();
			//appidIntent.setAction("APP_URL");
			//appidIntent.putExtra("appid", appid);
			//appidIntent.putExtra("app_url", appid_url.get(appid));
			//appidIntent.putExtra("deviceID", "null");
			//this.webView.getContext().sendBroadcast(appidIntent);
		}else if(action.equals("addApp")){
			Log.i("BCUtility", "addApp");
			AppManageCallback = callbackContext;
			IntentFilter mFilter = new IntentFilter();
			mFilter.addAction("appInfo");
			mContext.registerReceiver(mReceiver, mFilter);
		}else if (action.equals("removeApp")) {
			removeCallback = callbackContext;
			IntentFilter mFilter = new IntentFilter();
			mFilter.addAction("removeApp");
			mContext.registerReceiver(mReceiver, mFilter);
		}else if(action.equals("openApps")){
			Intent urlIntent = new Intent();
			for (int i = 0; i < args.length(); i++) {
				String url = args.getJSONObject(i).getString("url");
				String deviceAddress = args.getJSONObject(i).getString("deviceaddress");
				String deviceType = args.getJSONObject(i).getString("devicetype");
				String deviceName = args.getJSONObject(i).getString("devicename");
				String appName = args.getJSONObject(i).getString("appname");
				String smallIcon = args.getJSONObject(i).getString("smallicon");
				String isTemporary  =  args.getJSONObject(i).getString("istemporary");
				String ak  =  args.getJSONObject(i).getString("ak");
				urlIntent.setAction("OPEN_ALL_APPLICATION");
				urlIntent.putExtra("url", url);
				urlIntent.putExtra("deviceAddress", deviceAddress);
				urlIntent.putExtra("deviceType", deviceType);
				urlIntent.putExtra("deviceName", deviceName);
				urlIntent.putExtra("appName", appName);
				urlIntent.putExtra("smallIcon", smallIcon);
				urlIntent.putExtra("isTemporary", isTemporary);
				urlIntent.putExtra("ak", ak);
				mContext.sendBroadcast(urlIntent);
			};
			callbackContext.success();
		}else if("exitApp".equals(action)){
			cordova.getActivity().finish();
		}
		return true;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();
		if(mReceiver.isOrderedBroadcast()){
			mContext.unregisterReceiver(mReceiver);
		}
	}

	BroadcastReceiver mReceiver = new BroadcastReceiver() {

		@Override
		public void onReceive(Context arg0, Intent intent) {
			if (intent.getAction().equals("appInfo")) {
				String url = intent.getStringExtra("url");
				String deviceAddress = intent.getStringExtra("deviceAddress");
				String deviceType = intent.getStringExtra("deviceType");
				String deviceName =  intent.getStringExtra("deviceName");
				String appName =  intent.getStringExtra("appName");
				String smallIcon =  intent.getStringExtra("smallIcon");
				String isTemporary = intent.getStringExtra("isTemporary");
				String ak = intent.getStringExtra("ak");
				JSONObject obj = new JSONObject();
				try {
					obj.put("url", url);
					obj.put("deviceaddress", deviceAddress);
					obj.put("devicetype", deviceType);
					obj.put("devicename", deviceName);
					obj.put("appname", appName);
					obj.put("smallicon", smallIcon);
					obj.put("istemporary", isTemporary);
					obj.put("ak", ak);
				} catch (JSONException e) {
					e.printStackTrace();
				}
				PluginResult pluginResult = new PluginResult(PluginResult.Status.OK , obj);
				pluginResult.setKeepCallback(true);
				if (AppManageCallback != null) {
					AppManageCallback.sendPluginResult(pluginResult);
				}
			}else if (intent.getAction().equals("removeApp")) {
				String removeAppURL = intent.getStringExtra("removeAppURL");
				JSONObject obj = new JSONObject();
				try {
					obj.put("removeAppURL", removeAppURL);
				} catch (JSONException e) {
					e.printStackTrace();
				}
				PluginResult pluginResult = new PluginResult(PluginResult.Status.OK , obj);
				pluginResult.setKeepCallback(true);
				if (removeCallback != null) {
					removeCallback.sendPluginResult(pluginResult);
				}
			}
		}
	};
}
