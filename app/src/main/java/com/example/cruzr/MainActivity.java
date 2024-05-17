package com.example.cruzr;

import android.content.pm.PackageManager;
import android.media.AudioAttributes;
import android.media.MediaPlayer;
import android.os.Build;
import android.os.Bundle;
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

import com.example.cruzr.webrtc.CandidateObserver;
import com.example.cruzr.webrtc.CustomPeerConnectionObserver;
import com.example.cruzr.webrtc.SDPOfferObserver;
import com.example.cruzr.websockets.SSLContextHelper;
import com.example.cruzr.websockets.Server;
import com.google.common.util.concurrent.ListenableFuture;
import com.ubtechinc.cruzr.sdk.ros.RosRobotApi;

import org.java_websocket.server.DefaultSSLWebSocketServerFactory;
import org.webrtc.AudioSource;
import org.webrtc.AudioTrack;
import org.webrtc.Camera1Enumerator;
import org.webrtc.CameraEnumerator;
import org.webrtc.EglBase;
import org.webrtc.MediaConstraints;
import org.webrtc.MediaStream;
import org.webrtc.PeerConnection;
import org.webrtc.PeerConnectionFactory;
import org.webrtc.SurfaceTextureHelper;
import org.webrtc.VideoCapturer;
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

public class MainActivity extends AppCompatActivity {

    private boolean ledState = false;
    private TextView textView;
    private PreviewView previewView;
    private MediaPlayer mediaPlayer;
    private Server server;
    private PeerConnectionFactory peerConnectionFactory;
    private PeerConnection peerConnection;
    private VideoTrack localVideoTrack;
    private AudioTrack localAudioTrack;


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

        textView = findViewById(R.id.dummyText);
        previewView = findViewById(R.id.viewFinder);

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

        findViewById(R.id.endCall).setOnClickListener(v -> {
            if (peerConnection != null) {
                peerConnection.close();
                peerConnection = null;
            }
        });
    }

    @Override
    protected void onStop() {
        super.onStop();
        releaseMediaPlayer();
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        initMediaPlayer();
    }

    @Override
    protected void onDestroy() {
        RosRobotApi.get().destory();
        peerConnection.close();
        stopWebSocketServer();
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
            server = new Server(address);
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
        EglBase eglBase = EglBase.create();
        PeerConnectionFactory.InitializationOptions initializationOptions =
                PeerConnectionFactory.InitializationOptions.builder(this)
                        .createInitializationOptions();
        PeerConnectionFactory.initialize(initializationOptions);

        PeerConnectionFactory.Options options = new PeerConnectionFactory.Options();
        peerConnectionFactory = PeerConnectionFactory.builder()
                .setOptions(options)
                .createPeerConnectionFactory();

        VideoCapturer videoCapturer = createCameraCapture(new Camera1Enumerator(false));
        if (videoCapturer == null) {
            Log.e("MYRTC", "Cannot find camera capture");
            return;
        }
        VideoSource videoSource = peerConnectionFactory.createVideoSource(videoCapturer.isScreencast());
        videoCapturer.initialize(SurfaceTextureHelper.create("CaptureThread", eglBase.getEglBaseContext()),
                this, videoSource.getCapturerObserver());
        localVideoTrack = peerConnectionFactory.createVideoTrack("100", videoSource);

        AudioSource audioSource = peerConnectionFactory.createAudioSource(new MediaConstraints());
        localAudioTrack = peerConnectionFactory.createAudioTrack("101", audioSource);

        videoCapturer.startCapture(1024, 720, 30);
        peerConnection = createPeerConnection();

        server.setOnOfferObserver(new SDPOfferObserver(peerConnection, server));
        server.setOnCandidateObserver(new CandidateObserver(peerConnection, server));
    }

    private PeerConnection createPeerConnection() {
        List<PeerConnection.IceServer> iceServers = new ArrayList<>();
        iceServers.add(PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer());
        return peerConnectionFactory.createPeerConnection(iceServers, new CustomPeerConnectionObserver(server));
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
        MediaStream stream = peerConnectionFactory.createLocalMediaStream("CRUZR");
        stream.addTrack(localVideoTrack);
        stream.addTrack(localAudioTrack);
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
}