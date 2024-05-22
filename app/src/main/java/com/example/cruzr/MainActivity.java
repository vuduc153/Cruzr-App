// Following the same class structure as in
// https://webrtc.googlesource.com/src/+/refs/heads/main/examples/androidapp/src/org/appspot/apprtc/CallActivity.java

package com.example.cruzr;

import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.example.cruzr.websockets.SSLContextHelper;
import com.example.cruzr.websockets.Server;
import com.example.cruzr.webrtc.SignalingEvents;
import com.google.common.util.concurrent.ListenableFuture;
import com.ubtechinc.cruzr.sdk.ros.RosRobotApi;

import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
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

import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;

import javax.net.ssl.SSLContext;

public class MainActivity extends AppCompatActivity implements SignalingEvents, PeerConnection.Observer {

    private static final String AUDIO_ECHO_CANCELLATION_CONSTRAINT = "googEchoCancellation";
    private static final String AUDIO_AUTO_GAIN_CONTROL_CONSTRAINT = "googAutoGainControl";
    private static final String AUDIO_HIGH_PASS_FILTER_CONSTRAINT = "googHighpassFilter";
    private static final String AUDIO_NOISE_SUPPRESSION_CONSTRAINT = "googNoiseSuppression";
    private boolean ledState = false;
    private TextView textView;
    private PreviewView previewView;
    private MediaPlayer mediaPlayer;
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

        eglBase = EglBase.create();

        textView = findViewById(R.id.dummyText);
        previewView = findViewById(R.id.viewFinder);
        remoteView = findViewById(R.id.remoteView);
        remoteView.init(eglBase.getEglBaseContext(), null);

        initMediaPlayer();
        startWebsocketServer();

        findViewById(R.id.showRosVersion).setOnClickListener(v -> textView.setText(RosRobotApi.get().getRosVersion()));

        findViewById(R.id.showRosIP).setOnClickListener(v -> textView.setText(RosRobotApi.get().getRosWifiIp()));

        findViewById(R.id.moveForward).setOnClickListener(v -> {
            int ret = RosRobotApi.get().moveToward(0.1f, 0, 0);
            if (ret == 0) {
                Toast.makeText(this, "Cannot perform move action", Toast.LENGTH_SHORT).show();
            }
            if (ret == 1) {
                Toast.makeText(this, "Forbidden by application layer", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.stopMove).setOnClickListener(v -> RosRobotApi.get().stopMove());

        findViewById(R.id.toggleLed).setOnClickListener(v -> {
            ledState = !ledState;
            int ret = RosRobotApi.get().ledSetOnOff(ledState);
            if (ret == 0) {
                Toast.makeText(this, "Cannot change LED state", Toast.LENGTH_SHORT).show();
            }
        });

        findViewById(R.id.showCamera).setOnClickListener(v -> {
            boolean hasPermissions = allPermissionsGranted();
            textView.setText(String.valueOf(hasPermissions));
            startCamera();
        });

        findViewById(R.id.playAudio).setOnClickListener(v -> playAudio());

        findViewById(R.id.dance).setOnClickListener(v -> dance());

        findViewById(R.id.startCall).setOnClickListener(v -> {
            if (server == null) {
                Toast.makeText(this, "Websocket server is not running", Toast.LENGTH_SHORT).show();
                return;
            }
            setupPeerConnection();
            startStream();
        });

        findViewById(R.id.endCall).setOnClickListener(v -> closePeerConnection());
    }

    @Override
    protected void onStop() {
        super.onStop();
        closePeerConnection();
        stopWebSocketServer();
        releaseMediaPlayer();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        initMediaPlayer();
        startWebsocketServer();
    }

    @Override
    protected void onDestroy() {
        RosRobotApi.get().destory();
        closePeerConnection();
        stopWebSocketServer();
        releaseMediaPlayer();
        super.onDestroy();
    }

    private boolean allPermissionsGranted() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this.getBaseContext(), permission) != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());
                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(MainActivity.this, cameraSelector, preview);
            } catch (CancellationException | ExecutionException | InterruptedException e) {
                Log.e("CAMERA", "Camera provider future failed " + e);
            } catch (IllegalStateException | IllegalArgumentException e) {
                Log.e("CAMERA", "Binding failed " + e);
            } finally {
                Toast.makeText(this, "Could not open camera", Toast.LENGTH_SHORT).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void playAudio() {
        if (mediaPlayer == null) initMediaPlayer();
        String audioUrl = "https://www2.cs.uic.edu/~i101/SoundFiles/CantinaBand3.wav";
        try {
            mediaPlayer.stop();
            mediaPlayer.reset();
            mediaPlayer.setDataSource(audioUrl);
            mediaPlayer.setOnPreparedListener(MediaPlayer::start);
            mediaPlayer.prepareAsync();
        } catch (IllegalArgumentException | IOException e) {
            Log.e("AUDIO", "Audio source not found " + e);
        } finally {
            Toast.makeText(this, "Could not open audio source", Toast.LENGTH_SHORT).show();
        }
    }

    private void dance() {
        int ret = RosRobotApi.get().run("cute");
        if (ret == 0) {
            Toast.makeText(this, "Cannot perform action", Toast.LENGTH_SHORT).show();
        }
    }

    private void initMediaPlayer() {
        mediaPlayer = new MediaPlayer();
        mediaPlayer.setAudioAttributes(new AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .build());
    }

    private void releaseMediaPlayer() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    private void startWebsocketServer() {
        try {
//            InetSocketAddress address = new InetSocketAddress("0.0.0.0", 8080); for testing on emulator
            InetSocketAddress address = new InetSocketAddress(8080); // for testing on Cruzr
            SSLContext sslContext = SSLContextHelper.createSSLContext(this);
            server = new Server(address, this);
            server.setWebSocketFactory(new DefaultSSLWebSocketServerFactory(sslContext));
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

        videoCapturer.startCapture(1280, 720, 30);
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

    private void closePeerConnection() {

        if (videoCapturer != null) {
            try {
                videoCapturer.stopCapture();
            } catch (InterruptedException e) {
                Log.e("MYRTC", "Interrupt signal encountered when stopping cameraCapturer " + e);
            }
            videoCapturer.dispose();
            videoCapturer = null;
        }

        // TODO: Debug
//        remoteView.clearImage();
//        remoteView.release();
//        remoteView.init(eglBase.getEglBaseContext(), null);

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

    private void startStream() {
        MediaStream stream = peerConnectionFactory.createLocalMediaStream("CRUZR");
        stream.addTrack(localVideoTrack);
//        stream.addTrack(localAudioTrack);
        peerConnection.addStream(stream);
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
        remoteVideoTrack = mediaStream.videoTracks.get(0);
        remoteVideoTrack.setEnabled(true);
//        remoteAudioTrack = mediaStream.audioTracks.get(0);
//        remoteAudioTrack.setEnabled(true);
        remoteVideoTrack.addSink(remoteView);
    }

    @Override
    public void onRemoveStream(MediaStream mediaStream) {
        Log.i("MYRTC", "onRemoveStream " + mediaStream);
        remoteVideoTrack.removeSink(remoteView);

        if (remoteVideoTrack != null) {
            remoteVideoTrack.dispose();
            remoteVideoTrack = null;
        }

        if (remoteAudioTrack != null) {
            remoteAudioTrack.dispose();
            remoteAudioTrack = null;
        }

        remoteView.clearImage();
        remoteView.release();
        remoteView.init(eglBase.getEglBaseContext(), null);
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
            new Handler(Looper.getMainLooper()).post(this::closePeerConnection);
        }
    }
}