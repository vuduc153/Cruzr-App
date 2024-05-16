package com.example.cruzr.robot;

import android.util.Log;

import com.example.cruzr.interfaces.RobotCommand;
import com.ubtechinc.cruzr.sdk.ros.RosRobotApi;

import org.json.JSONArray;
import org.json.JSONException;

public class StopCommand extends RobotCommand {

    public StopCommand() {}

    @Override
    public void execute(JSONArray params) {
        RosRobotApi.get().stopMove();
    }
}