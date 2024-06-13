package com.example.cruzr.robot;

import android.util.Log;

import com.ubtechinc.cruzr.sdk.ros.RosRobotApi;

import org.json.JSONArray;
import org.json.JSONException;

public class NavigationCommand implements RobotCommand {

    public NavigationCommand() {}

    @Override
    public void execute(JSONArray params) {
        if (params.length() == 0) {
            RosRobotApi.get().cancelNavigate();
        } else {
            callNavigationAPI(params);
        }
    }

    private void callNavigationAPI(JSONArray params) {
        try {
            Object param1 = params.get(0);
            Object param2 = params.get(1);
            Object param3 = params.get(2);

            float p1 = ((Number) param1).floatValue();
            float p2 = ((Number) param2).floatValue();
            float p3 = ((Number) param3).floatValue();

            RosRobotApi.get().navigateToByPresetedSpeed(p1, p2, p3);
        } catch (IllegalArgumentException | JSONException | ClassCastException exception) {
            Log.e("API", "Invalid arguments for navigation API call " + exception);
        }
    }
}
