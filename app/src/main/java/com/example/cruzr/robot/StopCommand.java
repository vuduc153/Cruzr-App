package com.example.cruzr.robot;

import com.ubtechinc.cruzr.sdk.ros.RosRobotApi;

import org.json.JSONArray;

public class StopCommand implements RobotCommand {

    public StopCommand() {}

    @Override
    public void execute(JSONArray params) {
        RosRobotApi.get().stopMove();
    }
}