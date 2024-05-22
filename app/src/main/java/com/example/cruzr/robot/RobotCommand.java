package com.example.cruzr.robot;

import org.json.JSONArray;

public interface RobotCommand {
    void execute(JSONArray params);
}
