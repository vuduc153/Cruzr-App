package com.example.cruzr.interfaces;

import org.json.JSONArray;

public abstract class RobotCommand {

    public RobotCommand() {}

    public abstract void execute(JSONArray params);
}
