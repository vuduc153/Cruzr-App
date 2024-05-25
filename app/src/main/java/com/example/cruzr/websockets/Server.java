package com.example.cruzr.websockets;

import android.util.Log;

import com.example.cruzr.robot.RobotCommandInvoker;
import com.example.cruzr.webrtc.SignalingEvents;

import org.java_websocket.WebSocket;
import org.java_websocket.handshake.ClientHandshake;
import org.java_websocket.server.WebSocketServer;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.net.InetSocketAddress;

public class Server extends WebSocketServer {

    private final RobotCommandInvoker api;
    private final SignalingEvents events;

    public Server(InetSocketAddress address, SignalingEvents events) {
        super(address);
        this.events = events;
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
        JSONObject obj;
        try {
            obj = new JSONObject(message);
            String type = obj.getString("type");
            if (type.equals("offer")) {
                String sdp = obj.getString("sdp");
                events.onRemoteDescription(new SessionDescription(SessionDescription.Type.OFFER, sdp));
            } else if(type.equals("candidate")) {
                JSONObject candidate = obj.getJSONObject("iceCandidate");
                events.onRemoteIceCandidate(new IceCandidate(candidate.getString("sdpMid"),
                        candidate.getInt("sdpMLineIndex"),
                        candidate.getString("candidate")));
            } else {
                JSONArray parameters = obj.getJSONArray("params");
                api.execute(type, parameters);
            }
        } catch (JSONException exception) {
            Log.e("SERVER", "Invalid message from Websocket client " + exception);
        }
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
        events.onServerStart();
    }
}
