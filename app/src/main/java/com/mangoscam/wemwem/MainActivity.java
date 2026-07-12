package com.mangoscam.wemwem;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.media.midi.MidiDevice;
import android.media.midi.MidiDeviceInfo;
import android.media.midi.MidiManager;
import android.media.midi.MidiOutputPort;
import android.media.midi.MidiReceiver;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import java.util.ArrayList;

public class MainActivity extends Activity {
    private WemwemView view;
    private MidiManager midiManager;
    private final ArrayList<MidiDevice> openMidiDevices = new ArrayList<>();
    private final ArrayList<MidiOutputPort> openMidiPorts = new ArrayList<>();

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
            MidiDeviceInfo[] devices = midiManager.getDevices();
            view.setMidiStatus(devices.length > 0 ? "MIDI · " + devices.length + " DEVICE(S)" : "MIDI · READY");
            for (MidiDeviceInfo info : devices) openMidiDevice(info);
            midiManager.registerDeviceCallback(new MidiManager.DeviceCallback() {
                @Override public void onDeviceAdded(MidiDeviceInfo info) {
                    view.setMidiStatus("MIDI · CONNECTED");
                    openMidiDevice(info);
                }
                @Override public void onDeviceRemoved(MidiDeviceInfo info) {
                    view.setMidiStatus("MIDI · REMOVED");
                }
            }, null);
        } else {
            view.setMidiStatus("MIDI · UNAVAILABLE");
        }
    }

    private void openMidiDevice(MidiDeviceInfo info) {
        if (midiManager == null) return;
        midiManager.openDevice(info, device -> {
            if (device == null) return;
            openMidiDevices.add(device);
            int outputCount = info.getOutputPortCount();
            if (outputCount <= 0) {
                view.setMidiStatus("MIDI · DEVICE HAS NO OUT");
                return;
            }
            MidiOutputPort outputPort = device.openOutputPort(0);
            if (outputPort == null) return;
            openMidiPorts.add(outputPort);
            outputPort.connect(new MidiReceiver() {
                    @Override
                    public void onSend(byte[] msg, int offset, int count, long timestamp) {
                        parseMidi(msg, offset, count);
                    }
                });
            view.setMidiStatus("MIDI · PLAYING FIELD");
        }, null);
    }

    private void parseMidi(byte[] msg, int offset, int count) {
        int end = offset + count;
        for (int i = offset; i < end; ) {
            int status = msg[i] & 0xff;
            if (status < 0x80 || i + 2 >= end) break;
            int type = status & 0xf0;
            int d1 = msg[i + 1] & 0x7f;
            int d2 = msg[i + 2] & 0x7f;
            if (type == 0x90) {
                if (d2 > 0) view.midiNote(d1, d2); else view.midiNoteOff(d1);
                i += 3;
            } else if (type == 0x80) {
                view.midiNoteOff(d1);
                i += 3;
            } else if (type == 0xB0) {
                view.midiControl(d1, d2);
                i += 3;
            } else if (type == 0xE0) {
                int value14 = (d2 << 7) | d1;
                view.midiPitchBend((value14 - 8192) / 8192f);
                i += 3;
            } else {
                i += 3;
            }
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
    @Override protected void onDestroy() {
        for (MidiOutputPort p : openMidiPorts) try { p.close(); } catch (Exception ignored) {}
        for (MidiDevice d : openMidiDevices) try { d.close(); } catch (Exception ignored) {}
        super.onDestroy();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP) { view.manualBurst(1.0f); return true; }
        if (keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) { view.prune(); return true; }
        int note = keyToNote(keyCode);
        if (note >= 0) { view.midiNote(note, 110); return true; }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        int note = keyToNote(keyCode);
        if (note >= 0) { view.midiNoteOff(note); return true; }
        return super.onKeyUp(keyCode, event);
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
