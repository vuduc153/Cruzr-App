package com.example.cruzr.robot;

import android.util.Log;

import com.ubtechinc.cruzr.sdk.ros.RosRobotApi;

import org.json.JSONArray;
import org.json.JSONException;

public class PresetCommand implements RobotCommand {
    public PresetCommand() {};

    @Override
    public void execute(JSONArray params) {
        if (params.length() == 0) {
            RosRobotApi.get().stopRun();
        } else {
            callPresetRunAPI(params);
        }
    }

    private void callPresetRunAPI(JSONArray params) {
        try {
            RosRobotApi.get().run(params.getString(0));
        } catch (IllegalArgumentException | JSONException exception) {
            Log.e("API", "Invalid arguments " + exception);
        }
    }
}
