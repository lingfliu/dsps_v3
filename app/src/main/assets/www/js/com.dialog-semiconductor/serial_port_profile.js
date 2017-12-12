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

cordova.define("com.dialog-semiconductor.profile.serial_port", function(require, exports, module) {
		var BC = require("com.dialog-semiconductor.service.serial_port");
		
		var serviceUUID   = "0783b03e-8535-b5a0-7140-a304d2495cb7";
		var XON = "01";
		var XOFF = "02";
		var serial_port = {};
		var SerialPortProfile = BC.DialogSerialPortProfile = BC.Profile.extend({

			open : function(device,readCallback,flowControlCallback,successFunc,errorFunc){
				console.log("serial profile open");
				var that=this;
				device.connect(function(){
					serial_port.device = device;
					serial_port.readCallback = readCallback;
					serial_port.flowControlCallback = flowControlCallback;
					serial_port.flowControl = XON;
					device.discoverServices(function(){
						serial_port.service = device.getServiceByUUID(serviceUUID)[0];
						if (serial_port.service !== undefined){
							serial_port.service.subscribeRead(serial_port.readCallback, function(){
								serial_port.service.subscribeFlowControl(serial_port.flowControlCallback, successFunc, errorFunc);
							}, errorFunc);
						}else{
							errorFunc();
							alert("The remote device doesn't support the DSPS profile.");
						}
					},function(){
						errorFunc();
						if (!app.arti_disconnect)
							alert("Service discovery failed");
					});
					
				},function(){
					errorFunc();
					if (!app.arti_disconnect)
						alert("Connection failed");
				});
			},

			startReceive : function(device){	
				serial_port.service = device.getServiceByUUID(serviceUUID)[0];
				serial_port.service.subscribeRead(serial_port.readCallback);
			},

			stopReceive : function(){
				serial_port.service.unsubscribeRead();
			},

			close : function(device,successFunc,errorFunc){
				console.log("serial profile close");
				// Try to unsubscribe, disconnect in all cases
				var disconnect = function() {
					serial_port = {};
					device.disconnect(successFunc, errorFunc);
				};
				if (serial_port.service !== undefined){
					serial_port.service.unsubscribeRead(function(){
						serial_port.service.unsubscribeFlowControl(disconnect, disconnect);
					}, disconnect);
				}else{
					disconnect();
				}
			},

			read : function(device,successFunc,errorFunc){
			},
			
			write : function(writeType,writeValue,successFunc,errorFunc){
				if(serial_port.service){
					if (serial_port.flowControl == XON){
						serial_port.service.write(writeType,writeValue,successFunc,errorFunc);
					}else{
						errorFunc();
					}
				}
			},
			writeFlowControl : function(writeType,writeValue,successFunc,errorFunc){
				if(serial_port.service){
					serial_port.service.writeFlowControl(writeType,writeValue,successFunc,errorFunc);
				}
			}
		});
		module.exports = BC;
});

