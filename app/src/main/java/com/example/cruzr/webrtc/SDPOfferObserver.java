package com.example.cruzr.webrtc;

import android.util.Log;

import com.example.cruzr.interfaces.SignalingObserver;
import com.example.cruzr.websockets.Server;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.MediaConstraints;
import org.webrtc.PeerConnection;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;

public class SDPOfferObserver implements SignalingObserver {

    private final PeerConnection peerConnection;
    private final Server wsServer;

    public SDPOfferObserver(PeerConnection peerConnection, Server wsServer) {
        this.peerConnection = peerConnection;
        this.wsServer = wsServer;
    }

    @Override
    public void handle(String message) {
        try {
            String sdp = new JSONObject(message).getString("sdp");
            peerConnection.setRemoteDescription(new SdpObserver() {
                @Override
                public void onSetSuccess() {
                    peerConnection.createAnswer(new SdpObserver() {
                        @Override
                        public void onCreateSuccess(SessionDescription sessionDescription) {
                            peerConnection.setLocalDescription(new SdpObserver() {
                                @Override
                                public void onSetSuccess() {
                                    JSONObject answer = new JSONObject();
                                    try {
                                        answer.put("type", "answer");
                                        answer.put("sdp", sessionDescription.description);
                                        wsServer.broadcast(answer.toString());
                                    } catch (JSONException e) {
                                        Log.e("MYRTC","Invalid answer format " + e);
                                    }
                                }

                                @Override
                                public void onSetFailure(String s) {
                                    Log.e("MYRTC", "Cannot set local SDP description " + s);
                                }

                                @Override
                                public void onCreateSuccess(SessionDescription sessionDescription) {}

                                @Override
                                public void onCreateFailure(String s) {}

                            }, sessionDescription);
                        }

                        @Override
                        public void onCreateFailure(String s) {
                            Log.e("MYRTC", "Cannot create SDP answer " + s);
                        }

                        @Override
                        public void onSetSuccess() {}

                        @Override
                        public void onSetFailure(String s) {}
                    }, new MediaConstraints());
                }

                @Override
                public void onSetFailure(String s) {
                    Log.e("MYRTC", "Cannot set remote SDP description " + s);
                }

                @Override
                public void onCreateSuccess(SessionDescription sessionDescription) {}

                @Override
                public void onCreateFailure(String s) {}
            }, new SessionDescription(SessionDescription.Type.OFFER, sdp));
        } catch (JSONException e) {
            Log.e("MYRTC", "Invalid SDP offer message " + e);
        }
    }
}
