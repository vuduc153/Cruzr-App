package com.example.cruzr.robot;

import android.util.Log;

import com.example.cruzr.interfaces.RobotCommand;
import com.ubtechinc.cruzr.sdk.ros.RosRobotApi;

import org.json.JSONArray;
import org.json.JSONException;

public class MoveCommand implements RobotCommand {

    public MoveCommand() {}

    @Override
    public void execute(JSONArray params) {
        try {
            RosRobotApi.get().moveToward(Float.parseFloat((String) params.get(0)),
                    Float.parseFloat((String) params.get(1)),
                    Float.parseFloat((String) params.get(2)));
        } catch (IllegalArgumentException | JSONException exception) {
            Log.e("API", "Invalid arguments " + exception);
        }
    }
}
