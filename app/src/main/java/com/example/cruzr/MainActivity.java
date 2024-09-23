// Following the same class structure as in
// https://webrtc.googlesource.com/src/+/refs/heads/main/examples/androidapp/src/org/appspot/apprtc/CallActivity.java

package com.example.cruzr;

import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothHeadset;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.net.wifi.WifiManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.format.Formatter;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.text.HtmlCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.cruzr.audio.BluetoothReceiver;
import com.example.cruzr.websockets.Server;
import com.example.cruzr.webrtc.SignalingEvents;

import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera2Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.DataChannel;
import org.webrtc.DefaultVideoDecoderFactory;
import org.webrtc.DefaultVideoEncoderFactory;
import org.webrtc.EglBase;
import org.webrtc.IceCandidate;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.RtpReceiver;
import org.webrtc.SdpObserver;
import org.webrtc.SessionDescription;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.SurfaceViewRenderer;
import org.webrtc.VideoCapturer;
import org.webrtc.VideoDecoderFactory;
import org.webrtc.VideoEncoderFactory;
import org.webrtc.VideoSource;
import org.webrtc.VideoTrack;

import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MainActivity extends AppCompatActivity implements SignalingEvents, PeerConnection.Observer {

    private static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
    private static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";
    private static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";
    private static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";
    private AudioManager audioManager;
    private Server server;
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private VideoCapturer videoCapturer;
    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;
    private VideoSource videoSource;
    private AudioSource audioSource;
    private VideoTrack remoteVideoTrack;
    private AudioTrack remoteAudioTrack;
    private SurfaceViewRenderer remoteView;
    private EglBase eglBase;
    private TextView statusLabel;
    private TextView statusText;
    private TextView ipText;
    private ImageView imageOverlay;
    private Button connectBtn;
    private Button disconnectBtn;
    private int tapCount = 0;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        // Set audio output to speakerphone -- otherwise sound could be directed to earpiece speaker on mobile devices
        audioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
//        audioManager.setMode(AudioManager.MODE_NORMAL);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            List <AudioDeviceInfo> devices = audioManager.getAvailableCommunicationDevices();
            for (AudioDeviceInfo device: devices) {
                if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    audioManager.setCommunicationDevice(device);
                } // if bluetooth devices connected, set it as the audio source
                if (device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                    audioManager.setCommunicationDevice(device);
                }
            }
        }

        BluetoothReceiver bluetoothReceiver;
        bluetoothReceiver = new BluetoothReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED);
        registerReceiver(bluetoothReceiver, filter);

        eglBase = EglBase.create();

        remoteView = findViewById(R.id.remoteView);
        remoteView.init(eglBase.getEglBaseContext(), null);

        statusLabel = findViewById(R.id.statusLabel);
        statusText = findViewById(R.id.statusText);
        ipText = findViewById(R.id.ipText);
        imageOverlay = findViewById(R.id.imageOverlay);
        connectBtn = findViewById(R.id.startCall);
        disconnectBtn = findViewById(R.id.endCall);

        remoteView.setOnClickListener(v -> {
            tapCount++;
            if (tapCount == 3) {
                imageOverlay.setVisibility(View.VISIBLE);
                tapCount = 0;
            }
        });

        imageOverlay.setOnClickListener(v -> {
            if (imageOverlay.getVisibility() == View.VISIBLE) {
                // Hide the image overlay when it is tapped
                imageOverlay.setVisibility(View.GONE);
            }
        });

        connectBtn.setOnClickListener(v -> {
            startWebsocketServer(); // start the websocket server and open a PeerConnection onServerStart
            setTextOffline();
        });

        disconnectBtn.setOnClickListener(v -> {
            connectionCleanup();
            setTextOnline();
            showPlaceholder();
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        connectionCleanup();
    }

    @Override
    protected void onDestroy() {
        connectionCleanup();
        super.onDestroy();
    }

    @SuppressWarnings("deprecation")
    private String getAndroidIP() {
        WifiManager wifiManager = (WifiManager) this.getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        int ip = wifiManager.getConnectionInfo().getIpAddress();
        return Formatter.formatIpAddress(ip);
    }

    private void showPlaceholder() {
        statusLabel.setVisibility(View.VISIBLE);
        statusText.setVisibility(View.VISIBLE);
        ipText.setVisibility(View.VISIBLE);
    }

    private void hidePlaceholder() {
        statusLabel.setVisibility(View.GONE);
        statusText.setVisibility(View.GONE);
        ipText.setVisibility(View.GONE);
    }

    private void setTextOffline() {
        String status = String.format("<font color='%s'>online</font>", getResources().getColor(R.color.green));
        String ip = "Running on " + getAndroidIP();
        statusText.setText(HtmlCompat.fromHtml(status, HtmlCompat.FROM_HTML_MODE_LEGACY));
        ipText.setText(ip);
        connectBtn.setVisibility(View.GONE);
        disconnectBtn.setVisibility(View.VISIBLE);
    }

    private void setTextOnline() {
        String status = String.format("<font color='%s'>offline</font>", getResources().getColor(R.color.red));
        statusText.setText(HtmlCompat.fromHtml(status, HtmlCompat.FROM_HTML_MODE_LEGACY));
        ipText.setText("");
        disconnectBtn.setVisibility(View.GONE);
        connectBtn.setVisibility(View.VISIBLE);
    }

    private void startWebsocketServer() {
        try {
//            InetSocketAddress address = new InetSocketAddress("0.0.0.0", 8080); for testing on emulator
            InetSocketAddress address = new InetSocketAddress(8080); // for testing on Cruzr
            server = new Server(address, this);
            server.setReuseAddr(true);
            server.start();
        } catch (Exception exception) {
            stopWebSocketServer();
            Toast.makeText(this, "Cannot start Websocket server", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopWebSocketServer() {
        if (server != null) {
            try {
                server.stop();
                Log.i("SERVER", "WebSocket server stopped");
            } catch (InterruptedException e) {
                Log.e("SERVER", "Shutdown process interrupted");
            }
        }
    }

    private void setupPeerConnection() {
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        options.disableEncryption = false;
        options.disableNetworkMonitor = false;

        VideoEncoderFactory encoderFactory = new DefaultVideoEncoderFactory(eglBase.getEglBaseContext(), true, true);
        VideoDecoderFactory decoderFactory = new DefaultVideoDecoderFactory(eglBase.getEglBaseContext());

        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .setVideoDecoderFactory(decoderFactory)
                .setVideoEncoderFactory(encoderFactory)
                .createPeerConnectionFactory();

        videoCapturer = createCameraCapture(new Camera2Enumerator(this));
        if (videoCapturer == null) {
            Log.e("MYRTC", "Cannot find camera capture");
            return;
        }

        videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext()),
                this, videoSource.getCapturerObserver());
        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);
        localVideoTrack.setEnabled(true);

        MediaConstraints audioConstraints = new MediaConstraints();
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_ECHO_CANCELLATION_CONSTRAINT, "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT, "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_HIGH_PASS_FILTER_CONSTRAINT, "true"));
        audioConstraints.mandatory.add(
                new MediaConstraints.KeyValuePair(AUDIO_NOISE_SUPPRESSION_CONSTRAINT, "true"));

        audioSource = peerConnectionFactory.createAudioSource(audioConstraints);
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);
        localAudioTrack.setEnabled(true);

//        videoCapturer.startCapture(2560, 1440, 24); // for higher-end devices
        videoCapturer.startCapture(1280, 720, 24); // for Cruzr and lower-end devices
        peerConnection = createPeerConnection();
    }

    private PeerConnection createPeerConnection() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        return peerConnectionFactory.createPeerConnection(iceServers, this);
    }

    private VideoCapturer createCameraCapture(CameraEnumerator enumerator) {
        final String[] deviceNames = enumerator.getDeviceNames();
        for (String deviceName : deviceNames) {
            if (enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        for (String deviceName : deviceNames) {
            if (!enumerator.isFrontFacing(deviceName)) {
                VideoCapturer videoCapturer = enumerator.createCapturer(deviceName, null);
                if (videoCapturer != null) {
                    return videoCapturer;
                }
            }
        }
        return null;
    }

    private void startStream() {
        peerConnection.addTrack(localAudioTrack);
        peerConnection.addTrack(localVideoTrack);
    }

    private void closePeerConnection() {

        remoteView.clearImage();

        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                Log.e("MYRTC", "Interrupt signal encountered when stopping cameraCapturer " + e);
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }

        if (localVideoTrack != null) {
            localVideoTrack.dispose();
            localVideoTrack = null;
        }

        if (videoSource != null) {
            videoSource.dispose();
            videoSource = null;
        }

        if (localAudioTrack != null) {
            localAudioTrack.dispose();
            localAudioTrack = null;
        }

        if (audioSource != null) {
            audioSource.dispose();
            audioSource = null;
        }

        if (remoteVideoTrack != null) {
            remoteVideoTrack.dispose();
            remoteVideoTrack = null;
        }

        if (remoteAudioTrack != null) {
            remoteAudioTrack.dispose();
            remoteAudioTrack = null;
        }

        if (peerConnection != null) {
            peerConnection.close();
            peerConnection = null;
        }
    }

    private void connectionCleanup() {
        closePeerConnection();
        stopWebSocketServer();
    }

    public static final String[] REQUIRED_PERMISSIONS;

    static {
        ArrayList<String> permissions = new ArrayList<>(Arrays.asList(
                android.Manifest.permission.CAMERA,
                android.Manifest.permission.RECORD_AUDIO
        ));
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            permissions.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        REQUIRED_PERMISSIONS = permissions.toArray(new String[0]);
    }

    // --- Implementation of SignalingEvents ---------
    @Override
    public void onRemoteDescription(SessionDescription sdp) {
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
                                    server.broadcast(answer.toString());
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
        }, sdp);
    }

    @Override
    public void onRemoteIceCandidate(IceCandidate candidate) {
        peerConnection.addIceCandidate(candidate);
    }

    @Override
    public void onServerStart() {
        setupPeerConnection();
        startStream();
    }

    // --- Implementation of PeerConnection.Observer ---------
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
            server.broadcast(json.toString());
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
        if (!mediaStream.videoTracks.isEmpty()) {
            remoteVideoTrack = mediaStream.videoTracks.get(0);
            remoteVideoTrack.setEnabled(true);
            remoteVideoTrack.addSink(remoteView);
        }
        if (!mediaStream.audioTracks.isEmpty()) {
            remoteAudioTrack = mediaStream.audioTracks.get(0);
            remoteAudioTrack.setEnabled(true);
        }
    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) {
        Log.i("MYRTC", "onRemoveStream " + mediaStream);
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
        if (newState == PeerConnection.PeerConnectionState.DISCONNECTED) {
            new Handler(Looper.getMainLooper()).post(() -> {
                remoteView.clearImage();
                showPlaceholder();
            });
        }
        if (newState == PeerConnection.PeerConnectionState.CONNECTED) {
            new Handler(Looper.getMainLooper()).post(this::hidePlaceholder);
        }
    }
}