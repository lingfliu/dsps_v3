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
	cordova.define("com.dialog-semiconductor.service.serial_port", function(require, exports, module) {
		var BC = cordova.require("org.bcsphere.bluetooth.bcjs");

		var SerialPortService = BC.DialogSerialPortService = BC.Service.extend({
			
			serialPortCharacterUUID : "0783b03e-8535-b5a0-7140-a304d2495cb8",
			flowControlNotifyCharacterUUID : "0783b03e-8535-b5a0-7140-a304d2495cb9",
			serialPortCharacterUUID2 : "0783b03e-8535-b5a0-7140-a304d2495cba",
			

			subscribeRead : function(callback, success, error){
				var service = this;
				this.discoverCharacteristics(function(){
					service.getCharacteristicByUUID(service.serialPortCharacterUUID)[0].subscribe(callback, success, error);
				}, error);
			},
			
			unsubscribeRead : function(success, error){
				var service = this;
				this.discoverCharacteristics(function(){
					service.getCharacteristicByUUID(service.serialPortCharacterUUID)[0].unsubscribe(success, error);
				}, error);
			}, 

			write : function(writeType,writeValue,successFunc,errorFunc){
				var service = this;
				this.discoverCharacteristics(function(){
					service.getCharacteristicByUUID(service.serialPortCharacterUUID2)[0].write(writeType,writeValue,successFunc,errorFunc);
				}, errorFunc);
			},
			
			subscribeFlowControl : function(callback, success, error){
				var service = this;
				this.discoverCharacteristics(function(){
					service.getCharacteristicByUUID(service.flowControlNotifyCharacterUUID)[0].subscribe(callback, success, error);
				}, error);
			},
			
			unsubscribeFlowControl : function(success, error){
				var service = this;
				this.discoverCharacteristics(function(){
					service.getCharacteristicByUUID(service.flowControlNotifyCharacterUUID)[0].unsubscribe(success, error);
				}, error);
			}, 

			writeFlowControl : function(writeType,writeValue,successFunc,errorFunc){
				var service = this;
				this.discoverCharacteristics(function(){
					service.getCharacteristicByUUID(service.flowControlNotifyCharacterUUID)[0].write(writeType,writeValue,successFunc,errorFunc);
				}, errorFunc);
			},

		});
		
		document.addEventListener('bccoreready',function(){
			BC.bluetooth.UUIDMap["0783b03e-8535-b5a0-7140-a304d2495cb7"] = BC.DialogSerialPortService;
		});
		module.exports = BC;
	});

