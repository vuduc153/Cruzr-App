package com.example.cruzr;

import android.app.Application;
import android.util.Log;

import com.ubtechinc.cruzr.sdk.ros.RosRobotApi;
import com.ubtechinc.cruzr.serverlibutil.interfaces.InitListener;

public class App extends Application {

    @Override
    public void onCreate() {
        super.onCreate();
        RosRobotApi.get().initializ(this, new InitListener() {
            @Override
            public void onInit() {
                Log.i("App", "ROS API initialization succeeded");
            }
        });
    }
}