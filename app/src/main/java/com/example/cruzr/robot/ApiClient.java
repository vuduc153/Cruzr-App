package com.example.cruzr.robot;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class ApiClient {

    private static ApiClient instance;

    private ApiClient() {}

    public static synchronized ApiClient getInstance() {
        if (instance == null) {
            instance = new ApiClient();
        }
        return instance;
    }

    public void callFromJSONString(String json) {
        JSONObject obj;
        try {
            obj = new JSONObject(json);
            String action = obj.getString("action");
            JSONArray parameters = obj.getJSONArray("params");
            Log.i("API", action);
            for (int i = 0; i < parameters.length(); i++) {
                Log.i("API", parameters.get(i).toString());
            }
        } catch (JSONException exception) {
            Log.e("SERVER", "Invalid message from Websocket client " + exception);
        }
    }
}
