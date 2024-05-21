package com.example.cruzr.webrtc;

import android.util.Log;

import com.example.cruzr.websockets.Server;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioTrack;
import org.webrtc.DataChannel;
import org.webrtc.IceCandidate;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.RtpReceiver;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoTrack;

import java.util.Arrays;

public class CustomPeerConnectionObserver implements PeerConnection.Observer {

    protected Server wsServer;
    private final SurfaceViewRenderer remoteView;

    public CustomPeerConnectionObserver(Server wsServer, SurfaceViewRenderer remoteView) {
        this.wsServer = wsServer;
        this.remoteView = remoteView;
    }

    @Override
    public void onSignalingChange(PeerConnection.SignalingState signalingState) {
        Log.i("MYRTC", "onSignalingChange " + signalingState);
    }

    @Override
    public void onIceConnectionChange(PeerConnection.IceConnectionState iceConnectionState) {
        Log.i("MYRTC", "onIceConnectionChange " + iceConnectionState);
    }

    @Override
    public void onIceConnectionReceivingChange(boolean b) {
        Log.i("MYRTC", "onIceConnectionReceivingChange " + b);
    }

    @Override
    public void onIceGatheringChange(PeerConnection.IceGatheringState iceGatheringState) {
        Log.i("MYRTC", "onIceGatheringChange " + iceGatheringState);
    }

    @Override
    public void onIceCandidate(IceCandidate iceCandidate) {
        Log.i("MYRTC", "onIceCandidate " + iceCandidate);
        try {
            JSONObject json = new JSONObject();
            json.put("type", "candidate");
            json.put("candidate", iceCandidate.sdp);
            json.put("sdpMid", iceCandidate.sdpMid);
            json.put("sdpMLineIndex", iceCandidate.sdpMLineIndex);
            wsServer.broadcast(json.toString());
        } catch (JSONException e) {
            // json.put throws JSONException when iceCandidate is a non-finite number or "iceCandidate" already existed
            // neither of which can happen in this case
            Log.e("MYRTC", "Invalid ICE candidate " + iceCandidate);
        }
    }

    @Override
    public void onIceCandidatesRemoved(IceCandidate[] iceCandidates) {
        Log.i("MYRTC", "onIceCandidatesRemoved " + Arrays.toString(iceCandidates));
    }

    @Override
    public void onAddStream(MediaStream mediaStream) {
        Log.i("MYRTC", "onAddStream " + mediaStream);
        VideoTrack remoteVideoTrack = mediaStream.videoTracks.get(0);
        remoteVideoTrack.setEnabled(true);
//        AudioTrack remoteAudioTrack = mediaStream.audioTracks.get(0);
//        remoteAudioTrack.setEnabled(true);
        remoteVideoTrack.addSink(remoteView);
    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) {
        Log.i("MYRTC", "onRemoveStream " + mediaStream);
        VideoTrack remoteVideoTrack = mediaStream.videoTracks.get(0);
        remoteVideoTrack.removeSink(remoteView);
    }

    @Override
    public void onDataChannel(DataChannel dataChannel) {
        Log.i("MYRTC", "onDataChannel " + dataChannel);
    }

    @Override
    public void onRenegotiationNeeded() {
        Log.i("MYRTC", "onRenegotiationNeeded");
    }

    @Override
    public void onAddTrack(RtpReceiver rtpReceiver, MediaStream[] mediaStreams) {
        Log.i("MYRTC", "onAddTrack " + rtpReceiver + Arrays.toString(mediaStreams));
    }

    @Override
    public void onConnectionChange(final PeerConnection.PeerConnectionState newState) {
        Log.i("MYRTC", "onConnectionChange " + newState);
        if (newState == PeerConnection.PeerConnectionState.CLOSED) {
//            events.onConnected();
        }
    }
}
