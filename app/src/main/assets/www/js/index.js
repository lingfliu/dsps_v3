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

var app = {
    MTU: {
        "android": 128,
        "ios": 128
    },

    // global
    device: {},
    running_platform: null,
    flowcontrol_data: 1,
    g_connecting: 0,
    g_connected: 0,
    g_disconnecting: 0,
    connecting_start: 0,
    connecting_timeout: null,
    disconnect_timer: {},
    tx_timers: [],
    ftx_mode: 0,
    device_counter: 0,
    selectedFile: {},
    // rx
    dataBuffer: null,
    writerFileEntry: null,
    writer: null,
    writeBuffer: null,
    writeTimer: null,
    writing: false,
    lastReceive: 0,
    lastScroll: 0,
    end_data_flag: 1,

    // file tx
    ftx_interval: 5,

    // cyclic tx
    cycle_tx_stop: 0,
    cycle_tx_ongoing: 1,
    cycle_tx_timer: null,
    arti_disconnect: 0,

    searchAgainTimer: null,
    consoleCounter: true,
    deviceListScrollTop: 0,
    connecting_screen: false,
    connecting_animation: false,
    page_transition: false,
    sendingFile: false,
    selectedFile: null,
    fileOffset: 0,
    fileChunkSize: 102400,

    // RSSI
    RSSIrange: [
        [-130, -110],
        [-110, -90],
        [-90, -75],
        [-75, -60],
        [-60, -40],
        [-40, 0]
    ],
    switchRSSI: function (RSSI) {
        for (var i = 0, len = app.RSSIrange.length; i < len; i++) {
            var tempArr = app.RSSIrange[i];
            if (RSSI <= tempArr[1]) {
                return i;
            }
        }
        return 0;
    },

    initialize: function () {
        app.bindCordovaEvents();
        app.setTextAreaH();
        app.about();

        // Customize alert method
        app.alert = function(alert, msg) { alert(msg); }.bind(app, window.alert);
        window.alert = function(msg, callback) {
            if (!navigator.notification || !navigator.notification.alert) {
                app.alert(msg);
                return;
            }
            app.alertWait = true;
            navigator.notification.alert(msg, function() {
                app.alertWait = false;
                if (callback)
                    callback();
            }, "DSPS");
        };
    },

    about: function () {
        var setVersion = function (version) {
            $("#appVersion").html(version);
        }
        cordova.exec(setVersion, function () {}, "AppInfo", "getVersion", []);
    },

    bindCordovaEvents: function () {
        document.addEventListener('deviceready', app.onDeviceReady, false);
        document.addEventListener('bcready', app.onBCReady, false);
        document.addEventListener('backbutton', app.onBackButton, false);
    },

    onDeviceReady: function () {
        var BC = window.BC = cordova.require("com.dialog-semiconductor.profile.serial_port");
        var FileReader = window.FileReader = cordova.require('cordova-plugin-file.FileReader');

        // Enable disconnect timer on return to scan page
        $(document).on("pagecontainertransition", function (event, ui) {
            app.page_transition = false;
            if (ui.toPage.is("#scanPage") && app.device && (app.device.isConnected || app.device.isConnecting)) {
                app.startDisconnectionTimer(5000);
            }
        });

        // Disable text selection
        $("*").not("input,textarea").css("-webkit-user-select", "none");
    },

    onBCReady: function () {
        app.bindUIEvent();
        app.running_platform = app.testPlatform();

        app.DialogSerialPortProfile = new BC.DialogSerialPortProfile();
        if (BC.bluetooth.isopen) {
            app.onBluetoothScan();
        } else {
            alert("Please turn on Bluetooth!");
            BC.bluetooth.addEventListener("bluetoothstatechange", app.onBluetoothScan);
        }
    },

    onBackButton: function () {
        console.log("onBackButton");
        if (slideout.isOpen()) {
            slideout.close();
            return;
        }
        if (app.page_transition) {
            console.log("back button disabled during page transition");
            return;
        }
        if ($.mobile.activePage.is('#scanPage')) {
            if (app.connecting_animation || app.connecting_screen && !app.allowBackButton()) {
                console.log("back button disabled");
                return;
            }
            if (app.connecting_screen) {
                app.scanPageCancel();
                return;
            }
        }
        if ($.mobile.activePage.is('#scanPage') || $.mobile.navigate.history.activeIndex == 0)
            app.scanPageExit();
        else if ($.mobile.activePage.is('#mainPage')) {
            if (!app.allowBackButton()) {
                console.log("back button disabled");
                return;
            }
            app.mainPageDisconnect();
            app.backToScanPage();
        } else
            $.mobile.back();
    },

    onSoftBackButton: function () {
        console.log("onSoftBackButton");
        if (app.page_transition) {
            console.log("back button disabled during page transition");
            return;
        }
        if (!app.allowBackButton()) {
            console.log("back button disabled");
            return;
        }
        app.mainPageDisconnect();
        app.backToScanPage();
    },

    allowBackButton: function () {
        return Date.now() < app.connecting_start || Date.now() - app.connecting_start > 1250;
    },

    toggleLoader: function (state) {
        console.log("toggleLoader: " + state);
        if (state === true) {
            $("#loader").css({
                "display": ""
            });
        } else {
            $("#loader").css({
                "display": "none"
            });
        }
    },

    restoreSearchList: function () {
        app.connecting_screen = false;
        app.toggleLoader(false);
        $(".cancelConnection").css("display", "none").off("click");
        $(".cancelConnection>img").css({ 'margin-top': "0em", height: "0em", width: "0em" });
        var deviceList = $("#deviceList");
        deviceList.css("opacity", 0);
        deviceList.scrollTop(app.deviceListScrollTop);
        deviceList.clearQueue();
        deviceList.fadeTo(500, 1);
        $(".navigation-bar__center>.deviceName").html("DSPS");
        $(".navigation-bar__center>.deviceAddress").html("");
        $("#searchText").html(app.device_counter + (app.device_counter > 1 ? " devices found" : " device found"));
    },

    startDisconnectionTimer: function (delay) {
        if (!app.device)
            return;
        clearTimeout(app.disconnect_timer[app.device.deviceAddress]);
        app.disconnect_timer[app.device.deviceAddress] = setTimeout(function (device, time) {
            if (device && (device.isConnected || device.isConnecting)) {
                console.log("Disconnecting from device " + device.deviceAddress);
                callback = function (result) {
                    console.log("Disconnect from " + device.deviceAddress + " " + result);
                    if (time == app.connecting_start)
                        app.g_disconnecting = 0;
                };
                device.disconnect(callback.bind(app, "success"), callback.bind(app, "failure"));
            }
        }.bind(app, app.device, app.connecting_start), delay);
    },

    onBluetoothScan: function () {
        $("#searchText").html("Searching...");
        BC.bluetooth.addEventListener("newdevice", app.addNewDevice);
        BC.Bluetooth.StartScan("LE");
        app.stopScan();
    },

    testPlatform: function () {
        if (/android/i.test(navigator.userAgent)) {
            return "android";
        } else if (/ipad|iphone|mac/i.test(navigator.userAgent)) {
            return "ios"
        } else {
            throw new Error("Neither Android nor IOS platform");
        }
    },

    bindUIEvent: function () {
        $('#reload').click(function () {
            $("#searchText").html("Searching...");

            var deviceInfo = $('#deviceItem').siblings().remove();
            app.device_counter = 0;

            BC.bluetooth.devices = {};
            BC.Bluetooth.StopScan();
            BC.Bluetooth.StartScan("LE");
            app.stopScan();
        });
    },

    offset: function (ele) {
        var l = ele.offsetLeft;
        var t = ele.offsetTop;
        var p = ele.offsetParent;

        while (p) {
            l += p.offsetLeft + p.clientLeft;
            t += p.offsetTop + p.clientTop;
            p = p.offsetParent;
        }

        return {
            top: t,
            left: l
        };
    },

    addNewDevice: function (data) {
        // Check advertising data for DSPS service.
        if (!data.target.advertisementData.serviceUUIDs.contains("B75C49D204A34071A0B535853EB08307") && !data.target.advertisementData.serviceUUIDs.contains("0783b03e-8535-b5a0-7140-a304d2495cb7")) {
            return;
        }
        var deviceFoundStr = (++app.device_counter) > 1 ? " devices found" : "device found";

        $("#searchText").html(app.device_counter + " " + deviceFoundStr);
        var device = data.target;
        var listGroup = $('#deviceList');
        var deviceInfo = $('#deviceItem').clone();

        if (device.deviceName) {
            $('#deviceName', deviceInfo).html(device.deviceName);
        } else {
            $('#deviceName', deviceInfo).html('Unknown')
        }
        $('#deviceAddress', deviceInfo).html(device.deviceAddress);
        $('.wrap_active', deviceInfo).addClass("showActive" + app.switchRSSI(device.RSSI));
        $('#rssiValue', deviceInfo).html(device.RSSI + ' dB');
        if (!app.connecting_screen)
            deviceInfo.show();
        deviceInfo.click(function () {

            if (app.g_connecting || app.g_disconnecting || app.connecting_screen) {
                console.log("operation pending");
                return;
            }
            app.g_connecting = 1;
            app.connecting_start = Date.now();

            if (device.deviceName) {
                $(".navigation-bar__center>.deviceName").html(device.deviceName);
            } else {
                $(".navigation-bar__center>.deviceName").html("none");
            }
            $(".navigation-bar__center>.deviceAddress").html(device.deviceAddress);
            clearTimeout(app.searchAgainTimer);
            BC.Bluetooth.StopScan();

            clearTimeout(app.disconnect_timer[device.deviceAddress]);

            // Connecting screen
            app.connecting_screen = true;
            app.deviceListScrollTop = listGroup.scrollTop();
            // Start connecting animation
            app.connecting_animation = true;
            app.start(device.deviceAddress);
            setTimeout(function () {
                app.connecting_animation = false;
                if (!app.connecting_screen)
                    return;
                if (app.g_connected) {
                    app.showMainPage();
                    return;
                }
                setTimeout(function () {
                    if (!app.connecting_screen || app.g_connected)
                        return;
                    $(".cancelConnection").css("display", "block");
                    $(".cancelConnection>img").css({
                        'margin-top': "0em",
                        height: "0em",
                        width: "0em"
                    }).animate({
                        'margin-top': "3.75em",
                        height: "3em",
                        width: "3em"
                    }, 500, function() {
                        $(".cancelConnection").click(function() {
                            app.scanPageCancel();
                        });
                    });
                }, 750);
            }, 750);
        });
        listGroup.append(deviceInfo);
    },

    start: function (deviceAddress) {
        console.log("start");
        app.toggleLoader(true);
        app.device = BC.bluetooth.devices[deviceAddress];
        app.arti_disconnect = 0;
        app.device.addEventListener("devicedisconnected", app.onDeviceDisconnected, false);
        app.deviceOpen();
        if (app.consoleCounter) {
            app.consoleMode();
            app.consoleCounter = false;
        }
    },

    showMainPage: function () {
        console.log("showMainPage");
        app.clearShowData();
        app.clearConsoleModeData();
        app.clearWriteValue();
        app.changeToPage("#mainPage");
        app.toggleLoader(false);

        // var timerPulse = window.setInterval(app.writePulse, 1000);
         var timerRegAck = window.setInterval(app.writeRegAck, 1300);
        var timerOverflow = window.setInterval(app.writeOverflow, 10);
    },
    writeOverflow: function() {
        var msgOverflow = '';
        for (var m = 0; m < 45; m ++){
        msgOverflow += '00AA00CCE7';
        }
        app.writeDataMTU("HEX", msgOverflow);
        console.log("logcat: pulse write");
    },

    writePulse: function() {
        var msgPulse = '00AA00CCE7';
        app.writeDataMTU("HEX", msgPulse);
        console.log("logcat: pulse write");
    },

    writeRegAck: function() {

        var msgRegAck = '00AA00CCE63030303030303136030019f430303030303030303030303030303030';
        app.writeDataMTU("HEX", msgRegAck);
        console.log("logcat: regack write");
    },

    backToScanPage: function () {
        console.log("backToScanPage");
        slideout.close();
        app.restoreSearchList();
        app.changeToPage("#scanPage");
    },

    changeToPage: function (page) {
        console.log("changeToPage: " + page);
        if (!$.mobile.activePage.is(page))
            app.page_transition = true;
        $.mobile.changePage(page);
    },

    setTextAreaH: function () {
        var inputInfos = document.getElementsByClassName("inputInfo");

        // These calculations depend on values that are not known before rendering.
        // After the UI modifications they are not correct any more. For now, we use a fixed size.
        var inputInfoH;

        // RX/TX tab (must already be selected)
        /*var inputInfoH = window.innerHeight - $(".btn:eq(2)").outerHeight() - $(".btn:eq(3)").outerHeight() -
            $(".tab_bar").outerHeight() - $(".navigation-bar").outerHeight() - 8 -
            2 * $(".inputInfo").css("padding-top").replace("px", "") -
            2 * $(".inputInfo").css("padding-bottom").replace("px", "");
        inputInfoH = Math.floor(inputInfoH / 2);*/
        inputInfoH = Math.floor(window.innerHeight / 3);
        inputInfos[2].style.height = inputInfoH + "px";
        inputInfos[3].style.height = inputInfoH + "px";

        // Change tab
        $(".tabContent").each(function (index, val) {
                if (index == 0) {
                    $(val).addClass("selected");
                } else {
                    $(val).removeClass("selected");
                }
            })
        // Console tab
        /*inputInfoH = window.innerHeight - $(".btn:eq(0)").outerHeight() - $(".btn:eq(1)").outerHeight() -
            $(".tab_bar").outerHeight() - $(".navigation-bar").outerHeight() - 8 -
            $(".showConsoleMode:eq(0)").outerHeight() - $(".showConsoleMode:eq(1)").outerHeight() -
            2 * $(".inputInfo").css("padding-top").replace("px", "") -
            2 * $(".inputInfo").css("padding-bottom").replace("px", "");
        inputInfoH = Math.floor(inputInfoH / 2);*/
        inputInfoH = Math.floor(window.innerHeight / 3);
        inputInfos[0].style.height = inputInfoH + "px";
        inputInfos[1].style.height = inputInfoH + "px";
    },

    stopScan: function () {
        window.clearTimeout(app.searchAgainTimer);
        app.searchAgainTimer = setTimeout(function () {
            BC.Bluetooth.StopScan();
            if (app.device_counter == 0)
                $("#searchText").html("No devices found");
        }, 10000);
    },

    disconnectSuccessFunc: function () {
        console.log("disconnect success");
        app.g_disconnecting = 0;
        app.clearShowData();
        app.clearConsoleModeData();
        app.clearWriteValue();
        app.onDeviceDisconnected_signal();
        app.toggleLoader(false);
        window.clearInterval(app.cycle_tx_timer);
        app.tx_timers.forEach(function (item, index) {
            clearTimeout(item);
        });
        app.tx_timers = [];
    },
    disconnectErroFunc: function () {
        console.log("disconnect fail");
        app.g_disconnecting = 0;
    },

    //main page disconnect
    mainPageDisconnect: function () {
        console.log("mainPageDisconnect");
        if (app.g_disconnecting) {
            console.log("operation pending");
            return;
        }
        if (app.writerFileEntry && app.writer && !app.writer.length && !app.writing && !app.dataBuffer && !app.writeBuffer) {
            var name = app.writerFileEntry.name;
            app.writerFileEntry.remove(function() {
                console.log("Deleted empty log file " + name);
            }, function() {});
            app.writerFileEntry = null;
        }
        app.g_disconnecting = 1;
        app.arti_disconnect = 1;
        app.DialogSerialPortProfile.close(app.device, app.disconnectSuccessFunc, app.disconnectErroFunc);
    },
    //menu disconnect button
    menuDisconnect: function () {
        console.log("menuDisconnect");
        app.mainPageDisconnect();
        app.backToScanPage();
    },
    //scan page cancel
    scanPageCancel: function () {
        console.log("cancel connection");
        clearTimeout(app.connecting_timeout);
        app.mainPageDisconnect();
        app.startDisconnectionTimer(5000);
        app.restoreSearchList();
    },
    //scan page exit
    scanPageExit: function () {
        BC.bluetooth.stopScan();
        BCUtility.exitApp();
    },

    /*---------------------------------------------------------------------------------------------------------------------------------------------*/
    // GAP
    onDeviceDisconnected: function (arg) {
        if (app.sendingFile)
            app.cancelFileSend();
        if (!app.arti_disconnect) {
            app.g_connecting = 0;
            app.g_connected = 0;
            clearTimeout(app.connecting_timeout);
            alert(arg.deviceName + " [" + arg.deviceAddress + "] disconnected!");
            app.backToScanPage();
        }
    },

    onDeviceDisconnected_signal: function () {
        if (app.sendingFile)
            app.cancelFileSend();
        app.g_connecting = 0;
        app.g_connected = 0;
        clearTimeout(app.connecting_timeout);
    },

    openSuccessFunc: function () {
        app.g_connecting = 0;
        app.g_connected = 1;
        clearTimeout(app.connecting_timeout);
        console.log("connect success");
        if (!app.connecting_animation && !app.g_disconnecting && !$.mobile.activePage.is("#mainPage")) {
            app.showMainPage();
        }
    },
    openErroFunc: function () {
        app.g_connecting = 0;
        app.g_connected = 0;
        clearTimeout(app.connecting_timeout);
        console.log("connect fail");
        if (app.connecting_screen) {
            setTimeout(function () {
                app.scanPageCancel();
            }, 100);
        }
    },
    deviceOpen: function () {
        console.log("deviceOpen");
        requestFileSystem(LocalFileSystem.PERSISTENT, 0, app.gotFS, app.fail);
        app.DialogSerialPortProfile.open(app.device, app.defaultReceive, app.flowControlCallback, app.openSuccessFunc, app.openErroFunc);
        app.connecting_timeout = setTimeout(function () {
            app.scanPageCancel();
            alert("Connection failed.");
        }, 35000);
    },


    /*---------------------------------------------------------------------------------------------------------------------------------------------*/
    // Log file

    gotFS: function (fileSystem) {
        newFile = fileSystem.root.getDirectory("DSPS", {
            create: true,
            exclusive: false
        }, app.writerFile, app.fail);
        app.fileSystem = fileSystem;
    },

    readRootDirectory: function () {
        console.log("readRootDirectory");
        app.readEntries(app.fileSystem.root);
    },

    writerFile: function (newFile) {
        var fileName = 'DSPS_RX_';
        var date = new Date();
        fileName += date.getFullYear();
        fileName += parseInt(date.getMonth() + 1) > 9 ? parseInt(date.getMonth() + 1).toString() : '0' + parseInt(date.getMonth() + 1);
        fileName += date.getDate() > 9 ? date.getDate().toString() : '0' + date.getDate();
        fileName += date.getHours() > 9 ? date.getHours().toString() : '0' + date.getHours();
        fileName += date.getMinutes() > 9 ? date.getMinutes().toString() : '0' + date.getMinutes();
        fileName += date.getSeconds() > 9 ? date.getSeconds().toString() : '0' + date.getSeconds();
        fileName += '_' + app.deviceAddressToString(app.device.deviceAddress);
        fileName += '.txt';
        newFile.getFile(fileName, {
            create: true,
            exclusive: false
        }, app.gotFileEntry, app.fail);
    },

    deviceAddressToString: function (deviceAddress) {
        while (deviceAddress.indexOf(':') != -1) {
            deviceAddress = deviceAddress.replace(':', '')
        }
        return deviceAddress;
    },

    write: function (data) {
        if (app.writing) {
            if (!app.writeBuffer)
                app.writeBuffer = new BC.DataValue();
            app.writeBuffer.append(data);
            return;
        }
        app.writing = true;
        app.writer.seek(app.writer.length);
        app.writer.write(data.value);
    },

    gotFileEntry: function (fileEntry) {
        app.writerFileEntry = fileEntry;
        fileEntry.createWriter(app.gotFileWriter, app.fail);
    },

    gotFileWriter: function (writer) {
        writer.onwrite = function (evt) {
            console.log("write log success");
        };
        writer.onabort = function (evt) {
            console.log("write log abort");
        };
        writer.onerror = function (evt) {
            console.log("write log error");
        };
        writer.onwriteend = function (evt) {
            console.log("write log end");
            app.writing = false;
            if (app.writeBuffer) {
                var data = app.writeBuffer;
                app.writeBuffer = null;
                app.write(data);
            }
        };
        app.writeBuffer = null;
        app.writer = writer;
    },

    /*---------------------------------------------------------------------------------------------------------------------------------------------*/
    // Read file for file data streaming
    gotFiles: function (entries) {
        for (var i = 0; i < entries.length;) {
            if (entries[i].name.charAt(0) == ".") {
                entries.splice(i, 1);
            } else {
                i++;
            }
        }

        entries.sort(function (a, b) {
            if (a.isFile && !b.isFile)
                return 1;
            if (!a.isFile && b.isFile)
                return -1;
            var len = Math.min(a.name.length, b.name.length);
            a = a.name.toLowerCase();
            b = b.name.toLowerCase();
            for (var i = 0; i < len; i++) {
                if (a.charCodeAt(i) - b.charCodeAt(i) > 0) {
                    return 1;
                } else if (a.charCodeAt(i) - b.charCodeAt(i) < 0) {
                    return -1;
                } else {
                    continue;
                }
                return 0;
            }
        });

        var length = entries.length;
        var fileList = document.getElementById('fileList');
        fileList.innerHTML = "";
        if (length != 0) {
            entries[0].getParent(function (parent) {
                if (parent.name.length != 0 && parent.name != app.fileSystem.root.name) {
                    var parentItem = document.createElement('li');
                    parentItem.className = 'material-icons-before back';
                    parentItem.innerHTML = "Parent folder";
                    parentItem.onclick = function () {
                        parent.getParent(function (pare) {
                            app.readEntries(pare);
                        });
                    }
                    fileList.appendChild(parentItem);
                }

                for (var i = 0; i < length; i++) {
                    var fileEntry = entries[i];
                    var fileItem = document.createElement('li');
                    if (fileEntry.isFile) {
                        fileItem.className = 'material-icons-before file';
                        fileItem.innerHTML = fileEntry.name;;
                        (function (fileEntry) {
                            fileItem.onclick = function () {
                                app.fileDetails(fileEntry);
                            };
                        })(fileEntry);
                    } else {
                        fileItem.className = 'material-icons-before folder';
                        fileItem.innerHTML = fileEntry.name;;
                        (function (fileEntry) {
                            fileItem.onclick = function () {
                                app.readEntries(fileEntry);
                            };
                        })(fileEntry);
                    }
                    fileList.appendChild(fileItem);
                }
            });
        } else {
            if (app.fileEntry != null) {
                var parentItem = document.createElement('li');
                parentItem.className = 'material-icons-before back';
                parentItem.innerHTML = "Parent folder";
                parentItem.onclick = function () {
                    app.fileEntry.getParent(function (pare) {
                        app.readEntries(pare);
                    });
                }
                fileList.appendChild(parentItem);
            };
        }
    },

    readEntries: function (fileEntry) {
        app.fileEntry = fileEntry;
        var dirReader = fileEntry.createReader();
        dirReader.readEntries(app.gotFiles, function () {
            console.error("doDirectoryListing error callback")
        });
    },

    readFile: function (fileEntry) {
        fileEntry.file(function (file) {
            app.selectedFile = file;
            app.fileOffset = 0;
            app.sendNextFileChunk();
        }, function (e) {
            console.error("Failed to read file, code=" + e.code);
            alert("Failed to read file");
        });
    },

    sendNextFileChunk: function () {
        console.log("Sending file chunk at offset " + app.fileOffset);
        var chunk = app.selectedFile.slice(app.fileOffset, app.fileOffset + app.fileChunkSize)
        var reader = new FileReader();
        reader.onloadend = function (e) {
            var data = e.target.result;
            if (data.length > 0) {
                app.ftx_mode = 1;
                app.writeDataMTU("ASCII", data, 'file');
            } else {
                // No data. Set progress to 100%.
                app.sendProgress(1, 1);
            }
        }
        reader.onerror = function (e) {
            console.error("Failed to read file, code=" + e.code);
            alert("Failed to read file");
        }
        reader.readAsBinaryString(chunk);
    },

    fileDetails: function (fileEntry) {
        $('#fileTabList').css('display', 'none');
        $('#fileTabConfirm').css('display', 'block');
        app.selectedFile = fileEntry;
        $('#fileConfirmName').html(fileEntry.name);
        fileEntry.file(function(file) {
            if (file.size < 10240)
                $('#fileConfirmSize').html(file.size + (file.size != 1 ? " bytes" : " byte"))
            else
                $('#fileConfirmSize').html((Math.round(file.size * 100 / 1024) / 100) + " KB")
        });
    },

    fileSend: function () {
        $('#fileTabConfirm').css('display', 'none');
        $('#fileTabProgress').css('display', 'block');
        $('#fileProgressName').html(app.selectedFile.name);
        $('#fileProgressBar').val(0);
        app.ftx_interval = 0;
        if (cordova.platformId == "ios") {
            if (app.ftx_interval < 30)
                app.ftx_interval = 30;
        } else {
            if (app.ftx_interval < 5)
                app.ftx_interval = 5;
        }
        app.sendingFile = true;
        app.readFile(app.selectedFile);
    },

    sendProgress: function (curSize, allSize) {
        var curProgress = Math.floor(curSize / allSize * 100);
        $('#fileProgressBar').val(curProgress);
        $('#fileProgressPercentage').html(curProgress + "%");
        if (curProgress == 100) {
            $('#cancelButton').html("Done");
        }
    },

    cancelFileSend: function () {
        app.sendingFile = false;
        $('#fileTabConfirm').css('display', 'none');
        $('#fileTabProgress').css('display', 'none');
        $('#fileTabList').css('display', 'block');
        $('#fileProgressBar').val(0);
        $('#fileProgressPercentage').html(0 + "%");
        $('#cancelButton').html("Stop");
    },

    fail: function (error) {
        console.error("Failed to retrieve file:" + error.code);
    },

    /*---------------------------------------------------------------------------------------------------------------------------------------------*/
    // RX data receive
    defaultReceive: function (data) {
        // console.log("received: " + data.value.getASCIIString());
        console.log("logcat: received: " + data.value.getHexString());

        clearTimeout(app.writeTimer);
        var now = new Date().getTime();
        var prev = app.lastReceive;
        app.lastReceive = now;
        var scrollToEnd = function(elem) {
            elem.scrollTop = elem.scrollHeight;
            app.lastScroll = new Date().getTime();
        }
        var nIndex = $(".menu-section-list>li").index($(".selectedLi"));
        if (nIndex !== 0) {
            // Update RX window
            var hexChecked = document.getElementById("RxHex").checked;
            var showData = document.getElementById("showData");
            var func = hexChecked ? "getHexString" : "getASCIIString";
            var displaydata = showData.innerHTML + data.value[func]();
            if (displaydata.length > 1000) {
                showData.innerHTML = displaydata.slice(displaydata.length - 1000);
            } else {
                showData.innerHTML = displaydata;
            }
            // Scroll to end
            clearTimeout(app.scrollTimer);
            if (now - prev > 30 && now - app.lastScroll > 100) {
                scrollToEnd(showData);
            } else {
                if (now - app.lastScroll > 500)
                    scrollToEnd(showData);
                else
                    app.scrollTimer = setTimeout(scrollToEnd.bind(app, showData), 50);
            }
        } else {
            // update console window
            var hexChecked = document.getElementById("consoleRxHex").checked;
            var consoleModeData = document.getElementById("consoleModeData");
            var func = hexChecked ? "getHexString" : "getASCIIString";
            var displaydata = consoleModeData.innerHTML + data.value[func]();
            if (displaydata.length > 1000) {
                consoleModeData.innerHTML = displaydata.slice(displaydata.length - 1000);
            } else {
                consoleModeData.innerHTML = displaydata;
            }
            // Scroll to end
            clearTimeout(app.scrollTimer);
            if (now - prev > 30 && now - app.lastScroll > 100) {
                scrollToEnd(consoleModeData);
            } else {
                if (now - app.lastScroll > 500)
                    scrollToEnd(consoleModeData);
                else
                    app.scrollTimer = setTimeout(scrollToEnd.bind(app, consoleModeData), 50);
            }
        }

        if (!app.dataBuffer)
            app.dataBuffer = new BC.DataValue();
        app.dataBuffer.append(data.value);

        var writeLogFile = function() {
            app.write(app.dataBuffer);
            app.dataBuffer = null;
        };
        if (app.dataBuffer.value.byteLength > 10000)
            writeLogFile();
        else
            app.writeTimer = setTimeout(writeLogFile, 1000);
    },

    /*---------------------------------------------------------------------------------------------------------------------------------------------*/
    // RX flow control
    onStartReceive: function () {
        app.end_data_flag = 1;
        app.DialogSerialPortProfile.writeFlowControl("Hex", "01", function () {
            console.log("RX XON")
        }, function () {
            console.log("RX XON error")
        });
    },

    onStopReceive: function () {
        app.end_data_flag = 0;
        app.DialogSerialPortProfile.writeFlowControl("Hex", "02", function () {
            console.log("RX XOFF")
        }, function () {
            console.log("RX XOFF error")
        });
    },

    //clear rx window
    clearShowData: function () {
        document.getElementById("showData").innerHTML = "";
    },
    //clear tx window
    clearWriteValue: function () {
        document.getElementById("WriteValue").value = "";
    },
    //clear console window
    clearConsoleModeData: function () {
        document.getElementById("consoleModeData").innerHTML = "";
        document.getElementById("consoleMode").value = "";
    },

    /*---------------------------------------------------------------------------------------------------------------------------------------------*/
    // TX flow control
    flowControlCallback: function (data) {
        // case 1: send big data in one time
        // case 2: send cyclic
        // case 3: file tx streaming

        var XON = "01";
        var XOFF = "02";
        if (data.value.getHexString() == XOFF) {
            app.flowcontrol_data = 0;
            console.log("TX XOFF");
        } else if (data.value.getHexString() == XON) {
            app.flowcontrol_data = 1;
            console.log("TX XON");
        } else {
            alert("Received incorrect TX flow control code.");
        }
    },

    /*---------------------------------------------------------------------------------------------------------------------------------------------*/
    // TX send and stop button
    onSend: function () {
        var hexChecked = document.getElementById("TxHex").checked;
        var writeType = hexChecked ? "HEX" : "ASCII";

        var cycleSendChecked = document.getElementById("cycleSend").checked;
        var writeValue = document.getElementById("WriteValue").value;
        if (writeValue.length == 0)
            return;

        if (cycleSendChecked) {
            var interval = parseInt(document.getElementById("interval").value);
            app.writeDataToDeviceCycle(writeType, writeValue, interval);
        } else {
            app.writeDataToDevice(writeType, writeValue);
        }
    },

    enableTextfield: function () {
        if (document.getElementById("cycleSend").checked) {
            document.getElementById("interval").disabled = true;
        } else {
            document.getElementById("interval").disabled = false;
        }
    },

    onStop: function () {
        window.clearInterval(app.cycle_tx_timer);
        app.cycle_tx_stop = 1;
    },


    /*---------------------------------------------------------------------------------------------------------------------------------------------*/
    // One time TX data
    writeDataToDevice: function (writeType, writeValue) {
        window.clearInterval(app.cycle_tx_timer);
        app.writeDataMTU(writeType, writeValue);
    },

    /*---------------------------------------------------------------------------------------------------------------------------------------------*/
    // Cyclic TX data
    writeDataToDeviceCycle: function (writeType, writeValue, interval) {
        window.clearInterval(app.cycle_tx_timer);
        app.cycle_tx_ongoing = 0;
        app.cycle_tx_stop = 0;

        app.cycle_tx_timer = window.setInterval(function () {
            if (app.g_connected == 0) {
                return;
            }
            // send a box of data in the tx input window
            if (app.cycle_tx_ongoing) {
                app.cycle_tx_ongoing = 0;
                app.writeDataMTU(writeType, writeValue, 'cyclic');
            }
        }, interval);

        app.writeDataMTU(writeType, writeValue, 'cyclic');
    },

    /*---------------------------------------------------------------------------------------------------------------------------------------------*/
    // TX data send
    writeDataMTU: function (writeType, writeValue, mode) {
        var MTU = app.MTU[app.running_platform]
        var total_chunks = Math.ceil(writeValue.length / MTU);
        if (total_chunks == 0) {
            console.log("TX data empty");
            return;
        }
        if (total_chunks > 1)
            console.log("TX data exceed MTU, will be split into " + total_chunks + " chunks");
        var ftx_interval = app.ftx_interval;
        var cycle_tx_timer = app.cycle_tx_timer;
        (function (chunk, timer) {
            if (timer !== undefined && app.tx_timers.indexOf(timer) != -1)
                app.tx_timers.splice(app.tx_timers.indexOf(timer), 1);
            if (app.g_connected == 0) {
                if (mode == 'file')
                    app.ftx_mode = 0;
                return;
            }
            if (mode == 'cyclic' && app.cycle_tx_stop)
                return;
            if (mode == 'file' && !app.sendingFile)
                return;
            if (app.flowcontrol_data) {
                var currWriteValue = writeValue.slice(chunk * MTU, (chunk + 1) * MTU);
                console.log("TX data chunk " + (chunk + 1) + " of " + total_chunks);
                if (mode == 'file')
                    app.sendProgress(app.fileOffset + chunk * MTU + currWriteValue.length, app.selectedFile.size);
                console.log(currWriteValue);
                var nextCall = arguments.callee.bind(app, chunk + 1);
                app.DialogSerialPortProfile.write(writeType, currWriteValue,
                    function () {
                        if (++chunk == total_chunks) {
                            if (mode == 'file') {
                                app.fileOffset += app.fileChunkSize;
                                if (app.fileOffset < app.selectedFile.size)
                                    app.sendNextFileChunk();
                                else
                                    app.ftx_mode = 0;
                            }
                            if (mode == 'cyclic' && cycle_tx_timer == app.cycle_tx_timer)
                                app.cycle_tx_ongoing = 1;
                            return;
                        }
                        var id = setTimeout(function () {
                            nextCall(id);
                        }, mode == 'file' ? ftx_interval : 5);
                        app.tx_timers.push(id);
                    },
                    function () {
                        console.log("Fatal: GATT API write error!")
                    });
            } else {
                // Flow control is OFF, retry after a while
                var nextCall = arguments.callee.bind(app, chunk);
                var id = setTimeout(function () {
                    nextCall(id);
                }, 100);
                app.tx_timers.push(id);
            }
        })(0);
    },

    /*---------------------------------------------------------------------------------------------------------------------------------------------*/
    // Console mode
    consoleMode: function () {
        var consoleMode = $("#consoleMode");
        consoleMode.keydown(function (e) {
            app.preData = this.value;
            consoleMode.get(0).setSelectionRange(this.value.length, this.value.length);
        });
        consoleMode.keyup(function (e) {
            app.nextData = this.value;
            if (app.preData.length < app.nextData.length) {
                var hexChecked = document.getElementById("consoleTxHex").checked;
                if (hexChecked && app.nextData.length % 2)
                    return;
                var data = app.nextData.slice(!hexChecked ? -1 : -2);
                if (data) {
                    if (!app.flowcontrol_data) {
                        console.log("Flow control OFF. No data sent.");
                        return;
                    }
                    var writeType = hexChecked ? "HEX" : "ASCII";
                    console.log("Console TX: " + (hexChecked ? "0x" : "") + data);
                    app.DialogSerialPortProfile.write(writeType, data, function () {
                        console.log("write success")
                    }, function () {
                        console.log("write error")
                    });
                }
            }
        });
    },

};
