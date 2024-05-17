package com.example.cruzr.webrtc;

import android.util.Log;

import com.example.cruzr.interfaces.SignalingObserver;
import com.example.cruzr.websockets.Server;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.PeerConnection;

public class CandidateObserver implements SignalingObserver {

    private PeerConnection peerConnection;
    private final Server wsServer;

    public CandidateObserver(PeerConnection peerConnection, Server wsServer) {
        this.peerConnection = peerConnection;
        this.wsServer = wsServer;
    };

    @Override
    public void handle(String message) {
        try {
            JSONObject json = new JSONObject(message);
            JSONObject candidate = json.getJSONObject("iceCandidate");
            peerConnection.addIceCandidate(new IceCandidate(candidate.getString("sdpMid"),
                    candidate.getInt("sdpMLineIndex"),
                    candidate.getString("candidate")));
        } catch (JSONException e) {
            Log.e("MYRTC", "Invalid ICE candidate format " + e);
        }
    }
}
