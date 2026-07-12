package com.mangoscam.sculpturegarden;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.os.Build;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class SculptureGardenView extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint softPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.DITHER_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.SUBPIXEL_TEXT_FLAG);
    private final GestureDetector gestures;
    private final ScaleGestureDetector scaleGesture;
    private final RectF evolveRect = new RectF();
    private final RectF museumRect = new RectF();
    private final RectF relaxRect = new RectF();
    private final RectF dnaRect = new RectF();
    private final RectF asciiRect = new RectF();
    private final RectF listenRect = new RectF();

    private final Random particleRandom = new Random(8844L);
    private final List<SoundParticle> soundParticles = new ArrayList<>();
    private long particleSequence = 0L;
    private long lastParticleCondenseAt = 0L;
    private float condensationCharge = 0f;

    private Sculpture current;
    private final List<Sculpture> garden = new ArrayList<>();
    private final List<Sculpture> descendants = new ArrayList<>();
    private boolean choosingDescendant = false;
    private boolean museumMode = false;
    private boolean relaxMode = false;
    private boolean artDnaMode = true;
    private boolean asciiVolumeMode = true;
    private boolean midiConnected = false;
    private boolean micListening = false;
    private boolean running = true;
    private boolean splitDuringPinch = false;

    private float orbitX = -0.18f;
    private float orbitY = 0.42f;
    private float zoom = 1.0f;
    private float targetZoom = 1.0f;
    private float lastDragDistance = 0f;
    private float soundEnergy = 0f;
    private float targetSoundEnergy = 0f;
    private float midiPitch = 0.5f;
    private float midiControlGlow = 0f;
    private float micRms = 0f;
    private float micBass = 0f;
    private float micMids = 0f;
    private float micHighs = 0f;
    private float micOnset = 0f;
    private float micSustain = 0f;
    private float micSilence = 1f;
    private long lastAudioGrowthAt = 0L;
    private long lastAudioSproutAt = 0L;
    private String midiName = "MIDI WAITING";
    private String micName = "MIC WAITING";
    private long birth = System.currentTimeMillis();

    private String pulseText = "MANGOSCAM DNA";
    private long pulseAt = System.currentTimeMillis();

    public SculptureGardenView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        if (Build.VERSION.SDK_INT >= 11) setLayerType(View.LAYER_TYPE_SOFTWARE, null);

        textPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
        textPaint.setLetterSpacing(0.08f);

        current = Sculpture.seed();
        garden.add(current);

        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override public boolean onDown(MotionEvent e) {
                lastDragDistance = 0f;
                return true;
            }
            @Override public void onLongPress(MotionEvent e) {
                if (!choosingDescendant) increaseMass();
            }
            @Override public boolean onDoubleTap(MotionEvent e) {
                relaxMode = !relaxMode;
                pulse(relaxMode ? "SILENT POSTER" : "GARDEN");
                invalidate();
                return true;
            }
            @Override public boolean onSingleTapConfirmed(MotionEvent e) {
                handleTap(e.getX(), e.getY());
                return true;
            }
            @Override public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (!choosingDescendant && Math.abs(velocityX) + Math.abs(velocityY) > 900f) {
                    growNewBranch();
                    return true;
                }
                return false;
            }
            @Override public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (!choosingDescendant) {
                    orbitY -= distanceX * 0.0047f;
                    orbitX -= distanceY * 0.0028f;
                    orbitX = clamp(orbitX, -0.85f, 0.85f);
                    lastDragDistance += Math.abs(distanceX) + Math.abs(distanceY);
                    current.dna.twist += distanceX * 0.00065f;
                    if (lastDragDistance > 155f) {
                        current.addGesture(0.65f);
                        lastDragDistance = 0f;
                    }
                    invalidate();
                    return true;
                }
                return false;
            }
        });

        scaleGesture = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override public boolean onScaleBegin(ScaleGestureDetector detector) {
                splitDuringPinch = false;
                return true;
            }
            @Override public boolean onScale(ScaleGestureDetector detector) {
                targetZoom *= detector.getScaleFactor();
                targetZoom = clamp(targetZoom, 0.55f, 2.4f);
                if (!choosingDescendant && !splitDuringPinch && Math.abs(detector.getScaleFactor() - 1f) > 0.035f) {
                    splitDuringPinch = true;
                    splitStructure();
                }
                invalidate();
                return true;
            }
        });
    }

    public void resume() {
        running = true;
        birth = System.currentTimeMillis();
        postInvalidateOnAnimation();
    }

    public void pause() { running = false; }

    public void setMidiConnected(boolean connected, String name) {
        midiConnected = connected;
        midiName = name == null ? (connected ? "MIDI CONNECTED" : "MIDI WAITING") : name;
        pulse(connected ? "MIDI LINKED · " + midiName : midiName);
        invalidate();
    }


    public void setMicListening(boolean listening, String label) {
        micListening = listening;
        micName = label == null ? (listening ? "LISTENING" : "MIC WAITING") : label;
        pulse(micName);
        invalidate();
    }

    public void onAudioFrame(float rms, float bass, float mids, float highs, float onset, float sustain, float silence) {
        if (choosingDescendant) return;
        micRms = smoothValue(micRms, rms, 0.24f);
        micBass = smoothValue(micBass, bass, 0.20f);
        micMids = smoothValue(micMids, mids, 0.20f);
        micHighs = smoothValue(micHighs, highs, 0.22f);
        micOnset = Math.max(onset, micOnset * 0.74f);
        micSustain = smoothValue(micSustain, sustain, 0.12f);
        micSilence = smoothValue(micSilence, silence, 0.18f);

        float livingEnergy = clamp(micRms * 0.52f + micBass * 0.26f + micMids * 0.15f + micHighs * 0.07f, 0f, 1f);
        targetSoundEnergy = Math.max(targetSoundEnergy, livingEnergy * 1.15f);
        spawnParticlesFromAudio(micRms, micBass, micMids, micHighs, micOnset, micSustain, micSilence);
        updateParticleField(micBass, micMids, micHighs, micOnset, micSustain, micSilence);

        long now = System.currentTimeMillis();
        if (micOnset > 0.34f && livingEnergy > 0.09f && now - lastAudioSproutAt > 330L) {
            lastAudioSproutAt = now;
            lastAudioGrowthAt = now;
            condenseParticleCloud(micBass, micMids, micHighs, micOnset, micSustain, false);
            pulse(audioEventName(micBass, micMids, micHighs, micOnset));
        } else if (livingEnergy > 0.055f && now - lastAudioGrowthAt > 190L) {
            lastAudioGrowthAt = now;
            current.cultivateFromSound(micBass, micMids, micHighs, micSustain);
        } else if (micSilence > 0.72f && now - lastAudioGrowthAt > 620L) {
            lastAudioGrowthAt = now;
            current.restFromSilence(micSilence);
        }
        invalidate();
    }

    private String audioEventName(float bass, float mids, float highs, float onset) {
        if (bass > mids && bass > highs) return "BASS MASS SPROUT";
        if (highs > bass && highs > mids) return "HIGH ROOT ACCENT";
        if (onset > 0.62f) return "AUDIO BLOOM";
        return "SOUND GROWTH";
    }

    private static float smoothValue(float oldValue, float newValue, float amount) {
        return oldValue + (newValue - oldValue) * amount;
    }

    public void onMidiNote(int note, int velocity) {
        if (choosingDescendant) return;
        float v = clamp(velocity / 127f, 0f, 1f);
        midiPitch = clamp((note - 24f) / 72f, 0f, 1f);
        targetSoundEnergy = Math.max(targetSoundEnergy, 0.35f + v * 0.85f);
        soundEnergy = Math.max(soundEnergy, targetSoundEnergy);

        current.dna.rhythm = clamp(0.28f + ((note % 12) / 11f) * 0.62f, 0.12f, 0.96f);
        current.dna.thickness = clamp(current.dna.thickness + (v - 0.45f) * 0.055f, 0.22f, 0.98f);
        current.dna.loopFrequency = clamp(current.dna.loopFrequency + ((note % 5) - 2) * 0.012f, 0.12f, 0.96f);
        current.dna.twist += (midiPitch - 0.5f) * 0.18f;
        if (v > 0.78f || note % 7 == 0) current.addGesture(v);
        else current.rebuild();
        pulse("NOTE " + note + " · VEL " + velocity);
        invalidate();
    }

    public void onMidiControl(int controller, int value) {
        if (choosingDescendant) return;
        float v = clamp(value / 127f, 0f, 1f);
        midiControlGlow = 1f;
        switch (controller) {
            case 1:  current.dna.curvature = clamp(0.18f + v * 0.78f, 0.18f, 0.96f); break;
            case 2:  current.dna.thickness = clamp(0.20f + v * 0.78f, 0.20f, 0.98f); break;
            case 7:  current.dna.growth = clamp(0.16f + v * 0.84f, 0.16f, 1.0f); break;
            case 10: current.dna.compositionSpread = clamp(0.16f + v * 0.72f, 0.16f, 0.88f); break;
            case 11: current.dna.accentLines = clamp(0.04f + v * 0.72f, 0.04f, 0.76f); break;
            case 71: current.dna.chaos = clamp(0.08f + v * 0.52f, 0.08f, 0.60f); break;
            case 74: current.dna.loopFrequency = clamp(0.10f + v * 0.86f, 0.10f, 0.96f); break;
            case 91: current.dna.surfaceNoise = clamp(0.04f + v * 0.56f, 0.04f, 0.60f); break;
            default:
                current.dna.twist += (v - 0.5f) * 0.08f;
                break;
        }
        current.rebuild();
        pulse("CC " + controller + " · " + value);
        invalidate();
    }

    public void onMidiProgram(int value) {
        current.dna.paletteIndex = Math.floorMod(value, Palette.COUNT);
        current.rebuild();
        pulse("PALETTE · " + Palette.get(current.dna.paletteIndex).name);
        invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        scaleGesture.onTouchEvent(event);
        gestures.onTouchEvent(event);
        return true;
    }

    private void handleTap(float x, float y) {
        if (choosingDescendant) {
            int chosen = hitDescendant(x, y);
            if (chosen >= 0) {
                Sculpture survivor = descendants.get(chosen);
                survivor.parentId = current.id;
                current.childrenIds.add(survivor.id);
                current = survivor;
                garden.add(current);
                descendants.clear();
                choosingDescendant = false;
                pulse("GENERATION " + current.generation);
                invalidate();
                return;
            }
            choosingDescendant = false;
            descendants.clear();
            invalidate();
            return;
        }
        if (evolveRect.contains(x, y)) { evolve(); return; }
        if (museumRect.contains(x, y)) {
            museumMode = !museumMode;
            pulse(museumMode ? "WHITE MUSEUM" : Palette.get(current.dna.paletteIndex).name);
            invalidate();
            return;
        }
        if (relaxRect.contains(x, y)) {
            relaxMode = !relaxMode;
            pulse(relaxMode ? "SILENCE" : "GARDEN");
            invalidate();
            return;
        }
        if (listenRect.contains(x, y)) {
            pulse(micListening ? "AUDIO GROWTH ACTIVE" : micName);
            invalidate();
            return;
        }
        if (asciiRect.contains(x, y)) {
            asciiVolumeMode = !asciiVolumeMode;
            pulse(asciiVolumeMode ? "ASCII VOLUME" : "CLAY VOLUME");
            invalidate();
            return;
        }
        if (dnaRect.contains(x, y)) {
            artDnaMode = !artDnaMode;
            pulse(artDnaMode ? "MANGOSCAM DNA" : "QUIET DNA");
            current.dna.applyMangoscamBias(artDnaMode ? 1f : 0.55f);
            current.rebuild();
            invalidate();
        }
    }

    private int hitDescendant(float x, float y) {
        float w = getWidth();
        float h = getHeight();
        float rowY = h * 0.54f;
        for (int i = 0; i < descendants.size(); i++) {
            float cx = w * (0.14f + i * 0.18f);
            RectF r = new RectF(cx - w * 0.082f, rowY - h * 0.15f, cx + w * 0.082f, rowY + h * 0.16f);
            if (r.contains(x, y)) return i;
        }
        return -1;
    }

    public void growNewBranch() {
        if (choosingDescendant) return;
        current.addGesture(1.0f);
        current.dna.accentLines = clamp(current.dna.accentLines + 0.035f, 0.06f, 0.74f);
        pulse("NEW GESTURE");
        invalidate();
    }

    public void collapseBranch() {
        if (choosingDescendant) return;
        current.simplify();
        pulse("SIMPLIFIED");
        invalidate();
    }

    public void mutateFromShake() {
        if (choosingDescendant) return;
        current = current.createChild(3, 0.16f);
        garden.add(current);
        pulse("SOFT MUTATION");
        invalidate();
    }

    private void increaseMass() {
        current.dna.thickness = clamp(current.dna.thickness + 0.065f, 0.18f, 1.0f);
        current.dna.softness = clamp(current.dna.softness + 0.025f, 0.40f, 1.0f);
        current.rebuild();
        pulse("MASS INCREASED");
        invalidate();
    }

    private void splitStructure() {
        current.dna.loopFrequency = clamp(current.dna.loopFrequency + 0.055f, 0.12f, 0.94f);
        current.dna.compositionSpread = clamp(current.dna.compositionSpread + 0.025f, 0.18f, 0.92f);
        current.addGesture(0.75f);
        pulse("STRUCTURE SPLIT");
    }

    private void pulse(String text) {
        pulseText = text;
        pulseAt = System.currentTimeMillis();
    }

    private void evolve() {
        descendants.clear();
        for (int i = 0; i < 5; i++) descendants.add(current.createChild(i + 1, 0.12f));
        choosingDescendant = true;
        pulse("CHOOSE THE SURVIVOR");
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float t = (System.currentTimeMillis() - birth) / 1000f;
        zoom += (targetZoom - zoom) * 0.08f;
        soundEnergy += (targetSoundEnergy - soundEnergy) * 0.18f;
        targetSoundEnergy *= 0.90f;
        midiControlGlow *= 0.92f;
        micOnset *= 0.88f;
        if (relaxMode) {
            orbitY += 0.0009f;
            orbitX += Math.sin(t * 0.17f) * 0.00045f;
        }

        drawAtmosphere(canvas, w, h, t);
        if (choosingDescendant) {
            drawDescendants(canvas, w, h, t);
        } else {
            drawGardenGhosts(canvas, w, h, t);
            drawParticleField(canvas, w, h, Math.min(w, h) * 0.36f * zoom, t, false);
            drawSculpture(canvas, current, w * 0.50f, h * 0.51f, Math.min(w, h) * 0.36f * zoom, 1.0f, orbitX, orbitY, t, false);
            drawParticleField(canvas, w, h, Math.min(w, h) * 0.36f * zoom, t, true);
            drawSeedGlow(canvas, w, h, t);
        }
        if (!relaxMode) drawInterface(canvas, w, h, t); else drawRelaxWhisper(canvas, w, h, t);
        if (running) postInvalidateOnAnimation();
    }

    private void drawAtmosphere(Canvas canvas, float w, float h, float t) {
        Palette p = museumMode ? Palette.museum() : Palette.get(current.dna.paletteIndex);
        canvas.drawColor(p.background);

        paint.setShader(new RadialGradient(w * 0.48f, h * 0.36f, Math.max(w, h) * 0.74f,
                Color.argb(museumMode ? 82 : 48, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        drawCanvasGrain(canvas, w, h, p, t);

        if (!museumMode) {
            paint.setShader(new LinearGradient(0, 0, 0, h, Color.argb(28, 0, 0, 0), Color.argb(7, 255, 255, 255), Shader.TileMode.CLAMP));
            canvas.drawRect(0, 0, w, h, paint);
            paint.setShader(null);
        }
    }

    private void drawCanvasGrain(Canvas canvas, float w, float h, Palette p, float t) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1f);
        int dark = Color.argb(museumMode ? 14 : 22, 0, 0, 0);
        int light = Color.argb(museumMode ? 18 : 24, 255, 255, 255);
        float step = Math.max(4f, Math.min(w, h) * 0.009f);
        for (float y = 0; y < h; y += step) {
            paint.setColor(((int)(y / step) % 2 == 0) ? dark : light);
            canvas.drawLine(0, y, w, y + (float)Math.sin(y * 0.021f) * 1.8f, paint);
        }
        for (float x = 0; x < w; x += step * 1.35f) {
            paint.setColor(((int)(x / step) % 2 == 0) ? Color.argb(10, 0, 0, 0) : Color.argb(10, 255, 255, 255));
            canvas.drawLine(x, 0, x + (float)Math.sin(x * 0.019f + t) * 1.4f, h, paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawSeedGlow(Canvas canvas, float w, float h, float t) {
        if (current.strokes.size() > 5) return;
        float cx = w * 0.5f;
        float cy = h * 0.51f;
        float r = Math.min(w, h) * (0.022f + 0.003f * (float)Math.sin(t * 1.6f));
        paint.setShader(new RadialGradient(cx, cy, r * 4f,
                museumMode ? Color.argb(150, 20, 20, 20) : Color.argb(210, 255, 245, 210), Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r * 4f, paint);
        paint.setShader(null);
        paint.setColor(museumMode ? Color.rgb(30, 30, 27) : Color.rgb(246, 241, 219));
        canvas.drawCircle(cx, cy, r, paint);
    }

    private void drawGardenGhosts(Canvas canvas, float w, float h, float t) {
        int count = Math.min(garden.size() - 1, 6);
        for (int i = 0; i < count; i++) {
            Sculpture s = garden.get(Math.max(0, garden.size() - 2 - i));
            float lane = (i % 2 == 0) ? -1f : 1f;
            float x = w * 0.5f + lane * w * (0.25f + i * 0.020f);
            float y = h * (0.63f - i * 0.020f);
            float scale = Math.min(w, h) * 0.085f / (1f + i * 0.28f);
            drawSculpture(canvas, s, x, y, scale, 0.10f, orbitX * 0.25f, orbitY + i * 0.55f, t, true);
        }
    }

    private void drawDescendants(Canvas canvas, float w, float h, float t) {
        drawTextCentered(canvas, "FIVE DESCENDANTS", w * 0.5f, h * 0.15f, 14f, 0.82f);
        drawTextCentered(canvas, "safe · minimal · expressive · compact · experimental", w * 0.5f, h * 0.185f, 9f, 0.55f);
        float rowY = h * 0.54f;
        for (int i = 0; i < descendants.size(); i++) {
            float cx = w * (0.14f + i * 0.18f);
            drawSculpture(canvas, descendants.get(i), cx, rowY, Math.min(w, h) * 0.112f, 0.95f, orbitX * 0.35f, orbitY + i * 0.5f + t * 0.055f, t, false);
            drawTextCentered(canvas, descendants.get(i).mutationName, cx, rowY + h * 0.19f, 8.5f, 0.50f);
        }
    }


    private void spawnParticlesFromAudio(float rms, float bass, float mids, float highs, float onset, float sustain, float silence) {
        float energy = clamp(rms * 0.42f + bass * 0.25f + mids * 0.20f + highs * 0.13f, 0f, 1f);
        if (energy < 0.018f && onset < 0.05f) return;
        int birthCount = 1 + (int)(energy * 7f) + (int)(onset * 12f);
        birthCount = Math.min(18, birthCount);
        for (int i = 0; i < birthCount; i++) {
            float pick = particleRandom.nextFloat() * Math.max(0.001f, bass + mids + highs + onset * 0.6f);
            int type;
            if ((pick -= bass) <= 0f) type = SoundParticle.BASS;
            else if ((pick -= mids) <= 0f) type = SoundParticle.MID;
            else if ((pick -= highs) <= 0f) type = SoundParticle.HIGH;
            else type = SoundParticle.TRANSIENT;
            spawnSoundParticle(type, energy, bass, mids, highs, onset, sustain);
        }
        while (soundParticles.size() > 520) soundParticles.remove(0);
    }

    private void spawnSoundParticle(int type, float energy, float bass, float mids, float highs, float onset, float sustain) {
        SoundParticle p = new SoundParticle();
        p.id = particleSequence++;
        p.type = type;
        p.age = 0f;
        p.life = 2.2f + particleRandom.nextFloat() * 5.4f + sustain * 3.8f;
        p.energy = clamp(0.24f + energy * 0.76f + onset * 0.42f, 0f, 1.35f);
        p.mass = type == SoundParticle.BASS ? 1.8f : type == SoundParticle.MID ? 1.15f : type == SoundParticle.HIGH ? 0.62f : 1.0f;
        p.affinity = particleRandom.nextFloat();

        float archetypeAngle = current == null ? 0f : current.dna.rhythm * 6.283f + current.dna.twist;
        float burst = type == SoundParticle.TRANSIENT ? 0.28f + onset * 0.52f : 0.08f + energy * 0.22f;
        float a = archetypeAngle + particleRandom.nextFloat() * 6.283f;
        float r = burst * (0.28f + particleRandom.nextFloat());
        p.x = (float)Math.cos(a) * r;
        p.y = (float)Math.sin(a) * r * 0.72f;
        p.z = (particleRandom.nextFloat() - 0.5f) * (0.28f + energy * 0.80f);

        float speed = 0.0028f + energy * 0.018f + onset * 0.030f;
        if (type == SoundParticle.BASS) speed *= 0.58f;
        if (type == SoundParticle.HIGH) speed *= 1.55f;
        p.vx = (float)Math.cos(a + 1.57f) * speed + (particleRandom.nextFloat() - 0.5f) * speed;
        p.vy = (float)Math.sin(a + 1.57f) * speed * 0.80f + (particleRandom.nextFloat() - 0.5f) * speed;
        p.vz = (particleRandom.nextFloat() - 0.5f) * speed * 0.85f;
        soundParticles.add(p);
    }

    private void updateParticleField(float bass, float mids, float highs, float onset, float sustain, float silence) {
        if (soundParticles.isEmpty()) return;
        float cohesion = 0.003f + micMids * 0.008f + micSustain * 0.006f;
        float swirl = 0.002f + micHighs * 0.018f + midiPitch * 0.006f;
        float spread = current == null ? 0.55f : current.dna.compositionSpread;
        float targetRadius = 0.20f + spread * 0.70f;

        for (int i = soundParticles.size() - 1; i >= 0; i--) {
            SoundParticle p = soundParticles.get(i);
            p.age += 0.033f;
            float dist = (float)Math.sqrt(p.x * p.x + p.y * p.y + p.z * p.z) + 0.001f;
            float ringForce = (targetRadius - dist) * 0.0024f;
            p.vx += (p.x / dist) * ringForce;
            p.vy += (p.y / dist) * ringForce;
            p.vz += (p.z / dist) * ringForce * 0.60f;

            p.vx += -p.y * swirl * (p.type == SoundParticle.HIGH ? 1.8f : 1.0f);
            p.vy +=  p.x * swirl * (p.type == SoundParticle.BASS ? 0.45f : 1.0f);

            float attract = cohesion * p.mass;
            p.vx += -p.x * attract;
            p.vy += -p.y * attract * 0.86f;
            p.vz += -p.z * attract * 0.68f;

            p.vx += (particleRandom.nextFloat() - 0.5f) * 0.0020f * (0.3f + highs + onset);
            p.vy += (particleRandom.nextFloat() - 0.5f) * 0.0020f * (0.3f + highs + onset);
            p.vz += (particleRandom.nextFloat() - 0.5f) * 0.0016f * (0.3f + highs);

            float damping = p.type == SoundParticle.BASS ? 0.925f : p.type == SoundParticle.HIGH ? 0.955f : 0.942f;
            p.vx *= damping; p.vy *= damping; p.vz *= damping;
            p.x += p.vx; p.y += p.vy; p.z += p.vz;
            p.energy *= 0.988f - silence * 0.010f;
            if (p.age > p.life || p.energy < 0.035f) soundParticles.remove(i);
        }

        condensationCharge = clamp(condensationCharge + bass * 0.016f + mids * 0.020f + highs * 0.009f + onset * 0.080f - silence * 0.018f, 0f, 1.4f);
        long now = System.currentTimeMillis();
        if ((condensationCharge > 0.62f || (onset > 0.54f && soundParticles.size() > 44)) && now - lastParticleCondenseAt > 520L) {
            condenseParticleCloud(bass, mids, highs, onset, sustain, true);
            lastParticleCondenseAt = now;
            condensationCharge *= 0.32f;
        }
    }

    private void condenseParticleCloud(float bass, float mids, float highs, float onset, float sustain, boolean fromCloud) {
        if (soundParticles.size() < 8 && fromCloud) return;
        if (fromCloud) {
            float cx = 0f, cy = 0f, cz = 0f, total = 0.001f;
            for (SoundParticle p : soundParticles) {
                float w = p.energy * p.mass;
                cx += p.x * w; cy += p.y * w; cz += p.z * w; total += w;
            }
            cx /= total; cy /= total; cz /= total;
            for (int i = soundParticles.size() - 1; i >= 0; i--) {
                SoundParticle p = soundParticles.get(i);
                float dx = p.x - cx, dy = p.y - cy, dz = p.z - cz;
                float d = dx * dx + dy * dy + dz * dz;
                if (d < 0.34f || particleRandom.nextFloat() < 0.18f) soundParticles.remove(i);
            }
        }
        current.growFromSound(bass, mids, highs, Math.max(onset, condensationCharge), sustain);
        if (highs > 0.24f && particleRandom.nextFloat() < 0.45f) current.cultivateFromSound(bass * 0.7f, mids, highs, sustain);
        pulse(fromCloud ? "PARTICLE CONDENSATION" : "SOUND MATTER BLOOM");
    }

    private void drawParticleField(Canvas canvas, float w, float h, float scale, float time, boolean foreground) {
        if (soundParticles.isEmpty()) return;
        Palette palette = museumMode ? Palette.museum() : Palette.get(current.dna.paletteIndex);
        List<ParticleRender> renders = new ArrayList<>();
        float rotX = orbitX * 0.62f;
        float rotY = orbitY + current.dna.twist * 0.42f + time * 0.012f;
        for (SoundParticle p : soundParticles) {
            Vec3 rp = rotate(new Vec3(p.x, p.y, p.z), rotX, rotY);
            boolean front = rp.z > 0.02f;
            if (foreground != front) continue;
            float perspective = 1.0f / (1.95f + rp.z * 0.34f);
            ParticleRender pr = new ParticleRender();
            pr.x = w * 0.5f + rp.x * scale * perspective;
            pr.y = h * 0.51f - rp.y * scale * perspective;
            pr.z = rp.z;
            pr.size = Math.max(7f, scale * 0.018f * perspective * (0.8f + p.energy * 1.8f) * p.mass);
            pr.alpha = clamp((1f - p.age / Math.max(0.001f, p.life)) * (0.35f + p.energy), 0f, 1f);
            pr.type = p.type;
            pr.energy = p.energy;
            renders.add(pr);
        }
        Collections.sort(renders, new Comparator<ParticleRender>() {
            @Override public int compare(ParticleRender a, ParticleRender b) { return Float.compare(a.z, b.z); }
        });
        final char[] ramp = {'.', ':', '-', '=', '+', '*', '#', '%', '@'};
        textPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        for (ParticleRender pr : renders) {
            int role = pr.type == SoundParticle.BASS ? 0 : pr.type == SoundParticle.MID ? 1 : pr.type == SoundParticle.HIGH ? 3 : 2;
            int base = palette.color(role);
            float light = clamp(0.42f + pr.z * 0.22f + pr.energy * 0.35f, 0f, 1f);
            int color = blend(blend(base, Color.BLACK, 0.46f), blend(base, Color.WHITE, 0.52f), light);
            int index = Math.max(0, Math.min(ramp.length - 1, (int)(light * (ramp.length - 1))));
            String glyph = String.valueOf(ramp[index]);
            textPaint.setTextSize(pr.size);
            if (foreground) {
                textPaint.setColor(Color.argb((int)(pr.alpha * 54f), 0, 0, 0));
                canvas.drawText(glyph, pr.x + pr.size * 0.12f, pr.y + pr.size * 0.18f, textPaint);
            }
            textPaint.setColor(withAlpha(color, (int)(pr.alpha * (foreground ? 214 : 120))));
            canvas.drawText(glyph, pr.x, pr.y, textPaint);
        }
        textPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
    }

    private void drawSculpture(Canvas canvas, Sculpture sculpture, float cx, float cy, float scale, float alpha, float rx, float ry, float time, boolean ghost) {
        List<RenderSegment> segments = new ArrayList<>();
        float rotX = rx * 0.62f;
        float rotY = ry + sculpture.dna.twist * 0.42f + time * (ghost ? 0.008f : 0.014f) + soundEnergy * 0.08f;
        for (Stroke stroke : sculpture.strokes) {
            for (int i = 0; i < stroke.points.size() - 1; i++) {
                Vec3 a = stroke.points.get(i);
                Vec3 b = stroke.points.get(i + 1);
                Vec3 ar = rotate(a, rotX, rotY);
                Vec3 br = rotate(b, rotX, rotY);
                float pa = 1.0f / (1.95f + ar.z * 0.34f);
                float pb = 1.0f / (1.95f + br.z * 0.34f);
                RenderSegment seg = new RenderSegment();
                seg.x1 = cx + ar.x * scale * pa;
                seg.y1 = cy - ar.y * scale * pa;
                seg.x2 = cx + br.x * scale * pb;
                seg.y2 = cy - br.y * scale * pb;
                seg.depth = (ar.z + br.z) * 0.5f + stroke.layer * 0.05f;
                seg.energy = stroke.energy;
                seg.radius = Math.max(1f, stroke.radius * scale * 0.045f * (pa + pb) * 0.5f * (1f + soundEnergy * (ghost ? 0.04f : 0.18f)));
                seg.colorRole = stroke.colorRole;
                seg.strokeType = stroke.type;
                segments.add(seg);
            }
        }
        Collections.sort(segments, new Comparator<RenderSegment>() {
            @Override public int compare(RenderSegment a, RenderSegment b) { return Float.compare(b.depth, a.depth); }
        });

        Palette palette = museumMode ? Palette.museum() : Palette.get(sculpture.dna.paletteIndex);
        drawSculptureShadow(canvas, segments, scale, alpha, ghost);
        if (asciiVolumeMode) {
            drawAsciiVolume(canvas, segments, palette, alpha, ghost, time);
            return;
        }

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        for (RenderSegment seg : segments) {
            int base = palette.color(seg.colorRole);
            int dark = blend(base, Color.BLACK, 0.55f);
            int lightColor = blend(base, Color.WHITE, 0.44f);
            float light = clamp(0.56f + seg.depth * 0.22f + seg.energy * 0.10f, 0.18f, 1f);
            int color = blend(dark, lightColor, light);
            int a = (int)(alpha * (ghost ? 58 : 238));
            paint.setColor(withAlpha(color, a));
            paint.setStrokeWidth(seg.radius * (ghost ? 0.80f : 1.0f));
            canvas.drawLine(seg.x1, seg.y1, seg.x2, seg.y2, paint);

            if (!ghost && seg.radius > 3f && seg.strokeType != Stroke.ROOT) {
                paint.setColor(withAlpha(blend(base, Color.WHITE, 0.70f), (int)(alpha * 86)));
                paint.setStrokeWidth(Math.max(1f, seg.radius * 0.22f));
                canvas.drawLine(seg.x1 - seg.radius * 0.08f, seg.y1 - seg.radius * 0.26f, seg.x2 - seg.radius * 0.08f, seg.y2 - seg.radius * 0.26f, paint);
                paint.setColor(withAlpha(Color.BLACK, (int)(alpha * 33)));
                paint.setStrokeWidth(Math.max(1f, seg.radius * 0.16f));
                canvas.drawLine(seg.x1 + seg.radius * 0.24f, seg.y1 + seg.radius * 0.25f, seg.x2 + seg.radius * 0.24f, seg.y2 + seg.radius * 0.25f, paint);
            }
        }

        if (!ghost) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(cx - scale * 0.08f, cy - scale * 0.18f, scale * 0.62f,
                    Color.argb(62, 255, 255, 255), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, scale * 0.62f, paint);
            paint.setShader(null);
        }
    }


    private void drawAsciiVolume(Canvas canvas, List<RenderSegment> segments, Palette palette, float alpha, boolean ghost, float time) {
        final char[] ramp = {'.', ':', '-', '=', '+', '*', '#', '%', '@'};
        textPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.BOLD));
        textPaint.setTextAlign(Paint.Align.CENTER);
        for (RenderSegment seg : segments) {
            float dx = seg.x2 - seg.x1;
            float dy = seg.y2 - seg.y1;
            float len = (float)Math.sqrt(dx * dx + dy * dy);
            if (len < 1f) continue;
            float ux = dx / len;
            float uy = dy / len;
            float nx = -uy;
            float ny = ux;
            int samples = Math.max(2, Math.min(52, (int)(len / Math.max(6f, seg.radius * 0.72f))));
            int rings = ghost ? 0 : Math.max(1, Math.min(3, (int)(seg.radius / 7f)));
            int base = palette.color(seg.colorRole);
            float depthLight = clamp(0.50f + seg.depth * 0.24f + soundEnergy * 0.16f + micHighs * 0.10f, 0.12f, 1f);
            int lit = blend(blend(base, Color.BLACK, 0.38f), blend(base, Color.WHITE, 0.52f), depthLight);
            for (int i = 0; i <= samples; i++) {
                float p = i / (float)samples;
                float cx = seg.x1 + dx * p;
                float cy = seg.y1 + dy * p;
                float wave = (float)Math.sin(time * 4.0f + p * 7.0f + midiPitch * 8.0f) * (soundEnergy + micOnset * 0.45f) * seg.radius * 0.20f;
                for (int ring = -rings; ring <= rings; ring++) {
                    float rr = rings == 0 ? 0f : ring / (float)rings;
                    float off = rr * seg.radius * 0.52f + wave;
                    float shade = clamp(depthLight + (1f - Math.abs(rr)) * 0.24f - Math.abs(rr) * 0.18f, 0f, 1f);
                    int ci = Math.max(0, Math.min(ramp.length - 1, (int)(shade * (ramp.length - 1))));
                    String glyph = String.valueOf(ramp[ci]);
                    float x = cx + nx * off;
                    float y = cy + ny * off;
                    float size = Math.max(8f, seg.radius * (ghost ? 1.1f : 1.42f) * (1f - Math.abs(rr) * 0.10f));
                    textPaint.setTextSize(size);
                    if (!ghost) {
                        textPaint.setColor(Color.argb((int)(alpha * 70), 0, 0, 0));
                        canvas.drawText(glyph, x + seg.radius * 0.20f, y + seg.radius * 0.26f, textPaint);
                    }
                    textPaint.setColor(withAlpha(lit, (int)(alpha * (ghost ? 70 : 242))));
                    canvas.drawText(glyph, x, y, textPaint);
                    if (!ghost && ring == 0 && seg.radius > 6f) {
                        textPaint.setTextSize(size * 0.58f);
                        textPaint.setColor(withAlpha(blend(base, Color.WHITE, 0.78f), (int)(alpha * 92)));
                        canvas.drawText("'", x - nx * seg.radius * 0.23f - uy * seg.radius * 0.10f, y - ny * seg.radius * 0.23f + ux * seg.radius * 0.10f, textPaint);
                    }
                }
            }
        }
        textPaint.setTypeface(Typeface.create(Typeface.MONOSPACE, Typeface.NORMAL));
    }

    private void drawSculptureShadow(Canvas canvas, List<RenderSegment> segments, float scale, float alpha, boolean ghost) {
        softPaint.setStyle(Paint.Style.STROKE);
        softPaint.setStrokeCap(Paint.Cap.ROUND);
        softPaint.setStrokeJoin(Paint.Join.ROUND);
        softPaint.setMaskFilter(new BlurMaskFilter(ghost ? 9f : 20f, BlurMaskFilter.Blur.NORMAL));
        softPaint.setColor(Color.argb((int)(alpha * (ghost ? 30 : 88)), 0, 0, 0));
        float oy = scale * (ghost ? 0.12f : 0.18f);
        float ox = scale * 0.035f;
        for (RenderSegment seg : segments) {
            softPaint.setStrokeWidth(seg.radius * (ghost ? 2.1f : 3.6f));
            canvas.drawLine(seg.x1 + ox, seg.y1 + oy, seg.x2 + ox, seg.y2 + oy, softPaint);
        }
        softPaint.setMaskFilter(null);
    }

    private void drawInterface(Canvas canvas, float w, float h, float t) {
        float pad = Math.max(22f, w * 0.055f);
        Palette p = museumMode ? Palette.museum() : Palette.get(current.dna.paletteIndex);
        int uiColor = museumMode ? Color.rgb(25, 24, 22) : readableUiColor(p.background);

        textPaint.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(sp(12));
        textPaint.setColor(withAlpha(uiColor, 178));
        canvas.drawText("SCULPTURE GARDEN", pad, pad + sp(8), textPaint);

        textPaint.setTextSize(sp(9));
        textPaint.setColor(withAlpha(uiColor, 118));
        canvas.drawText("ART DNA · " + Palette.get(current.dna.paletteIndex).name + " · " + current.archetypeName(), pad, pad + sp(27), textPaint);
        float dnaW = textPaint.measureText("ART DNA · " + Palette.get(current.dna.paletteIndex).name) + sp(22);
        dnaRect.set(pad - sp(8), pad + sp(8), pad + dnaW, pad + sp(38));

        String dnaLine = "GEN " + current.generation + " · " + current.materialName() + " · DNA " + current.dna.shortCode();
        textPaint.setTextSize(sp(10));
        textPaint.setColor(withAlpha(uiColor, 132));
        canvas.drawText(dnaLine, pad, h - pad, textPaint);

        String help = "sound births particles · particles condense into ASCII volume · MIDI steers the field";
        textPaint.setTextSize(sp(8.5f));
        textPaint.setColor(withAlpha(uiColor, 92));
        canvas.drawText(help, pad, h - pad + sp(16), textPaint);

        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setTextSize(sp(14));
        textPaint.setColor(withAlpha(uiColor, 210));
        String evolve = "EVOLVE";
        float ex = w - pad;
        float ey = h - pad;
        canvas.drawText(evolve, ex, ey, textPaint);
        float ew = textPaint.measureText(evolve) + sp(30);
        evolveRect.set(ex - ew, ey - sp(30), ex + sp(10), ey + sp(18));

        textPaint.setTextSize(sp(9));
        String museum = museumMode ? "COLOR" : "MUSEUM";
        canvas.drawText(museum, w - pad, pad + sp(8), textPaint);
        float mw = textPaint.measureText(museum) + sp(28);
        museumRect.set(w - pad - mw, pad - sp(16), w - pad + sp(10), pad + sp(22));

        String relax = "RELAX";
        canvas.drawText(relax, w - pad, pad + sp(29), textPaint);
        float rw = textPaint.measureText(relax) + sp(28);
        relaxRect.set(w - pad - rw, pad + sp(6), w - pad + sp(10), pad + sp(42));

        String ascii = asciiVolumeMode ? "ASCII" : "CLAY";
        canvas.drawText(ascii, w - pad, pad + sp(50), textPaint);
        float aw = textPaint.measureText(ascii) + sp(28);
        asciiRect.set(w - pad - aw, pad + sp(27), w - pad + sp(10), pad + sp(63));

        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(sp(8.5f));
        int midiAlpha = midiConnected ? 170 + (int)(midiControlGlow * 70f) : 86;
        textPaint.setColor(withAlpha(uiColor, midiAlpha));
        canvas.drawText((midiConnected ? "MIDI · " : "MIDI · ") + midiName, pad, pad + sp(48), textPaint);
        String listen = (micListening ? "LISTEN · " : "MIC · ") + micName;
        textPaint.setColor(withAlpha(uiColor, micListening ? 170 : 92));
        canvas.drawText(listen, pad, pad + sp(64), textPaint);
        listenRect.set(pad - sp(8), pad + sp(47), pad + textPaint.measureText(listen) + sp(16), pad + sp(73));
        drawAudioMeters(canvas, pad, pad + sp(78), w * 0.24f, uiColor);

        long age = System.currentTimeMillis() - pulseAt;
        if (age < 1800L) {
            float a = 1f - age / 1800f;
            drawTextCentered(canvas, pulseText, w * 0.5f, h * 0.84f, 12f, a * 0.66f);
        }
    }


    private void drawAudioMeters(Canvas canvas, float x, float y, float width, int uiColor) {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1f, sp(1.2f)));
        float gap = sp(4f);
        float line = width;
        int alpha = micListening ? 82 : 36;
        paint.setColor(withAlpha(uiColor, alpha));
        canvas.drawLine(x, y, x + line, y, paint);
        canvas.drawLine(x, y + gap, x + line, y + gap, paint);
        canvas.drawLine(x, y + gap * 2f, x + line, y + gap * 2f, paint);
        paint.setColor(withAlpha(uiColor, micListening ? 182 : 74));
        canvas.drawLine(x, y, x + line * clamp(micBass, 0f, 1f), y, paint);
        canvas.drawLine(x, y + gap, x + line * clamp(micMids, 0f, 1f), y + gap, paint);
        canvas.drawLine(x, y + gap * 2f, x + line * clamp(micHighs, 0f, 1f), y + gap * 2f, paint);
        if (micOnset > 0.04f) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(withAlpha(uiColor, (int)(80 + micOnset * 150)));
            canvas.drawCircle(x + line + sp(8), y + gap, sp(2.2f + micOnset * 4.2f), paint);
        }
        paint.setStyle(Paint.Style.FILL);
    }

    private void drawRelaxWhisper(Canvas canvas, float w, float h, float t) {
        long age = System.currentTimeMillis() - pulseAt;
        float a = age < 2200L ? 1f - age / 2200f : 0.11f + 0.04f * (float)Math.sin(t * 0.35f);
        drawTextCentered(canvas, pulseText, w * 0.5f, h * 0.89f, 10f, a * 0.36f);
    }

    private void drawTextCentered(Canvas canvas, String text, float x, float y, float sizeSp, float alpha) {
        Palette p = museumMode ? Palette.museum() : Palette.get(current.dna.paletteIndex);
        int base = museumMode ? Color.rgb(22, 22, 20) : readableUiColor(p.background);
        textPaint.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sp(sizeSp));
        textPaint.setColor(withAlpha(base, (int)(255 * clamp(alpha, 0f, 1f))));
        canvas.drawText(text, x, y, textPaint);
    }

    private int readableUiColor(int bg) {
        int luminance = (int)(Color.red(bg) * 0.299f + Color.green(bg) * 0.587f + Color.blue(bg) * 0.114f);
        return luminance > 145 ? Color.rgb(18, 18, 17) : Color.rgb(246, 242, 230);
    }

    private float sp(float v) { return v * getResources().getDisplayMetrics().scaledDensity; }

    private static Vec3 rotate(Vec3 p, float ax, float ay) {
        float cosY = (float)Math.cos(ay), sinY = (float)Math.sin(ay);
        float cosX = (float)Math.cos(ax), sinX = (float)Math.sin(ax);
        float x = p.x * cosY - p.z * sinY;
        float z = p.x * sinY + p.z * cosY;
        float y = p.y * cosX - z * sinX;
        z = p.y * sinX + z * cosX;
        return new Vec3(x, y, z);
    }

    private static float clamp(float v, float min, float max) { return Math.max(min, Math.min(max, v)); }

    private static int withAlpha(int color, int alpha) {
        alpha = Math.max(0, Math.min(255, alpha));
        return Color.argb(alpha, Color.red(color), Color.green(color), Color.blue(color));
    }

    private static int blend(int c1, int c2, float t) {
        t = clamp(t, 0f, 1f);
        int r = (int)(Color.red(c1) + (Color.red(c2) - Color.red(c1)) * t);
        int g = (int)(Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t);
        int b = (int)(Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * t);
        return Color.rgb(r, g, b);
    }

    private static class SoundParticle {
        static final int BASS = 0;
        static final int MID = 1;
        static final int HIGH = 2;
        static final int TRANSIENT = 3;
        long id;
        int type;
        float x, y, z;
        float vx, vy, vz;
        float energy, mass, age, life, affinity;
    }

    private static class ParticleRender {
        float x, y, z, size, alpha, energy;
        int type;
    }

    private static class Vec3 {
        float x, y, z;
        Vec3(float x, float y, float z) { this.x = x; this.y = y; this.z = z; }
        Vec3 add(Vec3 other) { return new Vec3(x + other.x, y + other.y, z + other.z); }
        Vec3 scale(float s) { return new Vec3(x * s, y * s, z * s); }
    }

    private static class Stroke {
        static final int TUBE = 0, LOOP = 1, CAPSULE = 2, LADDER = 3, GLYPH = 4, ROOT = 5, ARCH = 6, COIL = 7;
        final List<Vec3> points = new ArrayList<>();
        float radius;
        float energy;
        float layer;
        int colorRole;
        int type;
    }

    private static class RenderSegment {
        float x1, y1, x2, y2, depth, radius, energy;
        int colorRole, strokeType;
    }

    private static class Sculpture {
        static int nextId = 1;
        int id = nextId++;
        int parentId = 0;
        int generation = 1;
        int archetype = 0;
        String mutationName = "SEED";
        final List<Integer> childrenIds = new ArrayList<>();
        DNA dna;
        final List<Stroke> strokes = new ArrayList<>();

        static Sculpture seed() {
            Sculpture s = new Sculpture();
            s.dna = DNA.seed();
            s.rebuild();
            return s;
        }

        Sculpture createChild(int childIndex, float mutation) {
            Sculpture child = new Sculpture();
            child.parentId = this.id;
            child.generation = this.generation + 1;
            child.dna = this.dna.mutated(childIndex, mutation);
            child.mutationName = mutationLabel(childIndex);
            child.rebuild();
            return child;
        }

        private String mutationLabel(int index) {
            switch (index) {
                case 1: return "SAFE";
                case 2: return "MINIMAL";
                case 3: return "EXPRESSIVE";
                case 4: return "COMPACT";
                default: return "STRANGE";
            }
        }

        String materialName() { return Material.name(dna.materialIndex); }

        String archetypeName() {
            String[] names = {"ORBITAL", "TOTEM", "FRAME", "KNOT", "DRIFT", "EMBLEM", "COLUMN"};
            return names[Math.floorMod(archetype, names.length)];
        }

        void addGesture(float influence) {
            dna.growth = clamp(dna.growth + 0.04f * influence, 0.12f, 1f);
            dna.seed += 83L + (long)(influence * 777f) + strokes.size() * 19L;
            dna.accentLines = clamp(dna.accentLines + 0.018f * influence, 0.06f, 0.76f);
            rebuild();
        }


        void growFromSound(float bass, float mids, float highs, float onset, float sustain) {
            float energy = clamp(bass * 0.36f + mids * 0.34f + highs * 0.18f + onset * 0.22f, 0f, 1f);
            dna.seed += 157L + (long)(energy * 2200f) + (long)(System.currentTimeMillis() % 997L);
            dna.growth = clamp(dna.growth + 0.035f + energy * 0.045f, 0.14f, 1.0f);
            dna.thickness = clamp(dna.thickness + bass * 0.032f - highs * 0.006f, 0.22f, 0.98f);
            dna.curvature = clamp(dna.curvature + mids * 0.026f + sustain * 0.010f, 0.22f, 0.97f);
            dna.loopFrequency = clamp(dna.loopFrequency + mids * 0.018f + onset * 0.010f, 0.12f, 0.96f);
            dna.accentLines = clamp(dna.accentLines + highs * 0.038f + onset * 0.012f, 0.05f, 0.78f);
            dna.rhythm = clamp(dna.rhythm + (mids - 0.28f) * 0.022f, 0.14f, 0.95f);
            dna.chaos = clamp(dna.chaos + onset * 0.010f + highs * 0.006f, 0.08f, 0.54f);
            if (bass > 0.42f) dna.secondaryShapeCount = Math.min(6, dna.secondaryShapeCount + 1);
            if (highs > 0.38f) dna.accentShapeCount = Math.min(4, dna.accentShapeCount + 1);
            rebuild();
        }

        void cultivateFromSound(float bass, float mids, float highs, float sustain) {
            dna.growth = clamp(dna.growth + mids * 0.010f + sustain * 0.004f, 0.14f, 1.0f);
            dna.thickness = clamp(dna.thickness + bass * 0.006f - highs * 0.002f, 0.22f, 0.98f);
            dna.curvature = clamp(dna.curvature + sustain * 0.006f + mids * 0.004f, 0.22f, 0.97f);
            dna.accentLines = clamp(dna.accentLines + highs * 0.007f, 0.05f, 0.78f);
            dna.twist += (highs - bass) * 0.006f;
            if (mids + bass + highs > 0.24f) dna.seed += 17L + (long)((bass + mids + highs) * 100f);
            rebuild();
        }

        void restFromSilence(float silence) {
            dna.chaos = clamp(dna.chaos - 0.008f * silence, 0.08f, 0.54f);
            dna.accentLines = clamp(dna.accentLines - 0.004f * silence, 0.04f, 0.78f);
            dna.balance = clamp(dna.balance + 0.004f * silence, 0.30f, 0.95f);
            dna.seed += 5L;
            rebuild();
        }

        void simplify() {
            dna.growth = clamp(dna.growth - 0.06f, 0.12f, 1f);
            dna.accentLines = clamp(dna.accentLines - 0.06f, 0.04f, 0.72f);
            dna.compositionSpread = clamp(dna.compositionSpread - 0.035f, 0.14f, 0.88f);
            dna.seed += 431L;
            rebuild();
        }

        void rebuild() {
            strokes.clear();
            Random r = new Random(dna.seed);
            archetype = Math.floorMod((int)(dna.seed + Math.round(dna.rhythm * 10f)), 7);

            int dominant = 1 + (dna.dominantShapeCount > 1 ? 1 : 0);
            int secondary = 2 + Math.round(dna.secondaryShapeCount * dna.growth * 0.75f);
            int accent = 1 + Math.round(dna.accentShapeCount * dna.accentLines);

            for (int i = 0; i < dominant; i++) addDominant(r, i, dominant);
            for (int i = 0; i < secondary; i++) addSecondary(r, i, secondary);
            for (int i = 0; i < accent; i++) addAccent(r, i, accent);
            qualityPass();
        }

        private void addDominant(Random r, int index, int total) {
            int choice = weightedChoice(r, new float[]{1.0f, dna.loopFrequency * 1.15f, 0.35f, 0.05f, 0.52f, 0.02f, 0.82f, 0.45f});
            float cx = archetypeX(index, total, r) * 0.56f;
            float cy = archetypeY(index, total, r) * 0.48f;
            float z = (r.nextFloat() - 0.5f) * 0.36f;
            float size = 0.56f + dna.compositionSpread * 0.34f + r.nextFloat() * 0.16f;
            float angle = (r.nextFloat() - 0.5f) * 1.8f + index * 0.75f;
            int color = index == 0 ? 0 : 1 + r.nextInt(3);
            float radius = 0.38f + dna.thickness * 0.55f;
            createShape(r, choice, cx, cy, z, size, angle, radius, color, index * 0.15f);
        }

        private void addSecondary(Random r, int index, int total) {
            int[] pool = {Stroke.TUBE, Stroke.LOOP, Stroke.CAPSULE, Stroke.LADDER, Stroke.GLYPH, Stroke.ARCH, Stroke.COIL};
            int choice = pool[Math.floorMod(r.nextInt(pool.length) + index, pool.length)];
            if (r.nextFloat() < dna.loopFrequency * 0.32f) choice = Stroke.LOOP;
            if (r.nextFloat() < 0.18f) choice = Stroke.LADDER;
            float orbit = (float)(Math.PI * 2f * index / Math.max(1, total) + dna.rhythm * 1.7f + r.nextFloat() * 0.7f);
            float spread = 0.22f + dna.compositionSpread * 0.52f;
            float cx = (float)Math.cos(orbit) * spread * (0.45f + r.nextFloat() * 0.58f);
            float cy = (float)Math.sin(orbit) * spread * 0.62f + (r.nextFloat() - 0.5f) * 0.28f;
            if (archetype == 1 || archetype == 6) cx *= 0.45f;
            if (archetype == 4) cx += (index - total * 0.5f) * 0.18f;
            float z = (r.nextFloat() - 0.5f) * 0.70f;
            float size = 0.24f + r.nextFloat() * 0.36f + dna.growth * 0.18f;
            float angle = orbit + (r.nextFloat() - 0.5f) * 1.5f;
            int color = 1 + r.nextInt(5);
            float radius = 0.18f + dna.thickness * (choice == Stroke.ROOT ? 0.16f : 0.34f);
            createShape(r, choice, cx, cy, z, size, angle, radius, color, 0.4f + index * 0.08f);
        }

        private void addAccent(Random r, int index, int total) {
            int choice = r.nextFloat() < 0.55f ? Stroke.ROOT : (r.nextFloat() < 0.75f ? Stroke.LADDER : Stroke.GLYPH);
            float side = index % 2 == 0 ? -1f : 1f;
            float cx = side * (0.24f + r.nextFloat() * 0.55f) * dna.compositionSpread;
            float cy = (r.nextFloat() - 0.5f) * 0.82f;
            float z = 0.25f + r.nextFloat() * 0.38f;
            float size = 0.18f + r.nextFloat() * 0.30f;
            float angle = side * (0.8f + r.nextFloat() * 1.2f);
            int color = 3 + r.nextInt(3);
            float radius = choice == Stroke.ROOT ? 0.055f + dna.thickness * 0.09f : 0.12f + dna.thickness * 0.16f;
            createShape(r, choice, cx, cy, z, size, angle, radius, color, 0.9f + index * 0.06f);
        }

        private float archetypeX(int index, int total, Random r) {
            float jitter = (r.nextFloat() - 0.5f) * 0.22f * dna.chaos;
            switch (archetype) {
                case 1: return jitter * 0.4f;
                case 2: return (index == 0 ? -0.28f : 0.28f) + jitter;
                case 4: return (index - 0.5f) * 0.55f + jitter;
                case 6: return (index == 0 ? -0.10f : 0.26f) + jitter;
                default: return (index - (total - 1) * 0.5f) * 0.35f + jitter;
            }
        }

        private float archetypeY(int index, int total, Random r) {
            float jitter = (r.nextFloat() - 0.5f) * 0.24f * dna.chaos;
            switch (archetype) {
                case 1: return (index - 0.5f) * 0.44f + jitter;
                case 6: return index == 0 ? 0.22f + jitter : -0.20f + jitter;
                case 3: return jitter;
                default: return (r.nextFloat() - 0.5f) * 0.42f;
            }
        }

        private int weightedChoice(Random r, float[] weights) {
            float sum = 0f;
            for (float w : weights) sum += Math.max(0.001f, w);
            float pick = r.nextFloat() * sum;
            for (int i = 0; i < weights.length; i++) {
                pick -= Math.max(0.001f, weights[i]);
                if (pick <= 0f) return i;
            }
            return 0;
        }

        private void createShape(Random r, int type, float cx, float cy, float z, float size, float angle, float radius, int colorRole, float layer) {
            switch (type) {
                case Stroke.LOOP: addLoop(r, cx, cy, z, size, angle, radius, colorRole, layer); break;
                case Stroke.CAPSULE: addCapsuleStack(r, cx, cy, z, size, angle, radius, colorRole, layer); break;
                case Stroke.LADDER: addLadder(r, cx, cy, z, size, angle, radius, colorRole, layer); break;
                case Stroke.GLYPH: addGlyph(r, cx, cy, z, size, angle, radius, colorRole, layer); break;
                case Stroke.ROOT: addRootLine(r, cx, cy, z, size, angle, radius, colorRole, layer); break;
                case Stroke.ARCH: addArchBlob(r, cx, cy, z, size, angle, radius, colorRole, layer); break;
                case Stroke.COIL: addCoil(r, cx, cy, z, size, angle, radius, colorRole, layer); break;
                default: addTube(r, cx, cy, z, size, angle, radius, colorRole, layer); break;
            }
        }

        private Stroke baseStroke(int type, float radius, int colorRole, float layer) {
            Stroke s = new Stroke();
            s.type = type;
            s.radius = radius;
            s.colorRole = colorRole;
            s.layer = layer;
            s.energy = 0.55f + radius * 0.18f;
            return s;
        }

        private void addPoint(Stroke s, float x, float y, float z, float angle) {
            float ca = (float)Math.cos(angle), sa = (float)Math.sin(angle);
            s.points.add(new Vec3(x * ca - y * sa, x * sa + y * ca, z));
        }

        private void addTube(Random r, float cx, float cy, float z, float size, float angle, float radius, int colorRole, float layer) {
            Stroke s = baseStroke(Stroke.TUBE, radius, colorRole, layer);
            int steps = 7 + r.nextInt(5);
            float len = size * (0.75f + r.nextFloat() * 0.42f);
            float amp = size * (0.10f + dna.curvature * 0.12f);
            for (int i = 0; i <= steps; i++) {
                float p = i / (float)steps;
                float x = cx + (p - 0.5f) * len;
                float y = cy + (float)Math.sin(p * Math.PI * (1.0f + dna.rhythm * 1.5f) + r.nextFloat() * 0.06f) * amp;
                float zz = z + (p - 0.5f) * 0.16f * dna.chaos;
                addPoint(s, x, y, zz, angle);
            }
            strokes.add(s);
        }

        private void addLoop(Random r, float cx, float cy, float z, float size, float angle, float radius, int colorRole, float layer) {
            Stroke s = baseStroke(Stroke.LOOP, radius, colorRole, layer);
            int steps = 20 + r.nextInt(8);
            float start = r.nextFloat() * 1.0f;
            float arc = (float)(Math.PI * (1.15f + dna.loopFrequency * 1.15f + r.nextFloat() * 0.55f));
            float rx = size * (0.32f + r.nextFloat() * 0.26f);
            float ry = size * (0.25f + r.nextFloat() * 0.31f);
            for (int i = 0; i <= steps; i++) {
                float p = i / (float)steps;
                float a = start + p * arc;
                float wobble = 1f + (float)Math.sin(p * Math.PI * 3f + dna.rhythm) * 0.08f * dna.chaos;
                float x = cx + (float)Math.cos(a) * rx * wobble;
                float y = cy + (float)Math.sin(a) * ry * wobble;
                float zz = z + (float)Math.sin(a + 0.7f) * 0.12f;
                addPoint(s, x, y, zz, angle);
            }
            strokes.add(s);
        }

        private void addCapsuleStack(Random r, float cx, float cy, float z, float size, float angle, float radius, int colorRole, float layer) {
            int count = 3 + r.nextInt(4);
            for (int k = 0; k < count; k++) {
                float offset = (k - (count - 1) * 0.5f) * size * 0.17f;
                addTube(r, cx + offset, cy + (float)Math.sin(k * 0.9f) * size * 0.06f, z + k * 0.035f, size * 0.38f, angle + (k - count * 0.5f) * 0.06f, radius * 0.88f, colorRole, layer + k * 0.018f);
            }
        }

        private void addLadder(Random r, float cx, float cy, float z, float size, float angle, float radius, int colorRole, float layer) {
            int bars = 3 + r.nextInt(4);
            float height = size * 0.70f;
            for (int k = 0; k < bars; k++) {
                float y = cy + (k - (bars - 1) * 0.5f) * height / bars;
                addTube(r, cx, y, z + k * 0.025f, size * 0.22f, angle + 1.57f, radius * 0.82f, colorRole, layer + k * 0.02f);
            }
            if (r.nextFloat() < 0.6f) addTube(r, cx - size * 0.14f, cy, z, size * 0.55f, angle, radius * 0.55f, colorRole, layer + 0.12f);
        }

        private void addGlyph(Random r, float cx, float cy, float z, float size, float angle, float radius, int colorRole, float layer) {
            Stroke s = baseStroke(Stroke.GLYPH, radius, colorRole, layer);
            int steps = 6 + r.nextInt(4);
            float x = cx - size * 0.28f;
            float y = cy + (r.nextFloat() - 0.5f) * size * 0.2f;
            for (int i = 0; i <= steps; i++) {
                float p = i / (float)steps;
                float bend = (float)Math.sin(p * Math.PI * (1.4f + dna.rhythm)) * size * 0.18f;
                float zig = ((i % 2 == 0) ? -1f : 1f) * size * 0.11f * (0.3f + dna.chaos);
                addPoint(s, x + p * size * 0.58f, y + bend + zig, z + (i % 3) * 0.035f, angle);
            }
            strokes.add(s);
        }

        private void addRootLine(Random r, float cx, float cy, float z, float size, float angle, float radius, int colorRole, float layer) {
            Stroke s = baseStroke(Stroke.ROOT, radius, colorRole, layer);
            int steps = 8 + r.nextInt(7);
            float walkX = cx;
            float walkY = cy;
            for (int i = 0; i <= steps; i++) {
                float p = i / (float)steps;
                walkX += (r.nextFloat() - 0.45f) * size * 0.10f;
                walkY += size * 0.055f + (r.nextFloat() - 0.5f) * size * 0.12f;
                addPoint(s, walkX + (p - 0.5f) * size * 0.26f, walkY, z + p * 0.11f, angle);
            }
            strokes.add(s);
        }

        private void addArchBlob(Random r, float cx, float cy, float z, float size, float angle, float radius, int colorRole, float layer) {
            Stroke s = baseStroke(Stroke.ARCH, radius * 1.05f, colorRole, layer);
            int steps = 16 + r.nextInt(6);
            float rx = size * (0.33f + r.nextFloat() * 0.22f);
            float ry = size * (0.28f + r.nextFloat() * 0.22f);
            float start = (float)Math.PI * (0.05f + r.nextFloat() * 0.2f);
            float end = (float)Math.PI * (0.90f + r.nextFloat() * 0.30f);
            for (int i = 0; i <= steps; i++) {
                float p = i / (float)steps;
                float a = start + (end - start) * p;
                float x = cx + (float)Math.cos(a) * rx;
                float y = cy + (float)Math.sin(a) * ry - ry * 0.25f;
                float zz = z + (float)Math.sin(a * 1.7f) * 0.10f;
                addPoint(s, x, y, zz, angle);
            }
            strokes.add(s);
        }

        private void addCoil(Random r, float cx, float cy, float z, float size, float angle, float radius, int colorRole, float layer) {
            Stroke s = baseStroke(Stroke.COIL, radius, colorRole, layer);
            int steps = 22 + r.nextInt(8);
            float turns = 1.4f + dna.rhythm * 1.5f;
            for (int i = 0; i <= steps; i++) {
                float p = i / (float)steps;
                float a = (float)(p * Math.PI * 2f * turns);
                float spiral = size * (0.07f + p * 0.18f);
                float x = cx + (p - 0.5f) * size * 0.56f;
                float y = cy + (float)Math.sin(a) * spiral;
                float zz = z + (float)Math.cos(a) * spiral * 0.48f;
                addPoint(s, x, y, zz, angle);
            }
            strokes.add(s);
        }

        private void qualityPass() {
            int max = 11;
            while (strokes.size() > max) strokes.remove(strokes.size() - 1);
            if (strokes.size() < 4) {
                Random r = new Random(dna.seed + 999L);
                addSecondary(r, 0, 3);
                addAccent(r, 1, 2);
            }
        }
    }

    private static class DNA {
        long seed;
        float curvature, elasticity, chaos, rhythm, density, thickness, balance, growth;
        float loopFrequency, shadowSoftness, surfaceNoise, twist, paletteShift;
        float accentLines, softness, compositionSpread, symmetryBias, voidSpace;
        int dominantShapeCount, secondaryShapeCount, accentShapeCount;
        int materialIndex, paletteIndex;

        static DNA seed() {
            DNA dna = new DNA();
            dna.seed = 730241L;
            dna.curvature = 0.82f;
            dna.elasticity = 0.62f;
            dna.chaos = 0.30f;
            dna.rhythm = 0.72f;
            dna.density = 0.56f;
            dna.thickness = 0.78f;
            dna.balance = 0.77f;
            dna.growth = 0.48f;
            dna.loopFrequency = 0.66f;
            dna.shadowSoftness = 0.74f;
            dna.surfaceNoise = 0.24f;
            dna.twist = 0.0f;
            dna.paletteShift = 0.15f;
            dna.accentLines = 0.38f;
            dna.softness = 0.92f;
            dna.compositionSpread = 0.58f;
            dna.symmetryBias = 0.18f;
            dna.voidSpace = 0.41f;
            dna.dominantShapeCount = 2;
            dna.secondaryShapeCount = 4;
            dna.accentShapeCount = 2;
            dna.materialIndex = 0;
            dna.paletteIndex = 0;
            return dna;
        }

        void applyMangoscamBias(float strength) {
            curvature = mix(curvature, 0.82f, strength);
            thickness = mix(thickness, 0.78f, strength);
            loopFrequency = mix(loopFrequency, 0.66f, strength);
            chaos = mix(chaos, 0.30f, strength);
            rhythm = mix(rhythm, 0.72f, strength);
            accentLines = mix(accentLines, 0.38f, strength);
            softness = mix(softness, 0.92f, strength);
            compositionSpread = mix(compositionSpread, 0.58f, strength);
        }

        private static float mix(float a, float b, float t) { return a + (b - a) * clamp(t, 0f, 1f); }

        DNA mutated(int childIndex, float intensity) {
            DNA d = copy();
            Random r = new Random(seed + childIndex * 104729L + 991L);
            d.seed = seed + 1009L * childIndex + r.nextInt(9000);
            if (childIndex == 1) {
                d.balance = mutateNice(r, balance, intensity * 0.35f, 0.45f, 0.92f);
                d.chaos = mutateNice(r, chaos, intensity * 0.25f, 0.12f, 0.44f);
            } else if (childIndex == 2) {
                d.secondaryShapeCount = Math.max(2, secondaryShapeCount - 1);
                d.accentShapeCount = Math.max(1, accentShapeCount - 1);
                d.compositionSpread = mutateNice(r, compositionSpread - 0.08f, intensity * 0.45f, 0.18f, 0.78f);
            } else if (childIndex == 3) {
                d.curvature = mutateNice(r, curvature + 0.08f, intensity, 0.38f, 0.96f);
                d.loopFrequency = mutateNice(r, loopFrequency + 0.08f, intensity, 0.20f, 0.95f);
                d.accentLines = mutateNice(r, accentLines + 0.08f, intensity, 0.10f, 0.76f);
            } else if (childIndex == 4) {
                d.compositionSpread = mutateNice(r, compositionSpread - 0.12f, intensity, 0.15f, 0.68f);
                d.thickness = mutateNice(r, thickness + 0.05f, intensity * 0.6f, 0.28f, 0.96f);
            } else {
                d.chaos = mutateNice(r, chaos + 0.08f, intensity * 0.9f, 0.16f, 0.55f);
                d.paletteIndex = Math.floorMod(paletteIndex + 1 + r.nextInt(Palette.COUNT - 1), Palette.COUNT);
            }
            d.curvature = mutateNice(r, d.curvature, intensity * 0.55f, 0.20f, 0.96f);
            d.rhythm = mutateNice(r, d.rhythm, intensity * 0.62f, 0.16f, 0.94f);
            d.density = mutateNice(r, d.density, intensity * 0.50f, 0.20f, 0.86f);
            d.thickness = mutateNice(r, d.thickness, intensity * 0.44f, 0.22f, 0.96f);
            d.balance = mutateNice(r, d.balance, intensity * 0.38f, 0.35f, 0.94f);
            d.growth = mutateNice(r, d.growth + 0.045f, intensity * 0.45f, 0.18f, 1.0f);
            d.shadowSoftness = mutateNice(r, d.shadowSoftness, intensity * 0.35f, 0.42f, 0.95f);
            d.surfaceNoise = mutateNice(r, d.surfaceNoise, intensity * 0.42f, 0.06f, 0.48f);
            d.twist += (r.nextFloat() - 0.5f) * intensity * 2.2f;
            if (r.nextFloat() < 0.28f) d.paletteIndex = Math.floorMod(d.paletteIndex + r.nextInt(3), Palette.COUNT);
            if (r.nextFloat() < 0.18f) d.materialIndex = (d.materialIndex + 1 + r.nextInt(Material.COUNT - 1)) % Material.COUNT;
            return d;
        }

        private static float mutateNice(Random r, float base, float intensity, float min, float max) {
            float harmonic = (r.nextFloat() - 0.5f) * 2f;
            harmonic = (float)Math.sin(harmonic * Math.PI * 0.5f);
            return clamp(base + harmonic * intensity, min, max);
        }

        DNA copy() {
            DNA d = new DNA();
            d.seed = seed; d.curvature = curvature; d.elasticity = elasticity; d.chaos = chaos; d.rhythm = rhythm;
            d.density = density; d.thickness = thickness; d.balance = balance; d.growth = growth;
            d.loopFrequency = loopFrequency; d.shadowSoftness = shadowSoftness; d.surfaceNoise = surfaceNoise; d.twist = twist;
            d.paletteShift = paletteShift; d.accentLines = accentLines; d.softness = softness; d.compositionSpread = compositionSpread;
            d.symmetryBias = symmetryBias; d.voidSpace = voidSpace; d.dominantShapeCount = dominantShapeCount;
            d.secondaryShapeCount = secondaryShapeCount; d.accentShapeCount = accentShapeCount;
            d.materialIndex = materialIndex; d.paletteIndex = paletteIndex;
            return d;
        }

        String shortCode() {
            long code = Math.abs(seed % 100000L);
            return String.format(Locale.US, "%05d-%02d", code, paletteIndex + 1);
        }
    }

    private static class Palette {
        static final int COUNT = 7;
        final String name;
        final int background, primary, secondary, accent1, accent2, accent3, dark;
        Palette(String name, int background, int primary, int secondary, int accent1, int accent2, int accent3, int dark) {
            this.name = name; this.background = background; this.primary = primary; this.secondary = secondary;
            this.accent1 = accent1; this.accent2 = accent2; this.accent3 = accent3; this.dark = dark;
        }
        int color(int role) {
            switch (Math.floorMod(role, 6)) {
                case 0: return primary;
                case 1: return secondary;
                case 2: return accent1;
                case 3: return accent2;
                case 4: return accent3;
                default: return dark;
            }
        }
        static Palette get(int index) {
            switch (Math.floorMod(index, COUNT)) {
                case 1: return new Palette("MUSEUM MINT", rgb(185,220,203), rgb(237,231,221), rgb(126,152,214), rgb(184,66,53), rgb(32,32,32), rgb(79,138,88), rgb(17,17,17));
                case 2: return new Palette("SOFT YELLOW", rgb(224,190,29), rgb(242,239,232), rgb(30,30,30), rgb(232,178,193), rgb(199,75,68), rgb(94,126,208), rgb(16,16,16));
                case 3: return new Palette("GALLERY BEIGE", rgb(232,210,187), rgb(245,241,234), rgb(38,58,102), rgb(185,64,51), rgb(79,138,88), rgb(225,201,74), rgb(17,17,17));
                case 4: return new Palette("PRIMARY BLUE", rgb(46,99,208), rgb(246,242,234), rgb(216,66,52), rgb(225,201,74), rgb(105,168,102), rgb(231,178,193), rgb(17,17,17));
                case 5: return new Palette("DUST PINK", rgb(216,142,160), rgb(245,234,216), rgb(240,209,79), rgb(193,59,51), rgb(79,154,113), rgb(94,126,208), rgb(17,17,17));
                case 6: return new Palette("STONE WHITE", rgb(241,238,231), rgb(224,198,61), rgb(94,126,208), rgb(201,69,58), rgb(22,22,22), rgb(245,241,234), rgb(17,17,17));
                default: return new Palette("RED ROOM", rgb(217,42,30), rgb(243,237,228), rgb(90,119,200), rgb(244,182,194), rgb(167,200,109), rgb(35,35,35), rgb(17,17,17));
            }
        }
        static Palette museum() { return new Palette("WHITE MUSEUM", rgb(238,236,230), rgb(236,232,222), rgb(105,120,165), rgb(187,70,59), rgb(30,30,28), rgb(198,178,71), rgb(17,17,17)); }
        private static int rgb(int r, int g, int b) { return Color.rgb(r, g, b); }
    }

    private static class Material {
        static final int COUNT = 8;
        static String name(int index) {
            String[] names = {"Matte Clay", "Painted Ceramic", "Soft Plaster", "Chalk Porcelain", "Coated Foam", "Latex Matte", "Rubber Pigment", "Canvas Resin"};
            return names[Math.floorMod(index, names.length)];
        }
    }
}
