package com.example.cruzr.audio;

import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothProfile;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.util.List;

public class BluetoothReceiver extends BroadcastReceiver {

    private static final long RETRY_INTERVAL_MS = 1000;
    private static final int MAX_RETRY_COUNT = 5;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent != null) {
            String action = intent.getAction();
            if (BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED.equals(action)) {
                int state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE,
                        BluetoothHeadset.STATE_DISCONNECTED);
                switch (state) {
                    case BluetoothProfile.STATE_CONNECTED:
                        Log.d("BluetoothReceiver", "Bluetooth headphones connected");
                        useHeadsetAudioSource(context);
                        break;
                    case BluetoothProfile.STATE_DISCONNECTED:
                        Log.d("BluetoothReceiver", "Bluetooth headphones disconnected");
                        useBuiltinAudioSource(context);
                        break;
                }
            }
        }
    }

    private void useHeadsetAudioSource(Context context) {

        final Handler handler = new Handler(Looper.getMainLooper());
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);

        // Retry mechanism
        handler.postDelayed(new Runnable() {
            private int retryCount = 0;

            @Override
            public void run() {
                boolean deviceReady = false;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    List<AudioDeviceInfo> devices = audioManager.getAvailableCommunicationDevices();
                    for (AudioDeviceInfo device: devices) {
                        if (device.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) {
                            Log.i("BluetoothReceiver", "Using bluetooth headset");
                            deviceReady = true;
                            audioManager.setCommunicationDevice(device);
                        }
                    }
                } else {
                    deviceReady = true;
                    audioManager.setMicrophoneMute(false);
                    audioManager.startBluetoothSco();
                    audioManager.setBluetoothScoOn(true);
                }

                if (deviceReady) return;

                if (retryCount < MAX_RETRY_COUNT) {
                    Log.d("BluetoothReceiver", "Failed");
                    retryCount++;
                    handler.postDelayed(this, RETRY_INTERVAL_MS);
                } else {
                    Log.d("BluetoothReceiver", "Failed to confirm headset readiness after retries");
                }
            }
        }, RETRY_INTERVAL_MS);
    }

    private void useBuiltinAudioSource(Context context) {
        AudioManager audioManager = (AudioManager) context.getSystemService(Context.AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            List<AudioDeviceInfo> devices = audioManager.getAvailableCommunicationDevices();
            for (AudioDeviceInfo device: devices) {
                if (device.getType() == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER) {
                    Log.i("BluetoothReceiver", "Using built-in audio source");
                    audioManager.setCommunicationDevice(device);
                }
            }
        } else {
            audioManager.setMicrophoneMute(false);
            audioManager.stopBluetoothSco();
            audioManager.setBluetoothScoOn(false);
        }
    }
}