package com.example.cruzr;

import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

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

import com.google.common.util.concurrent.ListenableFuture;
import com.ubtechinc.cruzr.sdk.ros.RosRobotApi;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.CancellationException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MainActivity extends AppCompatActivity {

    private boolean ledState = false;
    private TextView textView;
    private PreviewView previewView;
    private ExecutorService cameraExecutor;

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

        findViewById(R.id.showRosVersion).setOnClickListener(v -> textView.setText(RosRobotApi.get().getRosVersion()));

        findViewById(R.id.showRosIP).setOnClickListener(v -> textView.setText(RosRobotApi.get().getRosWifiIp()));

        findViewById(R.id.moveForward).setOnClickListener(v -> {
            int ret = RosRobotApi.get().moveToward(0.1f, 0, 0);
            Log.i("MOVE", Integer.toString(ret));
        });

        findViewById(R.id.stopMove).setOnClickListener(v -> RosRobotApi.get().stopMove());

        findViewById(R.id.toggleLed).setOnClickListener(v -> {
            ledState = !ledState;
            RosRobotApi.get().ledSetOnOff(ledState);
        });

        findViewById(R.id.showCamera).setOnClickListener(v -> {
            boolean hasPermissions = allPermissionsGranted();
            textView.setText(String.valueOf(hasPermissions));
            startCamera();
        });

        cameraExecutor = Executors.newSingleThreadExecutor();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
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
            }
        }, ContextCompat.getMainExecutor(this));
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