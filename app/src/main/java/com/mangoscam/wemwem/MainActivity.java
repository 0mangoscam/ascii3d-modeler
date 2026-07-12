package com.mangoscam.wemwem;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiInputPort;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;

import java.io.IOException;

public class MainActivity extends Activity implements SensorEventListener, WemwemAudioEngine.Listener {
    private WemwemView wemView;
    private SensorManager sensorManager;
    private Sensor accelerometer;
    private MidiManager midiManager;
    private MidiDevice connectedMidiDevice;
    private MidiOutputPort connectedOutputPort;
    private WemwemAudioEngine audioGrowthEngine;
    private long lastShakeTime = 0L;
    private float lastX;
    private float lastY;
    private float lastZ;
    private boolean hasLast = false;

    private final MidiReceiver midiReceiver = new MidiReceiver() {
        @Override
        public void onSend(byte[] data, int offset, int count, long timestamp) {
            parseMidi(data, offset, count);
        }
    };

    private final MidiManager.DeviceCallback midiCallback = new MidiManager.DeviceCallback() {
        @Override
        public void onDeviceAdded(MidiDeviceInfo device) {
            openMidiDevice(device);
        }

        @Override
        public void onDeviceRemoved(MidiDeviceInfo device) {
            if (wemView != null) wemView.setMidiConnected(false, "MIDI REMOVED");
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);

        wemView = new WemwemView(this);
        setContentView(wemView);
        enterImmersiveMode();

        audioGrowthEngine = new WemwemAudioEngine(this);
        ensureAudioGrowthPermission();

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        if (sensorManager != null) {
            accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        }

        midiManager = (MidiManager) getSystemService(MIDI_SERVICE);
        if (midiManager != null) {
            midiManager.registerDeviceCallback(midiCallback, new Handler(Looper.getMainLooper()));
            scanMidiDevices();
        } else {
            wemView.setMidiConnected(false, "MIDI NOT AVAILABLE");
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        enterImmersiveMode();
        if (sensorManager != null && accelerometer != null) {
            sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_GAME);
        }
        if (midiManager != null) scanMidiDevices();
        if (hasAudioPermission() && audioGrowthEngine != null && !audioGrowthEngine.isRunning()) audioGrowthEngine.start();
        wemView.resume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        if (audioGrowthEngine != null) audioGrowthEngine.stop();
        wemView.pause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (midiManager != null) midiManager.unregisterDeviceCallback(midiCallback);
        if (audioGrowthEngine != null) audioGrowthEngine.stop();
        closeMidi();
    }


    private void ensureAudioGrowthPermission() {
        if (hasAudioPermission()) {
            if (audioGrowthEngine != null) audioGrowthEngine.start();
            return;
        }
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 404);
        } else if (audioGrowthEngine != null) {
            audioGrowthEngine.start();
        }
    }

    private boolean hasAudioPermission() {
        return android.os.Build.VERSION.SDK_INT < 23
                || checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 404) {
            boolean granted = grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
            if (granted && audioGrowthEngine != null) {
                audioGrowthEngine.start();
            } else if (wemView != null) {
                wemView.setMicListening(false, "MIC PERMISSION NEEDED");
            }
        }
    }

    @Override
    public void onAudioFrame(float rms, float bass, float mids, float highs, float onset, float sustain, float silence) {
        if (wemView != null) wemView.onAudioFrame(rms, bass, mids, highs, onset, sustain, silence);
    }

    @Override
    public void onAudioState(boolean listening, String label) {
        if (wemView != null) wemView.setMicListening(listening, label);
    }

    private void enterImmersiveMode() {
        if (android.os.Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            | View.SYSTEM_UI_FLAG_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                            | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                            | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            );
        }
    }

    private void scanMidiDevices() {
        if (midiManager == null || connectedOutputPort != null) return;
        MidiDeviceInfo[] devices = midiManager.getDevices();
        for (MidiDeviceInfo info : devices) {
            if (hasOutputPort(info)) {
                openMidiDevice(info);
                return;
            }
        }
        if (wemView != null) wemView.setMidiConnected(false, "MIDI WAITING");
    }

    private boolean hasOutputPort(MidiDeviceInfo info) {
        for (MidiDeviceInfo.PortInfo port : info.getPorts()) {
            if (port.getType() == MidiDeviceInfo.PortInfo.TYPE_OUTPUT) return true;
        }
        return false;
    }

    private void openMidiDevice(final MidiDeviceInfo info) {
        if (midiManager == null || connectedOutputPort != null || !hasOutputPort(info)) return;
        midiManager.openDevice(info, new MidiManager.OnDeviceOpenedListener() {
            @Override
            public void onDeviceOpened(MidiDevice device) {
                if (device == null) {
                    if (wemView != null) wemView.setMidiConnected(false, "MIDI OPEN FAILED");
                    return;
                }
                connectedMidiDevice = device;
                for (MidiDeviceInfo.PortInfo port : info.getPorts()) {
                    if (port.getType() == MidiDeviceInfo.PortInfo.TYPE_OUTPUT) {
                        connectedOutputPort = device.openOutputPort(port.getPortNumber());
                        if (connectedOutputPort != null) {
                            connectedOutputPort.connect(midiReceiver);
                            String name = info.getProperties().getString(MidiDeviceInfo.PROPERTY_NAME);
                            if (name == null || name.trim().isEmpty()) name = "MIDI DEVICE";
                            if (wemView != null) wemView.setMidiConnected(true, name);
                        }
                        break;
                    }
                }
            }
        }, new Handler(Looper.getMainLooper()));
    }

    private void closeMidi() {
        try {
            if (connectedOutputPort != null) connectedOutputPort.close();
            if (connectedMidiDevice != null) connectedMidiDevice.close();
        } catch (IOException ignored) {
            // Let the garden decay naturally.
        }
        connectedOutputPort = null;
        connectedMidiDevice = null;
    }

    private void parseMidi(byte[] data, int offset, int count) {
        int end = offset + count;
        int i = offset;
        while (i < end) {
            int status = data[i] & 0xFF;
            int command = status & 0xF0;
            if (command == 0x90 && i + 2 < end) {
                int note = data[i + 1] & 0x7F;
                int velocity = data[i + 2] & 0x7F;
                if (velocity > 0 && wemView != null) wemView.onMidiNote(note, velocity);
                i += 3;
            } else if (command == 0x80 && i + 2 < end) {
                i += 3;
            } else if (command == 0xB0 && i + 2 < end) {
                int controller = data[i + 1] & 0x7F;
                int value = data[i + 2] & 0x7F;
                if (wemView != null) wemView.onMidiControl(controller, value);
                i += 3;
            } else if ((command == 0xC0 || command == 0xD0) && i + 1 < end) {
                int value = data[i + 1] & 0x7F;
                if (wemView != null) wemView.onMidiProgram(value);
                i += 2;
            } else {
                i++;
            }
        }
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_UP) {
                wemView.growNewBranch();
                return true;
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_VOLUME_DOWN) {
                wemView.collapseBranch();
                return true;
            }
            int note = computerKeyboardToMidi(event.getKeyCode());
            if (note >= 0) {
                wemView.onMidiNote(note, 104);
                return true;
            }
        }
        return super.dispatchKeyEvent(event);
    }

    private int computerKeyboardToMidi(int keyCode) {
        // Android hardware keyboard / Chromebook / desktop mapping.
        // White keys: A S D F G H J K, black-ish keys: W E T Y U.
        switch (keyCode) {
            case KeyEvent.KEYCODE_A: return 48;
            case KeyEvent.KEYCODE_W: return 49;
            case KeyEvent.KEYCODE_S: return 50;
            case KeyEvent.KEYCODE_E: return 51;
            case KeyEvent.KEYCODE_D: return 52;
            case KeyEvent.KEYCODE_F: return 53;
            case KeyEvent.KEYCODE_T: return 54;
            case KeyEvent.KEYCODE_G: return 55;
            case KeyEvent.KEYCODE_Y: return 56;
            case KeyEvent.KEYCODE_H: return 57;
            case KeyEvent.KEYCODE_U: return 58;
            case KeyEvent.KEYCODE_J: return 59;
            case KeyEvent.KEYCODE_K: return 60;
            default: return -1;
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() != Sensor.TYPE_ACCELEROMETER) return;

        float x = event.values[0];
        float y = event.values[1];
        float z = event.values[2];

        if (!hasLast) {
            lastX = x;
            lastY = y;
            lastZ = z;
            hasLast = true;
            return;
        }

        float dx = x - lastX;
        float dy = y - lastY;
        float dz = z - lastZ;
        lastX = x;
        lastY = y;
        lastZ = z;

        float force = dx * dx + dy * dy + dz * dz;
        long now = System.currentTimeMillis();
        if (force > 135f && now - lastShakeTime > 900L) {
            lastShakeTime = now;
            wemView.mutateFromShake();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        // No calibration required. The garden is allowed to breathe imperfectly.
    }
}
