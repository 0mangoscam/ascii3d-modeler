package com.mangoscam.wemwem;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiInputPort;
import android.media.midi.MidiManager;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;

public class MainActivity extends Activity {
    private WemwemView view;
    private MidiManager midiManager;

    @Override
    protected void onCreate(Bundle bundle) {
        super.onCreate(bundle);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        view = new WemwemView(this);
        setContentView(view);
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, 420);
        }
        setupMidiDiscovery();
    }

    private void setupMidiDiscovery() {
        midiManager = (MidiManager) getSystemService(MIDI_SERVICE);
        if (midiManager != null) {
            view.setMidiStatus("MIDI · READY");
            MidiDeviceInfo[] devices = midiManager.getDevices();
            if (devices.length > 0) view.setMidiStatus("MIDI · " + devices.length + " DEVICE(S)");
            midiManager.registerDeviceCallback(new MidiManager.DeviceCallback() {
                @Override public void onDeviceAdded(MidiDeviceInfo info) { view.setMidiStatus("MIDI · CONNECTED"); }
                @Override public void onDeviceRemoved(MidiDeviceInfo info) { view.setMidiStatus("MIDI · REMOVED"); }
            }, null);
        } else {
            view.setMidiStatus("MIDI · UNAVAILABLE");
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 420 && grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            view.startListening();
        }
    }

    @Override protected void onResume() { super.onResume(); view.resume(); }
    @Override protected void onPause() { view.pause(); super.onPause(); }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) { view.manualBurst(1.0f); return true; }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) { view.prune(); return true; }
        int note = keyToNote(keyCode);
        if (note >= 0) { view.midiNote(note, 110); return true; }
        return super.onKeyDown(keyCode, event);
    }

    private int keyToNote(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_A: return 60;
            case KeyEvent.KEYCODE_W: return 61;
            case KeyEvent.KEYCODE_S: return 62;
            case KeyEvent.KEYCODE_E: return 63;
            case KeyEvent.KEYCODE_D: return 64;
            case KeyEvent.KEYCODE_F: return 65;
            case KeyEvent.KEYCODE_T: return 66;
            case KeyEvent.KEYCODE_G: return 67;
            case KeyEvent.KEYCODE_Y: return 68;
            case KeyEvent.KEYCODE_H: return 69;
            case KeyEvent.KEYCODE_U: return 70;
            case KeyEvent.KEYCODE_J: return 71;
            case KeyEvent.KEYCODE_K: return 72;
        }
        return -1;
    }
}
