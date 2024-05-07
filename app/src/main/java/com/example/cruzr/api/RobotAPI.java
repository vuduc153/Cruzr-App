package com.example.cruzr.api;


import android.content.Context;
import android.net.wifi.WifiManager;
import android.text.format.Formatter;

import com.ubtechinc.cruzr.sdk.ros.RosRobotApi;


public class RobotAPI {
    // Single instance of the class
    private static RobotAPI instance;

    // Private constructor to prevent instantiation from other classes
    private RobotAPI() {
        // Initialize any necessary resources here
    }

    // Public method to get the single instance of the class
    public static synchronized RobotAPI getInstance() {
        if (instance == null) {
            instance = new RobotAPI();
        }
        return instance;
    }

    @SuppressWarnings("deprecation")
    public String getAndroidIP(Context context) {
        WifiManager wifiManager = (WifiManager) context.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int ip = wifiManager.getConnectionInfo().getIpAddress();
        return Formatter.formatIpAddress(ip);
    }

    public String getRosIP() {
        return RosRobotApi.get().getRosWifiIp();
    }

    public String getRosVersion() {
        return RosRobotApi.get().getRosVersion();
    }
}