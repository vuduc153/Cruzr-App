package com.example.cruzr.websockets;

import android.util.Log;

import com.example.cruzr.robot.RobotCommandInvoker;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;

import java.net.InetSocketAddress;

public class Server extends WebSocketServer {

    private RobotCommandInvoker api;

    public Server(InetSocketAddress address) {
        super(address);
        api = RobotCommandInvoker.getInstance();
    }

    @Override
    public void onOpen(WebSocket conn, ClientHandshake handshake) {
        conn.send("Welcome to the server!");
        Log.i("SERVER", "New connection: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onClose(WebSocket conn, int code, String reason, boolean remote) {
        Log.i("SERVER", "Closed connection: " + conn.getRemoteSocketAddress());
    }

    @Override
    public void onMessage(WebSocket conn, String message) {
        Log.i("SERVER", "Received message: " + message);
        api.callFromJSONString(message);
    }

    @Override
    public void onError(WebSocket conn, Exception ex) {
        Log.e("SERVER", "Connection error " + ex);
        if (conn != null) {
            Log.e("SERVER", "Port binding failed " + ex);
        }
    }

    @Override
    public void onStart() {
        Log.i("SERVER", "Server started successfully!");
    }
}
