/*
	Copyright 2013-2014, JUMA Technology
	Copyright (C) 2016 Dialog Semiconductor

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

package org.bcsphere.bluetooth;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.UUID;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.PluginResult;
import org.bcsphere.bluetooth.tools.Tools;
import org.json.JSONArray;
import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothAdapter.LeScanCallback;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.util.Log;

@SuppressLint("NewApi")
public class BluetoothG43plus implements IBluetooth{
	private static final String TAG = "BluetoothG43plus";
	private BluetoothManager mBluetoothManager;
	private BluetoothAdapter mBluetoothAdapter;
	private BluetoothGattServer mBluetoothGattServer;
	private Context mContext;
	private boolean isScanning = false;
	private int scanSum = 0;
	private boolean isOpenGattServer = false;
	private int gattServerSum = 0;
	private HashMap<String, CallbackContext> connectCC = new HashMap<String, CallbackContext>();
	private HashMap<String, CallbackContext> disconnectCC = new HashMap<String, CallbackContext>();
	private HashMap<String, DisconnectCheck> disconnectCheck = new HashMap<String, DisconnectCheck>();
	private HashMap<String, CallbackContext> getServicesCC = new HashMap<String, CallbackContext>();
	private HashMap<String, CallbackContext> writeValueCC = new HashMap<String, CallbackContext>();
	private HashMap<String, CallbackContext> readValueCC = new HashMap<String, CallbackContext>();
	private HashMap<String, CallbackContext> writeClientConfigurationCC = new HashMap<String, CallbackContext>();
	private HashMap<BluetoothGattCharacteristic, CallbackContext> setNotificationCC = new HashMap<BluetoothGattCharacteristic, CallbackContext>();
	private HashMap<String, CallbackContext> getDeviceAllDataCC = new HashMap<String, CallbackContext>();
	private HashMap<String ,CallbackContext> getRSSICC = new HashMap<String, CallbackContext>();
	private HashMap<String, CallbackContext> addEventListenerCC = new HashMap<String, CallbackContext>();
	private CallbackContext addServiceCC;
	private HashMap<String, BluetoothGattService> localServices = new HashMap<String, BluetoothGattService>();
	private HashMap<BluetoothGattCharacteristic, Integer> recordServiceIndex = new HashMap<BluetoothGattCharacteristic, Integer>();
	private HashMap<BluetoothGattCharacteristic, Integer> recordCharacteristicIndex = new HashMap<BluetoothGattCharacteristic, Integer>();
	private HashMap< String ,Boolean> connectedDevice = new HashMap<String, Boolean>(); 
	private HashMap<String, BluetoothGatt> mBluetoothGatts = new HashMap<String, BluetoothGatt>();
	private HashMap<String, List<BluetoothGattService>> deviceServices = new HashMap<String, List<BluetoothGattService>>();
	private HashMap<String, List<BleOpData>> pendingGattOps = new HashMap<String, List<BleOpData>>();
	
	private Thread mThread;
	private Handler mHandler;

	private static final int MSG_RESUME_PENDING_GATT_OPS = 0x80000001;

	private static enum BleOp {
		startScan, stopScan,
		connect, disconnect,
		getServices, getDeviceAllData,
		getCharacteristics, getDescriptors,
		readValue, readValueManage, readDescriptorManage,
		writeValue, writeValueManage,
		setNotification, setNotificationManage,
	}

	private static class BleOpData {
		BleOp op;
		JSONArray json;
		CallbackContext callbackContext;
		BluetoothGatt gatt;
		Object gattObj;
		byte[] value;
		int status;

		public BleOpData(BleOp op, JSONArray json, CallbackContext callbackContext, BluetoothGatt gatt, Object gattObj, int status) {
			this.op = op;
			this.json = json;
			this.callbackContext = callbackContext;
			this.gatt = gatt;
			this.gattObj = gattObj;
			if (gattObj instanceof BluetoothGattCharacteristic)
				this.value = ((BluetoothGattCharacteristic) gattObj).getValue().clone();
			if (gattObj instanceof BluetoothGattDescriptor)
				this.value = ((BluetoothGattDescriptor) gattObj).getValue().clone();
			this.status = status;
		}
	}

	@SuppressLint("HandlerLeak")
	private class BleOpHandler extends Handler {
		@Override
		public void handleMessage(Message msg) {
			if (msg.what == MSG_RESUME_PENDING_GATT_OPS) {
				resumePendingGattOperations((String) msg.obj);
				return;
			}
			BleOpData data = (BleOpData) msg.obj;
			Log.d(TAG, "BleOp: " + data.op.toString());
			switch (data.op) {
			case startScan:
				startScan(data.json, data.callbackContext);
				break;
			case stopScan:
				stopScan(data.json, data.callbackContext);
				break;
			case connect:
				connect(data.json, data.callbackContext);
				break;
			case disconnect:
				disconnect(data.json, data.callbackContext);
				break;
			case getDeviceAllData:
				getDeviceAllData(data.json, data.callbackContext);
				break;
			case getServices:
				getServices(data.json, data.callbackContext);
				break;
			case getCharacteristics:
				getCharacteristics(data.json, data.callbackContext);
				break;
			case getDescriptors:
				getDescriptors(data.json, data.callbackContext);
				break;
			case readValue:
				readValue(data.json, data.callbackContext);
				break;
			case writeValue:
				writeValue(data.json, data.callbackContext);
				break;
			case setNotification:
				setNotification(data.json, data.callbackContext);
				break;
			case readValueManage:
				readValueManage(data.gatt, (BluetoothGattCharacteristic) data.gattObj, data.value, data.status);
				break;
			case readDescriptorManage:
				readValueManage(data.gatt, (BluetoothGattDescriptor) data.gattObj, data.value, data.status);
				break;
			case writeValueManage:
				writeValueManage(data.gatt, data.status);
				break;
			case setNotificationManage:
				setNotificationManage(data.gatt, (BluetoothGattCharacteristic) data.gattObj, data.value);
				break;
			default:
				Log.e(TAG, "Unsupported BLE operation!");
				break;
			}
		}
	}

	private class BleOpThread extends Thread {
		@Override
		public void run() {
			Looper.prepare();

			synchronized (BluetoothG43plus.this) {
				mHandler = new BleOpHandler();
				BluetoothG43plus.this.notifyAll();
			}

			Log.i(TAG, "BLE operations thread start.");
			Looper.loop();
			Log.i(TAG, "BLE operations thread exit.");
		}
	}

	private synchronized void startBleOpThread(){
		if (mThread != null)
			return;
		mThread = new BleOpThread();
		mThread.start();
	}

	private synchronized void waitForHandler(){
		// Wait for handler initialization
		while (mHandler == null)
			try {
				wait();
			} catch (InterruptedException e) {}
	}

	private boolean checkThread(BleOp op, JSONArray json, CallbackContext callbackContext, BluetoothGatt gatt, Object gattObj, int status) {
		if (Thread.currentThread() == mThread)
			return false;
		if (mThread == null)
			startBleOpThread();
		if (mHandler == null)
			waitForHandler();
		Message msg = mHandler.obtainMessage();
		msg.obj = new BleOpData(op, json, callbackContext, gatt, gattObj, status);
		mHandler.sendMessage(msg);
		return true;
	}

	private boolean checkThread(BleOp op, JSONArray json, CallbackContext callbackContext) {
		return checkThread(op, json, callbackContext, null, null, 0);
	}

	private boolean checkThread(BleOp op, BluetoothGatt gatt, Object gattObj, int status) {
		return checkThread(op, null, null, gatt, gattObj, status);
	}

	private void postBleOperation(Runnable runnable) {
		if (Thread.currentThread() == mThread) {
			runnable.run();
			return;
		}
		if (mThread == null)
			startBleOpThread();
		if (mHandler == null)
			waitForHandler();
		mHandler.post(runnable);
	}

	private boolean checkPendingGattOperation(String deviceAddress, BleOp op, JSONArray json, CallbackContext callbackContext) {
		if (Thread.currentThread() != mThread) {
			if (operationPending(deviceAddress)) {
				Log.e(TAG, op + " failed: another pending operation for " + deviceAddress);
				Tools.sendErrorMsg(callbackContext);
				return true;
			}
			return false;
		}
		List<BleOpData> pendingGatt = pendingGattOps.get(deviceAddress);
		if (mHandler.hasMessages(MSG_RESUME_PENDING_GATT_OPS) || operationPending(deviceAddress) || pendingGatt != null && !pendingGatt.isEmpty()) {
			if (pendingGatt == null) {
				pendingGatt = new LinkedList<BleOpData>();
				pendingGattOps.put(deviceAddress, pendingGatt);
			}
			Log.d(TAG, "Pending GATT operation [" + deviceAddress + "]: " + op);
			pendingGatt.add(new BleOpData(op, json, callbackContext, null, null, 0));
			return true;
		}
		return false;
	}

	private void scheduleResumePendingGattOperations(String deviceAddress) {
		if (Thread.currentThread() != mThread)
			return;
		Message msg = mHandler.obtainMessage();
		msg.what = MSG_RESUME_PENDING_GATT_OPS;
		msg.obj = deviceAddress;
		mHandler.sendMessage(msg);
	}

	private void resumePendingGattOperations(String deviceAddress) {
		if (Thread.currentThread() != mThread)
			return;
		List<BleOpData> pendingGatt = pendingGattOps.remove(deviceAddress);
		if (pendingGatt == null || pendingGatt.isEmpty())
			return;
		int n = pendingGatt.size();
		Log.d(TAG, "Resuming GATT operations [" + deviceAddress + "], " + n + " pending");
		// Resume pending operations
		while (--n >= 0) {
			BleOpData data = pendingGatt.get(0);
			switch (data.op) {
			case readValue:
				readValue(data.json, data.callbackContext);
				break;
			case writeValue:
				writeValue(data.json, data.callbackContext);
				break;
			case setNotification:
				setNotification(data.json, data.callbackContext);
				break;
			default:
				Message msg = mHandler.obtainMessage();
				msg.obj = data;
				mHandler.sendMessage(msg);
				break;
			}
			// Operation still pending, restore pending operations list
			if (pendingGattOps.containsKey(deviceAddress)) {
				pendingGattOps.put(deviceAddress, pendingGatt);
				break;
			} else {
				pendingGatt.remove(0);
			}
		}
	}

	private class DisconnectCheck implements Runnable {
		String deviceAddress;

		public DisconnectCheck(String deviceAddress) {
			this.deviceAddress = deviceAddress;
		}

		@Override
		public void run() {
			if (disconnectCC.containsKey(deviceAddress)) {
				Log.d(TAG, "Restart pending disconnection.");
				mBluetoothGatts.get(deviceAddress).disconnect();
			}
		}
	}

	private void checkDisconnect(String deviceAddress, long delay) {
		// Remove previous disconnect check
		if (disconnectCheck.containsKey(deviceAddress))
			mHandler.removeCallbacks(disconnectCheck.remove(deviceAddress));
		DisconnectCheck check = new DisconnectCheck(deviceAddress);
		disconnectCheck.put(deviceAddress, check);
		mHandler.postDelayed(check, delay);
	}

	@Override
	public void setContext(Context context) {
		Log.i(TAG, "setContext");
		this.mContext = context;
		mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
		mBluetoothAdapter = mBluetoothManager.getAdapter();
		if (mThread == null)
			startBleOpThread();
	}

	@Override
	public void onDestroy() {
		Log.i(TAG, "onDestroy");
		if (isScanning) {
			Log.d(TAG, "Stopping scan in progress.");
			mBluetoothAdapter.stopLeScan(mLeScanCallback);
			isScanning = false;
		}
		if (!mBluetoothGatts.isEmpty()) {
			Log.d(TAG, "Closing active connections.");
			for (BluetoothGatt gatt : mBluetoothGatts.values()) {
				gatt.disconnect();
				gatt.close();
			}
		}
		if (mHandler != null && mHandler.getLooper() != null)
			mHandler.getLooper().quitSafely();
	}

	@Override
	public void startScan(JSONArray json, CallbackContext callbackContext) {
		if (checkThread(BleOp.startScan, json, callbackContext))
			return;
		Log.i(TAG, "startScan");
		if (isScanning) {
			Tools.sendSuccessMsg(callbackContext);
			scanSum = scanSum + 1;
			return;
		}

		UUID[] uuids = Tools.getUUIDs(json);
		if (uuids == null || uuids.length < 1) {
			mBluetoothAdapter.startLeScan(mLeScanCallback);
			Tools.sendSuccessMsg(callbackContext);
			scanSum = scanSum + 1;
			isScanning = true;
		}else {
			mBluetoothAdapter.startLeScan(uuids, mLeScanCallback);
			Tools.sendSuccessMsg(callbackContext);
			scanSum = scanSum + 1;	
			isScanning = true;
		}
	}

	@Override
	public void stopScan(JSONArray json, CallbackContext callbackContext) {
		if (checkThread(BleOp.stopScan, json, callbackContext))
			return;
		Log.i(TAG, "stopScan");
		if (!isScanning) {
			Tools.sendSuccessMsg(callbackContext);
			return;
		}
		mBluetoothAdapter.stopLeScan(mLeScanCallback);
		isScanning = false;
		Tools.sendSuccessMsg(callbackContext);
	}

	@Override
	public void connect(JSONArray json, CallbackContext callbackContext) {
		if (checkThread(BleOp.connect, json, callbackContext))
			return;
		Log.i(TAG, "connect");
		String deviceAddress = Tools.getData(json, Tools.DEVICE_ADDRESS);
		// Check for pending disconnection
		if (disconnectCC.containsKey(deviceAddress)) {
			Tools.sendErrorMsg(callbackContext);
			return;
		}
		// Remove previous disconnect check
		if (disconnectCheck.containsKey(deviceAddress))
			mHandler.removeCallbacks(disconnectCheck.remove(deviceAddress));
		if (connectedDevice.get(deviceAddress) != null) {
			Tools.sendSuccessMsg(callbackContext);
			return;
		}
		connectCC.put(deviceAddress, callbackContext);
		BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(deviceAddress);
		mBluetoothGatts.put(device.getAddress(), device.connectGatt(mContext, false, mGattCallback));
	}

	@Override
	public void disconnect(JSONArray json, CallbackContext callbackContext) {
		if (checkThread(BleOp.disconnect, json, callbackContext))
			return;
		Log.i(TAG, "disconnect");
		String deviceAddress = Tools.getData(json, Tools.DEVICE_ADDRESS);
		// Check for pending connection
		CallbackContext connectCallbackContext = connectCC.remove(deviceAddress);
		if (connectCallbackContext != null) {
			Tools.sendErrorMsg(connectCallbackContext);
			BluetoothGatt gatt = mBluetoothGatts.remove(deviceAddress);
			if (gatt != null) {
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e) {}
				gatt.disconnect();
				gatt.close();
			}
			Tools.sendSuccessMsg(callbackContext);
			return;
		}
		// Call error callback and remove any pending operations
		CallbackContext getServicesCallbackContext = getServicesCC.remove(deviceAddress);
		if (getServicesCallbackContext != null)
			Tools.sendErrorMsg(getServicesCallbackContext);
		CallbackContext readValueCallbackContext = readValueCC.remove(deviceAddress);
		if (readValueCallbackContext != null)
			Tools.sendErrorMsg(readValueCallbackContext);
		CallbackContext writeValueCallbackContext = writeValueCC.remove(deviceAddress);
		if (writeValueCallbackContext != null)
			Tools.sendErrorMsg(writeValueCallbackContext);
		CallbackContext writeClientConfigurationiCallbackContext = writeClientConfigurationCC.remove(deviceAddress);
		if (writeClientConfigurationiCallbackContext != null)
			Tools.sendErrorMsg(writeClientConfigurationiCallbackContext);
		List<BleOpData> pendingGatt = pendingGattOps.remove(deviceAddress);
		if (pendingGatt != null) {
			for (BleOpData data : pendingGatt)
				Tools.sendErrorMsg(data.callbackContext);
		}
		// Not connected
		if (connectedDevice.get(deviceAddress) == null) {
			Tools.sendSuccessMsg(callbackContext);
			return;
		}
		// Disconnect
		disconnectCC.put(deviceAddress, callbackContext);
		mBluetoothGatts.get(deviceAddress).disconnect();
		// Workaround for disconnect during service discovery
		if (getServicesCallbackContext != null)
			checkDisconnect(deviceAddress, 1000);
	}

	@Override
	public void getConnectedDevices(JSONArray json,
			CallbackContext callbackContext) {
		Log.i(TAG, "getConnectedDevices");
		JSONArray ary = new JSONArray();
		List<BluetoothDevice> devices = mBluetoothManager.getConnectedDevices(BluetoothProfile.GATT);
		for (int i = 0; i < devices.size(); i++) {
			JSONObject obj = new JSONObject();
			Tools.addProperty(obj, Tools.DEVICE_ADDRESS, devices.get(i).getAddress());
			Tools.addProperty(obj, Tools.DEVICE_NAME, devices.get(i).getName());
			ary.put(obj);
		}
		callbackContext.success(ary);
	}

	@Override
	public void writeValue(JSONArray json, CallbackContext callbackContext) {
		if (checkThread(BleOp.writeValue, json, callbackContext))
			return;
		Log.i(TAG, "writeValue");
		String deviceAddress = Tools.getData(json, Tools.DEVICE_ADDRESS);
		if (connectedDevice.get(deviceAddress) == null) {
			Log.e(TAG, "writeValue failed");
			Tools.sendErrorMsg(callbackContext);
			return;
		}
		if (checkPendingGattOperation(deviceAddress, BleOp.writeValue, json, callbackContext))
			return;
		int serviceIndex = Integer.parseInt(Tools.getData(json, Tools.SERVICE_INDEX));
		int characteristicIndex = Integer.parseInt(Tools.getData(json, Tools.CHARACTERISTIC_INDEX));
		String  descriptorIndex =Tools.getData(json, Tools.DESCRIPTOR_INDEX);
		String writeValue = Tools.getData(json, Tools.WRITE_VALUE);
		writeValueCC.put(deviceAddress, callbackContext);
		if (descriptorIndex.equals("")) {
			BluetoothGattCharacteristic characteristic = deviceServices.get(deviceAddress).get(serviceIndex)
					.getCharacteristics().get(characteristicIndex);
			characteristic.setValue(Tools.decodeBase64(writeValue));
			mBluetoothGatts.get(deviceAddress).writeCharacteristic(characteristic);
		}else {
			BluetoothGattDescriptor descriptor = deviceServices.get(deviceAddress).get(serviceIndex).getCharacteristics()
					.get(characteristicIndex).getDescriptors().get(Integer.parseInt(descriptorIndex));
			descriptor.setValue(Tools.decodeBase64(writeValue));
			mBluetoothGatts.get(deviceAddress).writeDescriptor(descriptor);
		}
	} 

	@Override
	public void readValue(JSONArray json, CallbackContext callbackContext) {
		if (checkThread(BleOp.readValue, json, callbackContext))
			return;
		Log.i(TAG, "readValue");
		String deviceAddress = Tools.getData(json, Tools.DEVICE_ADDRESS);
		if (connectedDevice.get(deviceAddress) == null) {
			Log.e(TAG, "readValue failed");
			Tools.sendErrorMsg(callbackContext);
			return;
		}
		if (checkPendingGattOperation(deviceAddress, BleOp.readValue, json, callbackContext))
			return;
		int serviceIndex = Integer.parseInt(Tools.getData(json, Tools.SERVICE_INDEX));
		int characteristicIndex = Integer.parseInt(Tools.getData(json, Tools.CHARACTERISTIC_INDEX));
		String  descriptorIndex =Tools.getData(json, Tools.DESCRIPTOR_INDEX);
		readValueCC.put(deviceAddress, callbackContext);
		if (descriptorIndex.equals("")) {
			mBluetoothGatts.get(deviceAddress).readCharacteristic(deviceServices.get(deviceAddress).get(serviceIndex)
					.getCharacteristics().get(characteristicIndex));
		}else {
			mBluetoothGatts.get(deviceAddress).readDescriptor(deviceServices.get(deviceAddress).get(serviceIndex)
					.getCharacteristics().get(characteristicIndex).getDescriptors().get(Integer.parseInt(descriptorIndex)));
		}
	}

	@Override
	public void setNotification(JSONArray json, CallbackContext callbackContext) {
		if (checkThread(BleOp.setNotification, json, callbackContext))
			return;
		Log.i(TAG, "setNotification");
		String deviceAddress = Tools.getData(json, Tools.DEVICE_ADDRESS);
		if (connectedDevice.get(deviceAddress) == null) {
			Log.e(TAG, "setNotification failed");
			Tools.sendErrorMsg(callbackContext);
			return;
		}
		if (checkPendingGattOperation(deviceAddress, BleOp.setNotification, json, callbackContext))
			return;
		int serviceIndex = Integer.parseInt(Tools.getData(json, Tools.SERVICE_INDEX));
		int characteristicIndex = Integer.parseInt(Tools.getData(json, Tools.CHARACTERISTIC_INDEX));
		String enable = Tools.getData(json, Tools.ENABLE);
		BluetoothGattCharacteristic characteristic = deviceServices.get(deviceAddress).get(serviceIndex).getCharacteristics()
				.get(characteristicIndex);
		BluetoothGattDescriptor descriptor = characteristic.getDescriptor(Tools.NOTIFICATION_UUID);
		if (enable.equals("true")) {
			setNotificationCC.put(characteristic, callbackContext);
			writeClientConfigurationCC.put(deviceAddress, callbackContext);
			if(Tools.lookup(characteristic.getProperties(),BluetoothGattCharacteristic.PROPERTY_NOTIFY)!=null){
			    descriptor.setValue(BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE);
			}else{
			    descriptor.setValue(BluetoothGattDescriptor.ENABLE_INDICATION_VALUE);
			}
			mBluetoothGatts.get(deviceAddress).writeDescriptor(descriptor);
			mBluetoothGatts.get(deviceAddress).setCharacteristicNotification(characteristic, true);
			recordServiceIndex.put(characteristic, serviceIndex);
			recordCharacteristicIndex.put(characteristic, characteristicIndex);
		}else {
			writeClientConfigurationCC.put(deviceAddress, callbackContext);
			descriptor.setValue(BluetoothGattDescriptor.DISABLE_NOTIFICATION_VALUE);
			mBluetoothGatts.get(deviceAddress).writeDescriptor(descriptor);
			mBluetoothGatts.get(deviceAddress).setCharacteristicNotification(characteristic, false);
			setNotificationCC.remove(characteristic);
			recordServiceIndex.remove(characteristic);
			recordCharacteristicIndex.remove(characteristic);
		}
	}

	@Override
	public void getDeviceAllData(JSONArray json, CallbackContext callbackContext) {
		if (checkThread(BleOp.getDeviceAllData, json, callbackContext))
			return;
		Log.i(TAG, "getDeviceAllData");
		String deviceAddress = Tools.getData(json, Tools.DEVICE_ADDRESS);
		if (connectedDevice.get(deviceAddress) == null) {
			Tools.sendErrorMsg(callbackContext);
			return;
		}
		getDeviceAllDataCC.put(deviceAddress, callbackContext);
		mBluetoothGatts.get(deviceAddress).discoverServices();
	}


	@Override
	public void removeServices(JSONArray json, CallbackContext callbackContext) {
		Log.i(TAG, "removeServices");
		String uniqueID = Tools.getData(json, Tools.UINQUE_ID);
		if (uniqueID.equals("")) {
			mBluetoothGattServer.clearServices();
			mBluetoothGattServer.close();
			isOpenGattServer = false;
			Tools.sendSuccessMsg(callbackContext);
		}else {
			if (mBluetoothGattServer.removeService(localServices.get(uniqueID))) {
				Tools.sendSuccessMsg(callbackContext);
			}else {
				Tools.sendErrorMsg(callbackContext);
			}
		}
	}

	@Override
	public void getRSSI(JSONArray json, CallbackContext callbackContext) {
		Log.i(TAG, "getRSSI");
		String deviceAddress = Tools.getData(json, Tools.DEVICE_ADDRESS);
		if (connectedDevice.get(deviceAddress) == null) {
			Tools.sendErrorMsg(callbackContext);
			return;
		}
		getRSSICC.put(deviceAddress, callbackContext);
		mBluetoothGatts.get(deviceAddress).readRemoteRssi();
	}

	@Override
	public void getServices(JSONArray json, CallbackContext callbackContext) {
		if (checkThread(BleOp.getServices, json, callbackContext))
			return;
		Log.i(TAG, "getServices");
		String deviceAddress = Tools.getData(json, Tools.DEVICE_ADDRESS);
		if (connectedDevice.get(deviceAddress) == null ) {
			Tools.sendErrorMsg(callbackContext);
			return;
		}
		mBluetoothGatts.get(deviceAddress).discoverServices();
		getServicesCC.put(deviceAddress, callbackContext);
	}

	@Override
	public void getCharacteristics(JSONArray json,
			CallbackContext callbackContext) {
		if (checkThread(BleOp.getCharacteristics, json, callbackContext))
			return;
		Log.i(TAG, "getCharacteristics");
		String deviceAddress = Tools.getData(json, Tools.DEVICE_ADDRESS);
		if (connectedDevice.get(deviceAddress) == null) {
			Tools.sendErrorMsg(callbackContext);
			return;
		}
		JSONObject obj = new JSONObject();
		JSONArray ary = new JSONArray();
		int serviceIndex = Integer.parseInt(Tools.getData(json, Tools.SERVICE_INDEX));
		Tools.addProperty(obj, Tools.DEVICE_ADDRESS, deviceAddress);
		List<BluetoothGattCharacteristic> characteristics = deviceServices.get(deviceAddress).get(serviceIndex).getCharacteristics();
		for (int i = 0; i < characteristics.size(); i++) {
			JSONObject infoObj = new JSONObject();
			Tools.addProperty(infoObj, Tools.CHARACTERISTIC_INDEX, i);
			Tools.addProperty(infoObj, Tools.CHARACTERISTIC_UUID, characteristics.get(i).getUuid());
			Tools.addProperty(infoObj, Tools.CHARACTERISTIC_NAME, Tools.lookup(characteristics.get(i).getUuid()));
			Tools.addProperty(infoObj, Tools.CHARACTERISTIC_PROPERTY, Tools.decodeProperty(characteristics.get(i).getProperties()));
			ary.put(infoObj);
		}
		Tools.addProperty(obj, Tools.CHARACTERISTICS, ary);
		callbackContext.success(obj);
	}

	@Override
	public void getDescriptors(JSONArray json, CallbackContext callbackContext) {
		if (checkThread(BleOp.getDescriptors, json, callbackContext))
			return;
		Log.i(TAG, "getDescriptors");
		String deviceAddress = Tools.getData(json, Tools.DEVICE_ADDRESS);
		if (connectedDevice.get(deviceAddress) == null) {
			Tools.sendErrorMsg(callbackContext);
			return;
		}
		JSONObject obj = new JSONObject();
		JSONArray ary = new JSONArray();
		int serviceIndex = Integer.parseInt(Tools.getData(json, Tools.SERVICE_INDEX));
		int characteristicIndex = Integer.parseInt(Tools.getData(json, Tools.CHARACTERISTIC_INDEX));
		List<BluetoothGattDescriptor> descriptors = deviceServices.get(deviceAddress).get(serviceIndex).getCharacteristics().get(characteristicIndex).getDescriptors();
		for (int i = 0; i < descriptors.size(); i++) {
			JSONObject infoObj = new JSONObject();
			Tools.addProperty(infoObj, Tools.DESCRIPTOR_INDEX, i);
			Tools.addProperty(infoObj, Tools.DESCRIPTOR_UUID, descriptors.get(i).getUuid());
			Tools.addProperty(infoObj, Tools.DESCRIPTOR_NAME, Tools.lookup(descriptors.get(i).getUuid()));
			ary.put(infoObj);
		}
		Tools.addProperty(obj, Tools.DEVICE_ADDRESS, deviceAddress);
		Tools.addProperty(obj, Tools.DESCRIPTORS, ary);
		callbackContext.success(obj);
	}

	@Override
	public void addEventListener(JSONArray json, CallbackContext callbackContext) {
		Log.i(TAG, "addEventListener");
		String eventName = Tools.getData(json, Tools.EVENT_NAME);
		if (eventName == null) {
			Tools.sendErrorMsg(callbackContext);
			return;
		}
		addEventListenerCC.put(eventName, callbackContext);
	}

	@Override
	public void addServices(JSONArray json, CallbackContext callbackContext) {
		Log.i(TAG, "addServices");
		if (!isOpenGattServer) {
			mBluetoothGattServer = mBluetoothManager.openGattServer(mContext, mGattServerCallback);
			isOpenGattServer = true;
		}
		addServiceCC = callbackContext;
		JSONArray services  = Tools.getArray(json, Tools.SERVICES);
		gattServerSum = services.length();
		for (int i = 0; i < services.length(); i++) {
			String uniqueID = Tools.getData(services, i, Tools.UINQUE_ID);
			int serviceType = -1;
			if (Tools.getData(services, i , Tools.SERVICE_TYPE).equals("0")) {
				serviceType = BluetoothGattService.SERVICE_TYPE_PRIMARY;
			}else {
				serviceType = BluetoothGattService.SERVICE_TYPE_SECONDARY;
			}
			UUID serviceUUID = UUID.fromString(Tools.getData(services, i , Tools.SERVICE_UUID));
			BluetoothGattService service =  new BluetoothGattService(serviceUUID, serviceType);
			JSONArray characteristics = Tools.getArray(services, i, Tools.CHARACTERISTICS);
			for (int j = 0; j <characteristics.length(); j++) {
				byte[] characteristicValue = Tools.decodeBase64(Tools.getData(characteristics, Tools.CHARACTERISTIC_VALUE));
				UUID characteristicUUID = UUID.fromString(Tools.getData(characteristics, Tools.CHARACTERISTIC_UUID));
				int characteristicProperty = Tools.encodeProperty(Tools.getArray(characteristics, Tools.CHARACTERISTIC_PROPERTY));
				int characteristicPermission = Tools.encodePermission(Tools.getArray(characteristics, Tools.CHARACTERISTIC_PERMISSION));
				BluetoothGattCharacteristic characteristic = new BluetoothGattCharacteristic(characteristicUUID, characteristicProperty, characteristicPermission);
				characteristic.setValue(characteristicValue);
				JSONArray descriptors = Tools.getArray(characteristics, j, Tools.DESCRIPTORS);
				for (int k = 0; k < descriptors.length(); k++) {
					byte[] descriptorValue =Tools.decodeBase64(Tools.getData(descriptors, Tools.DESCRIPTOR_VALUE));
					UUID descriptorUUID = UUID.fromString(Tools.getData(descriptors, Tools.DESCRIPTOR_UUID));
					int descriptorPermission = Tools.encodePermission(Tools.getArray(descriptors, Tools.DESCRIPTOR_PERMISSION));
					BluetoothGattDescriptor descriptor = new BluetoothGattDescriptor(descriptorUUID, descriptorPermission);
					descriptor.setValue(descriptorValue);
					characteristic.addDescriptor(descriptor);
				}
				service.addCharacteristic(characteristic);
			}
			if (mBluetoothGattServer.addService(service)) {
				localServices.put(uniqueID, service);
			}
		}
	}


	private BluetoothAdapter.LeScanCallback mLeScanCallback = new LeScanCallback() {
		
		@Override
		public void onLeScan(BluetoothDevice device, int rssi, byte[] scanRecord) {
			//Log.i(TAG, "onLeScan");
			startScanManage(device, rssi, scanRecord);
		}
	};

	private BluetoothGattCallback mGattCallback = new BluetoothGattCallback() {

		@Override
		public void onCharacteristicChanged(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic) {
			Log.i(TAG, "onCharacteristicChanged");
			super.onCharacteristicChanged(gatt, characteristic);
			if (checkThread(BleOp.setNotificationManage, gatt, characteristic, 0))
				return;
			setNotificationManage(gatt, characteristic, null);
		}

		@Override
		public void onCharacteristicRead(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {
			Log.i(TAG, "onCharacteristicRead");
			super.onCharacteristicRead(gatt, characteristic, status);
			if (checkThread(BleOp.readValueManage, gatt, characteristic, status))
				return;
			readValueManage(gatt, characteristic, null, status);
		}

		@Override
		public void onCharacteristicWrite(BluetoothGatt gatt,
				BluetoothGattCharacteristic characteristic, int status) {
			Log.i(TAG, "onCharacteristicWrite");
			super.onCharacteristicWrite(gatt, characteristic, status);
			if (checkThread(BleOp.writeValueManage, gatt, null, status))
				return;
			writeValueManage(gatt,status);
		}

		@Override
		public void onConnectionStateChange(final BluetoothGatt gatt, final int status, final int newState) {
			Log.i(TAG, "onConnectionStateChange");
			super.onConnectionStateChange(gatt, status, newState);
			postBleOperation(new Runnable() {
				@Override
				public void run() {
					String deviceAddress = gatt.getDevice().getAddress();
					if (connectCC.get(deviceAddress) != null) {
					    connectManage(gatt,newState);
					}else if(disconnectCC.get(deviceAddress) != null){
					    disconnectManage(gatt,newState);
					}else{
					    addEventListenerManage(gatt ,newState);
					}
				}
			});
		}

		@Override
		public void onDescriptorRead(BluetoothGatt gatt,
				BluetoothGattDescriptor descriptor, int status) {
			Log.i(TAG, "onDescriptorRead");
			super.onDescriptorRead(gatt, descriptor, status);
			if (checkThread(BleOp.readValueManage, gatt, descriptor, status))
				return;
			readValueManage(gatt, descriptor, null, status);
		}

		@Override
		public void onDescriptorWrite(BluetoothGatt gatt,
				BluetoothGattDescriptor descriptor, int status) {
			Log.i(TAG, "onDescriptorWrite");
			super.onDescriptorWrite(gatt, descriptor, status);
			if (checkThread(BleOp.writeValueManage, gatt, null, status))
				return;
			writeValueManage(gatt,status);
		}

		@Override
		public void onReadRemoteRssi(BluetoothGatt gatt, int rssi, int status) {
			Log.i(TAG, "onReadRemoteRssi");
			super.onReadRemoteRssi(gatt, rssi, status);
			getRSSIManage(gatt , rssi ,status);
		}

		@Override
		public void onServicesDiscovered(final BluetoothGatt gatt, final int status) {
			Log.i(TAG, "onServicesDiscovered");
			super.onServicesDiscovered(gatt, status);
			postBleOperation(new Runnable() {
				@Override
				public void run() {
					String deviceAddress = getDeviceAddress(gatt);
					// Check for pending disconnection (only for versions before marshmallow)
					if (disconnectCC.containsKey(deviceAddress) && android.os.Build.VERSION.SDK_INT < 23) {
						Log.d(TAG, "Restart pending disconnection after cancelling discovery.");
						mBluetoothGatts.get(deviceAddress).disconnect();
						return;
					}
					getServicesManage(gatt , status);
					getDeviceAllDataManage(gatt, status);
				}
			});
		}
	};

	private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {

		@Override
		public void onServiceAdded(int status, BluetoothGattService service) {
			Log.i(TAG, "onServiceAdded");
			super.onServiceAdded(status, service);
			addServiceManage(status);
		}
	};


	private void startScanManage(BluetoothDevice device , int rssi , byte[] scanRecord){
		JSONObject obj = new JSONObject();
		Tools.addProperty(obj, Tools.DEVICE_ADDRESS, device.getAddress());
		Tools.addProperty(obj, Tools.DEVICE_NAME, device.getName());
		Tools.addProperty(obj, Tools.IS_CONNECTED, Tools.IS_FALSE);
		Tools.addProperty(obj, Tools.RSSI, rssi);
		Tools.addProperty(obj, Tools.ADVERTISEMENT_DATA, Tools.decodeAdvData(scanRecord));
		Tools.addProperty(obj, Tools.TYPE, "BLE");
		PluginResult pluginResult = new PluginResult(PluginResult.Status.OK , obj);
		pluginResult.setKeepCallback(true);
		addEventListenerCC.get(Tools.NEW_ADV_PACKET).sendPluginResult(pluginResult);
	}

	private void connectManage(BluetoothGatt gatt, int newState){
		String deviceAddress = getDeviceAddress(gatt);
		JSONObject obj = new JSONObject();
		if (connectCC.get(deviceAddress) != null) {
			if (newState == BluetoothProfile.STATE_CONNECTED) {
				Tools.addProperty(obj, Tools.DEVICE_ADDRESS, deviceAddress);
				connectCC.get(deviceAddress).success(obj);
				connectCC.remove(deviceAddress);
				connectedDevice.put(deviceAddress, true);
				// Remove any previous pending operations
				readValueCC.remove(deviceAddress);
				writeValueCC.remove(deviceAddress);
				writeClientConfigurationCC.remove(deviceAddress);
				pendingGattOps.remove(deviceAddress);
			}else{
				Tools.addProperty(obj, Tools.DEVICE_ADDRESS, deviceAddress);
				connectCC.get(deviceAddress).error(obj);
				connectCC.remove(deviceAddress);
				mBluetoothGatts.get(deviceAddress).close();
				mBluetoothGatts.remove(deviceAddress);
			}
		}
	}

	private void disconnectManage(BluetoothGatt gatt , int newStatus){
		String deviceAddress = getDeviceAddress(gatt);
		JSONObject obj = new JSONObject();
		if (newStatus ==  BluetoothProfile.STATE_DISCONNECTED) {
			Tools.addProperty(obj, Tools.DEVICE_ADDRESS, getDeviceAddress(gatt));
			disconnectCC.get(deviceAddress).success(obj);
			disconnectCC.remove(deviceAddress);
			connectedDevice.remove(deviceAddress);
			if (deviceServices.get(deviceAddress) != null) {
				deviceServices.remove(deviceAddress);
			}
			mBluetoothGatts.get(deviceAddress).close();
			mBluetoothGatts.remove(deviceAddress);
		}else {
			Tools.addProperty(obj, Tools.DEVICE_ADDRESS, deviceAddress);
			disconnectCC.get(deviceAddress).error(obj);
			disconnectCC.remove(deviceAddress);
		}
	}

	private void getServicesManage(BluetoothGatt gatt , int status){
		String deviceAddress = getDeviceAddress(gatt);
		JSONObject obj = new JSONObject();
		JSONArray ary = new JSONArray();
		if (getServicesCC.get(deviceAddress) !=null) {
			if (deviceServices.get(deviceAddress)==null) {
				deviceServices.put(deviceAddress, gatt.getServices());
			}
			if (deviceServices.get(deviceAddress)!=null) {
				deviceServices.get(deviceAddress).remove(deviceAddress);
				deviceServices.put(deviceAddress, gatt.getServices());
			}
			Tools.addProperty(obj, Tools.DEVICE_ADDRESS, deviceAddress);
			for (int i = 0; i <deviceServices.get(deviceAddress).size(); i++) {
				JSONObject infoObj = new JSONObject();
				Tools.addProperty(infoObj, Tools.SERVICE_INDEX, i);
				Tools.addProperty(infoObj, Tools.SERVICE_UUID, deviceServices.get(deviceAddress).get(i).getUuid());
				Tools.addProperty(infoObj, Tools.SERVICE_NAME, Tools.lookup(deviceServices.get(deviceAddress).get(i).getUuid()));
				ary.put(infoObj);
			}
			Tools.addProperty(obj, Tools.SERVICES, ary);
			getServicesCC.get(deviceAddress).success(obj);
			getServicesCC.remove(deviceAddress);
		}
	}

	private void writeValueManage(BluetoothGatt gatt , int status){
		String deviceAddress = getDeviceAddress(gatt);
		scheduleResumePendingGattOperations(deviceAddress);
		if (writeValueCC.get(deviceAddress) != null) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				Tools.sendSuccessMsg(writeValueCC.get(deviceAddress));
				writeValueCC.remove(deviceAddress);
			}else {
				Tools.sendErrorMsg(writeValueCC.get(deviceAddress));
				writeValueCC.remove(deviceAddress);
			}
		}
		// Client configuration descriptor write from setNotification.
		// Must call setKeepCallback(true) before sending the result,
		// because the same callback is used by the notifications.
		if (writeClientConfigurationCC.get(deviceAddress) != null) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				PluginResult pluginResult = new PluginResult(PluginResult.Status.OK);
				pluginResult.setKeepCallback(true);
				writeClientConfigurationCC.get(deviceAddress).sendPluginResult(pluginResult);
				writeClientConfigurationCC.remove(deviceAddress);
			}else {
				PluginResult pluginResult = new PluginResult(PluginResult.Status.ERROR);
				pluginResult.setKeepCallback(true);
				writeClientConfigurationCC.get(deviceAddress).sendPluginResult(pluginResult);
				writeClientConfigurationCC.remove(deviceAddress);
			}
		}
	}

	private void readValueManage(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value, int status) {
		String deviceAddress = getDeviceAddress(gatt);
		scheduleResumePendingGattOperations(deviceAddress);
		JSONObject obj = new JSONObject();
		if (readValueCC.get(deviceAddress) != null) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				Tools.addProperty(obj, Tools.DEVICE_ADDRESS, deviceAddress);
				Tools.addProperty(obj, Tools.VALUE, Tools.encodeBase64(value != null ? value : characteristic.getValue()));
				Tools.addProperty(obj, Tools.DATE, Tools.getDateString());
				readValueCC.get(deviceAddress).success(obj);
				readValueCC.remove(deviceAddress);
			}else {
				Tools.sendErrorMsg(readValueCC.get(deviceAddress));
				readValueCC.remove(deviceAddress);
			}
		}
	}

	private void readValueManage(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, byte[] value, int status) {
		String deviceAddress = getDeviceAddress(gatt);
		scheduleResumePendingGattOperations(deviceAddress);
		JSONObject obj = new JSONObject();
		if (readValueCC.get(deviceAddress) != null) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				Tools.addProperty(obj, Tools.DEVICE_ADDRESS, deviceAddress);
				Tools.addProperty(obj, Tools.VALUE, Tools.encodeBase64(value != null ? value : descriptor.getValue()));
				Tools.addProperty(obj, Tools.DATE, Tools.getDateString());
				readValueCC.get(deviceAddress).success(obj);
				readValueCC.remove(deviceAddress);
			}else {
				Tools.sendErrorMsg(readValueCC.get(deviceAddress));
				readValueCC.remove(deviceAddress);
			}
		}
	}

	private void setNotificationManage(BluetoothGatt gatt, BluetoothGattCharacteristic characteristic, byte[] value) {
		String deviceAddress = getDeviceAddress(gatt);
		if (setNotificationCC.get(characteristic) != null) {
			JSONObject obj = new JSONObject();
			Tools.addProperty(obj, Tools.DEVICE_ADDRESS, deviceAddress);
			Tools.addProperty(obj, Tools.SERVICE_INDEX, recordServiceIndex.get(characteristic));
			Tools.addProperty(obj, Tools.CHARACTERISTIC_INDEX, recordCharacteristicIndex.get(characteristic));
			Tools.addProperty(obj, Tools.VALUE, Tools.encodeBase64(value != null ? value : characteristic.getValue()));
			Tools.addProperty(obj, Tools.DATE, Tools.getDateString());
			PluginResult pluginResult = new PluginResult(PluginResult.Status.OK , obj);
			pluginResult.setKeepCallback(true);
			setNotificationCC.get(characteristic).sendPluginResult(pluginResult);
		}
	}

	private void getDeviceAllDataManage(BluetoothGatt gatt , int status){
		String deviceAddress =  getDeviceAddress(gatt);
		if (getDeviceAllDataCC.get(deviceAddress) != null) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				JSONObject obj = new JSONObject();
				JSONArray servicesInfo = new JSONArray();
				List<BluetoothGattService> services = gatt.getServices();
				for (int i = 0; i < services.size(); i++) {
					JSONObject serviceInfo = new JSONObject();
					Tools.addProperty(serviceInfo, Tools.SERVICE_INDEX, i);
					Tools.addProperty(serviceInfo, Tools.SERVICE_UUID, services.get(i).getUuid());
					Tools.addProperty(serviceInfo, Tools.SERVICE_NAME, Tools.lookup(services.get(i).getUuid()));
					List<BluetoothGattCharacteristic>  characteristics = services.get(i).getCharacteristics();
					JSONArray characteristicsInfo = new JSONArray();
					for (int j = 0; j < characteristics.size(); j++) {
						JSONObject characteristicInfo = new JSONObject();
						Tools.addProperty(characteristicInfo, Tools.CHARACTERISTIC_INDEX, j);
						Tools.addProperty(characteristicInfo, Tools.CHARACTERISTIC_UUID, characteristics.get(j).getUuid());
						Tools.addProperty(characteristicInfo, Tools.CHARACTERISTIC_NAME,Tools.lookup(characteristics.get(j).getUuid()));
						Tools.addProperty(characteristicInfo, Tools.CHARACTERISTIC_PROPERTY, Tools.decodeProperty(characteristics.get(j).getProperties()));
						List<BluetoothGattDescriptor> descriptors = new ArrayList<BluetoothGattDescriptor>();
						JSONArray descriptorsInfo = new JSONArray();
						for (int k = 0; k < descriptors.size(); k++) {
							JSONObject descriptorInfo = new JSONObject();
							Tools.addProperty(descriptorInfo, Tools.DESCRIPTOR_INDEX, k);
							Tools.addProperty(descriptorInfo, Tools.DESCRIPTOR_UUID, descriptors.get(k).getUuid());
							Tools.addProperty(descriptorInfo, Tools.DESCRIPTOR_NAME, Tools.lookup(descriptors.get(k).getUuid()));
							descriptorsInfo.put(descriptorInfo);
						}
						Tools.addProperty(characteristicInfo, Tools.DESCRIPTORS, descriptorsInfo);
						characteristicsInfo.put(characteristicInfo);
					}
					Tools.addProperty(serviceInfo, Tools.CHARACTERISTICS, characteristicsInfo);
					servicesInfo.put(serviceInfo);
				}
				Tools.addProperty(obj, Tools.DEVICE_ADDRESS, deviceAddress);
				Tools.addProperty(obj, Tools.SERVICES, servicesInfo);
				getDeviceAllDataCC.get(deviceAddress).success(obj);
				getDeviceAllDataCC.remove(deviceAddress);
				deviceServices.put(deviceAddress, services);
			}else {
				Tools.sendErrorMsg(getDeviceAllDataCC.get(deviceAddress));
				getDeviceAllDataCC.remove(deviceAddress);
			}
		}
	}

	private void addServiceManage(int status){
		if (addServiceCC != null) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				gattServerSum = gattServerSum - 1 ;
				if (gattServerSum == 0) {
					Tools.sendSuccessMsg(addServiceCC);
					addServiceCC = null;
				}
			}else {
				gattServerSum = 0;
				Tools.sendErrorMsg(addServiceCC);
				addServiceCC = null;
			}
		}
	}

	private void getRSSIManage(BluetoothGatt gatt , int rssi , int status){
		String deviceAddress = getDeviceAddress(gatt);
		if (getRSSICC.get(deviceAddress)!=null) {
			if (status == BluetoothGatt.GATT_SUCCESS) {
				JSONObject obj = new JSONObject();
				Tools.addProperty(obj, Tools.DEVICE_ADDRESS, deviceAddress);
				Tools.addProperty(obj, Tools.RSSI, rssi);
				getRSSICC.get(deviceAddress).success(obj);
				getRSSICC.remove(deviceAddress);
			}else {
				Tools.sendErrorMsg(getRSSICC.get(deviceAddress));
				getRSSICC.remove(deviceAddress);
			}
		}
	}

	private void addEventListenerManage(BluetoothGatt gatt, int newState){
		String deviceAddress = getDeviceAddress(gatt);
		if (newState == BluetoothProfile.STATE_DISCONNECTED) {
			gatt.close();
			connectedDevice.remove(deviceAddress);
			mBluetoothGatts.remove(deviceAddress);
			JSONObject obj = new JSONObject();
			Tools.addProperty(obj, Tools.DEVICE_ADDRESS, deviceAddress);
			PluginResult pluginResult = new PluginResult(PluginResult.Status.OK , obj);
			pluginResult.setKeepCallback(true);
			addEventListenerCC.get(Tools.DISCONNECT).sendPluginResult(pluginResult);
		}
	}

	private String getDeviceAddress(BluetoothGatt gatt){
		return gatt.getDevice().getAddress();
	}

	private boolean operationPending(String deviceAddress) {
		return readValueCC.containsKey(deviceAddress)
				|| writeValueCC.containsKey(deviceAddress)
				|| writeClientConfigurationCC.containsKey(deviceAddress);
	}
}
