package com.example.cruzr.robot;

import android.util.Log;

import com.ubtechinc.cruzr.sdk.face.CruzrFaceApi;
import com.ubtechinc.cruzr.sdk.ros.RosRobotApi;

import org.json.JSONArray;
import org.json.JSONException;

public class ExpressionCommand implements RobotCommand {
    public ExpressionCommand() {};

    @Override
    public void execute(JSONArray params) {
        if (params.length() == 0) {
            // Clear face
            return;
        } else {
            callExpressionAPI(params);
        }
    }

    private void callExpressionAPI(JSONArray params) {
        try {
            CruzrFaceApi.setCruzrFace(null, params.getString(0), false, false);
        } catch (IllegalArgumentException | JSONException exception) {
            Log.e("API", "Invalid arguments " + exception);
        }
    }
}
