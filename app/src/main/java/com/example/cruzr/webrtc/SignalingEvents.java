// Adapted from the WebRTC Android AppRTC sample code in
// https://webrtc.googlesource.com/src/+/refs/heads/main/examples/androidapp/src/org/appspot/apprtc/AppRTCClient.java

package com.example.cruzr.webrtc;

import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

public interface SignalingEvents {
    void onRemoteDescription(final SessionDescription sdp);

    void onRemoteIceCandidate(final IceCandidate candidate);

    // other methods if necessary
}
