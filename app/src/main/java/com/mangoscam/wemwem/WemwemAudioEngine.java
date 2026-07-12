package com.mangoscam.wemwem;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Handler;
import android.os.Looper;
import android.os.Process;

public class WemwemAudioEngine {
    public interface Listener {
        void onAudioFrame(float rms, float bass, float mids, float highs, float onset, float sustain, float silence);
        void onAudioState(boolean listening, String label);
    }

    private static final int SAMPLE_RATE = 22050;
    private static final int BLOCK_SIZE = 1024;

    private final Listener listener;
    private final Handler main = new Handler(Looper.getMainLooper());
    private volatile boolean running = false;
    private Thread thread;

    private float smoothRms = 0f;
    private float smoothBass = 0f;
    private float smoothMids = 0f;
    private float smoothHighs = 0f;
    private float previousEnergy = 0f;
    private float sustainMemory = 0f;

    public WemwemAudioEngine(Listener listener) {
        this.listener = listener;
    }

    public boolean isRunning() { return running; }

    public void start() {
        if (running) return;
        running = true;
        thread = new Thread(new Runnable() {
            @Override public void run() { captureLoop(); }
        }, "WEMWEM-AudioGrowth");
        thread.start();
    }

    public void stop() {
        running = false;
        if (thread != null) {
            try { thread.join(300); } catch (InterruptedException ignored) { }
            thread = null;
        }
        postState(false, "LISTEN PAUSED");
    }

    private void captureLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);
        int minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
        int bufferSize = Math.max(minBuffer, BLOCK_SIZE * 4);
        AudioRecord record = null;
        try {
            record = new AudioRecord(MediaRecorder.AudioSource.MIC, SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize);
            if (record.getState() != AudioRecord.STATE_INITIALIZED) {
                postState(false, "MIC INIT FAILED");
                running = false;
                return;
            }
            short[] buffer = new short[BLOCK_SIZE];
            record.startRecording();
            postState(true, "LISTENING");
            float low = 0f;
            float mid = 0f;
            while (running) {
                int read = record.read(buffer, 0, buffer.length);
                if (read <= 0) continue;

                float sum = 0f;
                float bassSum = 0f;
                float midSum = 0f;
                float highSum = 0f;
                float previous = 0f;
                for (int i = 0; i < read; i++) {
                    float x = buffer[i] / 32768f;
                    sum += x * x;

                    low += 0.040f * (x - low);
                    float withoutLow = x - low;
                    mid += 0.140f * (withoutLow - mid);
                    float high = withoutLow - mid;

                    bassSum += low * low;
                    midSum += mid * mid;
                    highSum += high * high;
                    previous = x;
                }

                float rms = (float)Math.sqrt(sum / Math.max(1, read));
                float bass = (float)Math.sqrt(bassSum / Math.max(1, read)) * 3.8f;
                float mids = (float)Math.sqrt(midSum / Math.max(1, read)) * 4.8f;
                float highs = (float)Math.sqrt(highSum / Math.max(1, read)) * 5.6f;

                rms = clamp(rms * 5.2f, 0f, 1f);
                bass = clamp(bass, 0f, 1f);
                mids = clamp(mids, 0f, 1f);
                highs = clamp(highs, 0f, 1f);

                smoothRms = smooth(smoothRms, rms, rms > smoothRms ? 0.35f : 0.10f);
                smoothBass = smooth(smoothBass, bass, 0.20f);
                smoothMids = smooth(smoothMids, mids, 0.22f);
                smoothHighs = smooth(smoothHighs, highs, 0.24f);

                float energy = smoothRms * 0.55f + smoothBass * 0.22f + smoothMids * 0.16f + smoothHighs * 0.07f;
                float onset = clamp((energy - previousEnergy) * 4.6f, 0f, 1f);
                previousEnergy = smooth(previousEnergy, energy, 0.18f);

                if (energy > 0.075f) sustainMemory = clamp(sustainMemory + 0.025f + energy * 0.015f, 0f, 1f);
                else sustainMemory = clamp(sustainMemory - 0.035f, 0f, 1f);
                float silence = clamp(1f - energy * 7.8f, 0f, 1f);

                postFrame(smoothRms, smoothBass, smoothMids, smoothHighs, onset, sustainMemory, silence);
            }
        } catch (SecurityException se) {
            postState(false, "MIC PERMISSION NEEDED");
        } catch (Throwable t) {
            postState(false, "MIC ERROR");
        } finally {
            if (record != null) {
                try { record.stop(); } catch (Throwable ignored) { }
                try { record.release(); } catch (Throwable ignored) { }
            }
        }
    }

    private void postFrame(final float rms, final float bass, final float mids, final float highs,
                           final float onset, final float sustain, final float silence) {
        main.post(new Runnable() {
            @Override public void run() {
                listener.onAudioFrame(rms, bass, mids, highs, onset, sustain, silence);
            }
        });
    }

    private void postState(final boolean listening, final String label) {
        main.post(new Runnable() {
            @Override public void run() { listener.onAudioState(listening, label); }
        });
    }

    private static float smooth(float oldValue, float newValue, float amount) {
        return oldValue + (newValue - oldValue) * amount;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }
}
