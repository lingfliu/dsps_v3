cordova.define("org.bcsphere.cordova.utilities", function(require, exports, module) {
/*
 *
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 *
*/

var exec = require('cordova/exec');
var platform = require('cordova/platform');
var interval_index = null;

/**
 * Provides access to bluetooth on the device.
 */
var utilities = {

    redirectToApp : function(success,error,data){
        cordova.exec(success,error,"BCUtility", "redirectToApp",[data]);
    },

    openApp : function(success,error,appid){
        cordova.exec(success,error,"BCUtility","openApp",[{"appid" : appid}]);
    },

    retry : function(success,error){
        cordova.exec(success,error,"BCUtility","retry",[]);
    },

    addApp : function(success,error){
        cordova.exec(success,error,"BCUtility","addApp",[]);
    },

    removeApp : function(success,error){
        cordova.exec(success,error,"BCUtility","removeApp",[]);
    },

    openApps : function(success,error,apps){
        cordova.exec(success,error,"BCUtility","openApps",apps);
    },

    exitApp:function(success,error){
        cordova.exec(success,error,"BCUtility","exitApp",[]);
    }
};
module.exports = utilities;
});
