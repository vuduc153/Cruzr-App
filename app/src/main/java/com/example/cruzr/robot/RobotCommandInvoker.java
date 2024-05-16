package com.example.cruzr.robot;

import android.util.Log;

import com.example.cruzr.interfaces.RobotCommand;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.HashMap;
import java.util.Map;

public class RobotCommandInvoker {

    private static RobotCommandInvoker instance;
    private final Map<String, RobotCommand> commandRegistry  = new HashMap<String, RobotCommand>() {{
        put("move", new MoveCommand());
        put("stop", new StopCommand());
    }};

    private RobotCommandInvoker() {}

    public static synchronized RobotCommandInvoker getInstance() {
        if (instance == null) {
            instance = new RobotCommandInvoker();
        }
        return instance;
    }

    public void callFromJSONString(String json) {
        JSONObject obj;
        try {
            obj = new JSONObject(json);
            String action = obj.getString("action");
            JSONArray parameters = obj.getJSONArray("params");
            if (commandRegistry.containsKey(action)) {
                commandRegistry.get(action).execute(parameters);
            } else {
                throw new JSONException("Invalid keyword for action");
            }
        } catch (JSONException exception) {
            Log.e("SERVER", "Invalid message from Websocket client " + exception);
        }
    }
}
