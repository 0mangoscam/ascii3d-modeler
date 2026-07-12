package com.mangoscam.wemwem;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.*;
import android.media.*;
import android.os.SystemClock;
import android.view.*;
import java.util.*;

public class WemwemView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Random rng = new Random(3710611);
    private final ArrayList<Particle> particles = new ArrayList<>(520);
    private final ArrayList<SolidGlyph> solids = new ArrayList<>(140);
    private final ArrayList<DrawItem> drawItems = new ArrayList<>(1400);
    // No fixed ASCII ladder: every cloud generates its own procedural glyph matter.
    private final char[][] glyphPools = new char[][]{
            new char[]{'.','·',':','∴','°','⁙','˙','·'},
            new char[]{'-','~','≈','=','+','÷','×','≋','⌁'},
            new char[]{'*','#','%','@','█','▓','▒','░','◆','◇','◌','◎'},
            new char[]{'|','/','\\','(',')','{','}','[',']','<','>','?','!'}
    };
    private final int[] palette = new int[]{0xff9dfacb, 0xffd6f6ff, 0xffff5f5f, 0xff7f9cff, 0xfff4df75, 0xffffffff, 0xffff9be6, 0xffaaffff};
    private int cloudSeed = 37106;

    private AudioThread audioThread;
    private final SynthEngine synth = new SynthEngine();
    private volatile AudioFrame latest = new AudioFrame();
    private String midiStatus = "MIDI · WAITING";
    private boolean listening = false;
    private boolean persist = false;
    private boolean padOpen = false;
    private int decayMode = 3;
    private float cameraYaw = 0.15f;
    private float cameraPitch = -0.25f;
    private float zoom = 1.0f;
    private float lastX, lastY;
    private float lastDist = 0;
    private long lastFrame = SystemClock.uptimeMillis();
    private float soundMemory = 0;
    private float fieldBend = 0;
    private float midiDensity = 1f;
    private float midiMass = 1f;
    private float midiSpread = 1f;
    private float midiCohesion = 1f;
    private int maxParticles = 520;
    private int maxSolids = 120;
    private RectF listenRect = new RectF(), persistRect = new RectF(), decayRect = new RectF(), padToggleRect = new RectF();
    private final RectF[] padRects = new RectF[]{new RectF(), new RectF(), new RectF(), new RectF()};
    private long lastInvalidate = 0;

    public WemwemView(Context c) {
        super(c);
        setFocusable(true);
        setFocusableInTouchMode(true);
        p.setTypeface(Typeface.MONOSPACE);
        p.setSubpixelText(false);
        p.setLinearText(false);
        startListening();
    }

    public void resume() { lastFrame = SystemClock.uptimeMillis(); startListening(); postInvalidateOnAnimation(); }
    public void pause() { if (audioThread != null) audioThread.close(); audioThread = null; listening = false; }
    public void setMidiStatus(String s) { midiStatus = s; }

    public void startListening() {
        if (audioThread != null) return;
        if (getContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
        audioThread = new AudioThread(frame -> latest = frame);
        audioThread.start();
        listening = true;
    }

    public void manualBurst(float power) { padHit(2, power); }
    public void prune() {
        for (int i = particles.size() - 1; i >= 0; i -= 2) particles.remove(i);
        for (SolidGlyph g : solids) g.life *= .72f;
    }

    public void midiNote(int note, int velocity) {
        float v = Math.max(0.05f, velocity / 127f);
        synth.playMidiNote(note, v, 140);
        AudioFrame f = new AudioFrame(v, v * ((note < 55) ? 1f : .25f), v * .74f, v * ((note > 72) ? 1f : .38f), 1f, .26f, 0f);
        spawnBurst(f, noteToWorldX(note), .18f + v * .12f, 34 + (int)(v * 34));
        latest = f;
    }

    public void midiNoteOff(int note) { synth.releaseNote(note); }

    public void midiControl(int cc, int value) {
        float v = value / 127f;
        switch (cc) {
            case 1: midiCohesion = .45f + v * 1.55f; break;
            case 2: midiDensity = .35f + v * 1.75f; break;
            case 7: midiMass = .45f + v * 1.9f; break;
            case 10: midiSpread = .55f + v * 2.1f; break;
            case 11: maxParticles = 260 + (int)(v * 520); break;
            case 71: fieldBend = (v - .5f) * 2f; break;
            case 74: maxSolids = 40 + (int)(v * 160); break;
            case 91: decayMode = Math.max(0, Math.min(5, (int)(v * 6f))); break;
        }
    }

    public void midiPitchBend(float bend) { fieldBend = bend; }

    private float noteToWorldX(int note) { return ((note % 24) / 23f - .5f) * 1.7f; }

    @Override protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(.042f, (now - lastFrame) / 1000f);
        lastFrame = now;

        drawTerminalBackground(canvas);
        updateWorld(dt);
        renderWorld(canvas);
        drawUI(canvas);

        long delay = soundMemory > .04f || particles.size() > 0 || solids.size() > 0 ? 16 : 50;
        postInvalidateDelayed(delay);
    }

    private void updateWorld(float dt) {
        AudioFrame a = latest;
        float energy = Math.max(a.rms, Math.max(a.bass, Math.max(a.mid, a.high)));
        boolean hasSound = listening && energy > 0.035f;
        soundMemory = soundMemory * .91f + energy * .09f;

        if (hasSound) spawnFromAudio(a, dt);
        simulateParticles(a, dt, hasSound);
        condenseParticles(a, hasSound);
        erode(dt, hasSound);
        cameraYaw += dt * (0.025f + soundMemory * 0.08f + fieldBend * .04f);
        fieldBend *= .985f;
    }

    private void spawnFromAudio(AudioFrame a, float dt) {
        int births = Math.min(22, (int)((1 + a.rms * 26 + a.onset * 18) * midiDensity));
        for (int i = 0; i < births; i++) {
            int type = chooseType(a);
            Particle q = new Particle();
            float spread = (1.2f + a.mid * 1.1f + a.high * .6f) * midiSpread;
            // Spawn inside a true 3D Gaussian cloud around a moving acoustic attractor.
            float muX = (float)Math.sin((SystemClock.uptimeMillis()+type*913) * 0.00031) * .34f;
            float muY = (float)Math.cos((SystemClock.uptimeMillis()+type*577) * 0.00027) * .22f;
            float muZ = (float)Math.sin((SystemClock.uptimeMillis()+type*311) * 0.00023) * .34f;
            float sigma = .035f + a.rms * .11f + a.high * .055f;
            q.x = muX + randGaussian() * sigma;
            q.y = muY + randGaussian() * sigma;
            q.z = muZ + randGaussian() * sigma;
            float ang = (float)(rng.nextFloat() * Math.PI * 2);
            float elev = (rng.nextFloat() - .5f) * .75f;
            float speed = .09f + a.rms * .58f + a.onset * .9f;
            q.vx = (float)Math.cos(ang) * speed * spread;
            q.vy = elev * speed + (type == 2 ? -.18f : 0);
            q.vz = (float)Math.sin(ang) * speed * spread;
            q.energy = .32f + a.rms + rng.nextFloat() * .25f;
            q.mass = (type == 0 ? 1.6f : type == 1 ? 1.0f : .55f) * midiMass;
            q.type = type;
            q.ch = proceduralGlyph(type, q.energy, rng.nextFloat());
            q.color = proceduralColor(type, q.energy);
            q.scale = .72f + Math.abs(randGaussian()) * .55f;
            q.sigma = sigma;
            q.spin = (rng.nextFloat() - .5f) * 2f;
            q.life = 1f;
            q.age = 0f;
            particles.add(q);
        }
        trimParticles();
    }

    private void spawnBurst(AudioFrame a, float originX, float radius, int count) {
        int safeCount = Math.min(96, Math.max(8, count));
        for (int i=0; i<safeCount; i++) {
            Particle q = new Particle();
            q.x = originX + randGaussian() * radius;
            q.y = randGaussian() * radius * 1.3f;
            q.z = randGaussian() * radius;
            float ang = (float)(rng.nextFloat() * Math.PI * 2);
            float speed = .25f + rng.nextFloat() * .95f + a.onset * .4f;
            q.vx = (float)Math.cos(ang) * speed;
            q.vy = (rng.nextFloat() - .5f) * speed;
            q.vz = (float)Math.sin(ang) * speed;
            q.energy = .65f + a.rms;
            q.mass = (.55f + rng.nextFloat() * .85f) * midiMass;
            q.type = chooseType(a);
            q.ch = proceduralGlyph(q.type, q.energy, rng.nextFloat());
            q.color = proceduralColor(q.type, q.energy);
            q.scale = .75f + Math.abs(randGaussian()) * .75f;
            q.sigma = radius;
            q.spin = (rng.nextFloat() - .5f) * 2.4f;
            q.life = 1f;
            particles.add(q);
        }
        trimParticles();
    }

    private void padHit(int pad, float power) {
        float baseNote;
        AudioFrame f;
        switch (pad) {
            case 0:
                baseNote = 36; synth.playPad(0, .65f * power); f = new AudioFrame(.75f*power,.95f*power,.35f*power,.1f,1f,.45f,0); break;
            case 1:
                baseNote = 55; synth.playPad(1, .62f * power); f = new AudioFrame(.65f*power,.2f,.95f*power,.24f,1f,.6f,0); break;
            case 2:
                baseNote = 72; synth.playPad(2, .58f * power); f = new AudioFrame(.7f*power,.18f,.35f,.95f*power,1f,.2f,0); break;
            default:
                baseNote = 48; synth.playPad(3, .6f * power); f = new AudioFrame(.82f*power,.55f,.7f,.6f,1f,.8f,0); break;
        }
        spawnBurst(f, noteToWorldX((int)baseNote), .2f + power * .18f, 70);
        latest = f;
    }

    private void trimParticles() { while (particles.size() > maxParticles) particles.remove(0); }

    private int chooseType(AudioFrame a) {
        float total = a.bass + a.mid + a.high + .001f;
        float r = rng.nextFloat() * total;
        if (r < a.bass) return 0;
        if (r < a.bass + a.mid) return 1;
        return 2;
    }

    private void simulateParticles(AudioFrame a, float dt, boolean hasSound) {
        float cohesion = (.12f + a.sustain * .34f) * midiCohesion;
        float orbit = .2f + a.high * .38f + Math.abs(fieldBend) * .2f;
        float repel = .006f;
        for (int i=0; i<particles.size(); i++) {
            Particle q = particles.get(i);
            float d = (float)Math.sqrt(q.x*q.x + q.y*q.y + q.z*q.z) + .001f;
            float pull = cohesion * dt / d;
            q.vx -= q.x * pull;
            q.vy -= q.y * pull;
            q.vz -= q.z * pull;
            q.vx += (-q.z * orbit + fieldBend * .18f) * dt;
            q.vz += q.x * orbit * dt;
            if ((i & 7) == 0) {
                q.vx += randn() * repel;
                q.vy += randn() * repel;
                q.vz += randn() * repel;
            }
            if (q.type == 0) q.vy += .035f * dt;
            if (q.type == 2) q.vy -= .05f * dt;
            q.x += q.vx * dt;
            q.y += q.vy * dt;
            q.z += q.vz * dt;
            q.vx *= .982f;
            q.vy *= .982f;
            q.vz *= .982f;
            q.age += dt;
            q.life -= dt * (hasSound ? .045f : decayRate() * .36f);
        }
        for (int i = particles.size()-1; i>=0; i--) if (particles.get(i).life <= 0) particles.remove(i);
    }

    private void condenseParticles(AudioFrame a, boolean hasSound) {
        if (!hasSound || particles.size() < 24) return;
        float threshold = .42f - a.sustain * .10f;
        int tries = Math.min(5, particles.size() / 65 + (int)(a.onset * 3));
        for (int t=0; t<tries; t++) {
            Particle seed = particles.get(rng.nextInt(particles.size()));
            int nearby = 0; float cx=0, cy=0, cz=0; int bass=0, mid=0, high=0;
            for (int i=0; i<particles.size(); i+=2) {
                Particle q=particles.get(i);
                float dx=q.x-seed.x, dy=q.y-seed.y, dz=q.z-seed.z;
                if (dx*dx+dy*dy+dz*dz < threshold*threshold) {
                    nearby++; cx+=q.x; cy+=q.y; cz+=q.z;
                    if(q.type==0) bass++; else if(q.type==1) mid++; else high++;
                }
            }
            if (nearby > 12 + solids.size()/8) {
                SolidGlyph g = new SolidGlyph();
                g.x = cx/nearby; g.y = cy/nearby; g.z = cz/nearby;
                g.size = (.13f + nearby * .007f + a.bass * .22f) * midiMass;
                g.energy = Math.min(1.5f, a.rms + nearby/62f);
                g.type = bass > mid && bass > high ? 0 : mid >= high ? 1 : 2;
                g.curve = (rng.nextFloat()-.5f) * (.75f + a.mid + Math.abs(fieldBend));
                g.angle = rng.nextFloat() * 6.28f;
                g.ch = proceduralGlyph(g.type, g.energy, rng.nextFloat());
                g.color = proceduralColor(g.type, g.energy);
                g.sigma = .07f + nearby * .0025f + a.rms * .08f;
                g.life = 1f;
                solids.add(g);
                while (solids.size() > maxSolids) solids.remove(0);
            }
        }
    }

    private void erode(float dt, boolean hasSound) {
        float rate = hasSound ? .016f : decayRate();
        for (SolidGlyph g : solids) {
            g.age += dt;
            g.angle += dt * (.08f + g.energy * .08f + fieldBend * .04f);
            g.life -= dt * rate;
            if (persist && g.life < .16f) g.life = .16f;
        }
        for (int i=solids.size()-1; i>=0; i--) if (solids.get(i).life <= 0) solids.remove(i);
    }

    private float decayRate() {
        switch (decayMode) {
            case 0: return 3.8f;
            case 1: return 1.7f;
            case 2: return .85f;
            case 3: return .38f;
            case 4: return .16f;
            default: return .06f;
        }
    }

    private void renderWorld(Canvas c) {
        drawItems.clear();
        for (SolidGlyph g : solids) collectSolid(drawItems, g);
        for (Particle q : particles) collectParticle(drawItems, q);
        Collections.sort(drawItems, (a,b) -> Float.compare(b.depth, a.depth));
        p.setTypeface(Typeface.MONOSPACE);
        p.setTextAlign(Paint.Align.CENTER);
        for (DrawItem d : drawItems) {
            p.setTextSize(d.size);
            p.setColor(d.shadow);
            c.drawText(String.valueOf(d.ch), d.x + 2.0f, d.y + 3.0f, p);
            p.setColor(d.color);
            c.drawText(String.valueOf(d.ch), d.x, d.y, p);
        }
    }

    private void collectParticle(ArrayList<DrawItem> out, Particle q) {
        float[] pr = project(q.x, q.y, q.z);
        if (pr == null) return;
        // Particles are procedural: glyph, scale and brightness are born with the particle, not from a fixed ASCII gradient.
        char ch = q.ch;
        if (((int)(q.age * 18 + q.energy * 7) & 7) == 0) ch = proceduralGlyph(q.type, q.energy, q.age % 1f);
        int alpha = (int)(Math.max(0, Math.min(1, q.life)) * (150 + 90 * Math.min(1f, q.energy)));
        float pulse = .82f + .18f * (float)Math.sin(q.age * 8f + q.spin * 3f);
        out.add(new DrawItem(pr[0], pr[1], pr[2], (7f + 8f * q.scale * pulse) * pr[3], ch, applyAlpha(q.color, alpha), 0x66000000));
    }

    private void collectSolid(ArrayList<DrawItem> out, SolidGlyph g) {
        // Condensed matter is a volumetric Gaussian ellipsoid made from procedural glyph dust.
        // No tube, loop or fixed sculpture style remains here.
        int steps = g.type == 2 ? 26 : 34;
        float sx = g.sigma * (g.type == 0 ? 2.4f : g.type == 1 ? 2.0f : 1.35f);
        float sy = g.sigma * (g.type == 0 ? 1.25f : g.type == 1 ? 1.65f : 1.05f);
        float sz = g.sigma * (g.type == 0 ? 1.65f : g.type == 1 ? 1.2f : 1.85f);
        for (int i=0; i<steps; i++) {
            float gx = pseudoGaussian(i, g.angle + .13f);
            float gy = pseudoGaussian(i, g.angle + 2.21f);
            float gz = pseudoGaussian(i, g.angle + 4.09f);
            float swirl = (float)Math.sin(g.age * .9f + i * .37f + g.curve) * g.size * .08f;
            float x = g.x + gx * sx + (float)Math.cos(g.angle) * swirl;
            float y = g.y + gy * sy;
            float z = g.z + gz * sz + (float)Math.sin(g.angle) * swirl;
            float[] pr = project(x,y,z);
            if (pr == null) continue;
            float dist = Math.min(1.8f, (gx*gx + gy*gy + gz*gz) / 3f);
            float shade = .92f - dist * .28f + .22f * (float)Math.sin(g.angle + i*.31f + g.age);
            int alpha = (int)(Math.max(0, Math.min(1, g.life)) * (180 + 60 * Math.max(0, 1f-dist)));
            char ch = proceduralGlyph(g.type, g.energy * shade, (i * .137f + g.angle) % 1f);
            out.add(new DrawItem(pr[0], pr[1], pr[2], (9f + g.size*13f*(1.2f-dist*.25f)) * pr[3], ch, tint(g.color, shade, alpha), 0x88000000));
        }
    }

    private float[] project(float x, float y, float z) {
        float cy = (float)Math.cos(cameraYaw), sy = (float)Math.sin(cameraYaw);
        float cp = (float)Math.cos(cameraPitch), sp = (float)Math.sin(cameraPitch);
        float x1 = x*cy - z*sy;
        float z1 = x*sy + z*cy;
        float y1 = y*cp - z1*sp;
        float z2 = y*sp + z1*cp + 4.0f / zoom;
        if (z2 <= .1f) return null;
        float scale = Math.min(getWidth(), getHeight()) * .42f / z2;
        float sx = getWidth()*.5f + x1*scale;
        float sy2 = getHeight()*.53f + y1*scale;
        return new float[]{sx, sy2, z2, Math.max(.55f, Math.min(1.8f, 4f/z2))};
    }

    private void drawTerminalBackground(Canvas c) {
        c.drawColor(Color.BLACK);
        p.setStrokeWidth(1);
        p.setColor(0x1019ffcc);
        int gap = 18;
        for (int x=0; x<getWidth(); x+=gap) c.drawLine(x,0,x,getHeight(),p);
        for (int y=0; y<getHeight(); y+=gap) c.drawLine(0,y,getWidth(),y,p);
        p.setColor(0x0600ff99);
        for (int x=0; x<getWidth(); x+=gap*4) c.drawLine(x,0,x,getHeight(),p);
        for (int y=0; y<getHeight(); y+=gap*4) c.drawLine(0,y,getWidth(),y,p);
    }

    private void drawUI(Canvas c) {
        p.setTypeface(Typeface.MONOSPACE);
        p.setTextAlign(Paint.Align.LEFT);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xd8e9f6ef);
        p.setTextSize(30);
        c.drawText("WEMWEM", 40, 58, p);
        p.setTextSize(18);
        p.setColor(0xa4d6d6d6);
        c.drawText("EMPTY UNTIL SOUND · PROCEDURAL GAUSSIAN 3D", 40, 95, p);
        c.drawText(midiStatus, 40, 130, p);
        c.drawText("GAUSSIAN PARTICLES " + particles.size() + " · CLOUDS " + solids.size() + " · RANDOM DNA", 40, 164, p);

        listenRect.set(40, getHeight()-170, 190, getHeight()-118);
        persistRect.set(210, getHeight()-170, 380, getHeight()-118);
        decayRect.set(400, getHeight()-170, 560, getHeight()-118);
        padToggleRect.set(getWidth()-190, getHeight()-170, getWidth()-40, getHeight()-118);
        drawButton(c, listenRect, listening ? "LISTEN ON" : "LISTEN");
        drawButton(c, persistRect, persist ? "PERSIST" : "VANISH");
        drawButton(c, decayRect, "DECAY " + decayLabel());
        drawButton(c, padToggleRect, padOpen ? "PAD CLOSE" : "PAD OPEN");

        drawPiano(c);
        if (padOpen) drawPad(c);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setColor(0xb8ffffff);
        p.setTextSize(18);
        c.drawText("sound births procedural Gaussian clouds · silence dissolves to black", getWidth()-38, getHeight()-32, p);
    }

    private String decayLabel(){ String[] s={"NOW","VERY FAST","FAST","MED","SLOW","GHOST"}; return s[decayMode]; }
    private void drawButton(Canvas c, RectF r, String label){ p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.2f); p.setColor(0x88d6f6ff); c.drawRoundRect(r, 2,2,p); p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(17); p.setColor(0xd8ffffff); c.drawText(label, r.centerX(), r.centerY()+6, p); }

    private void drawPiano(Canvas c) {
        float y = getHeight() - 105;
        float w = getWidth() - 80;
        float key = w / 13f;
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1); p.setColor(0x4c6dffe8);
        for(int i=0;i<13;i++) c.drawRect(40+i*key,y,40+(i+1)*key,y+55,p);
        p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(14); p.setColor(0x88ffffff);
        String[] k={"A","W","S","E","D","F","T","G","Y","H","U","J","K"};
        for(int i=0;i<13;i++) c.drawText(k[i],40+i*key+key/2,y+78,p);
    }

    private void drawPad(Canvas c) {
        float w = Math.min(360, getWidth()-80);
        float h = 156;
        float left = getWidth() - w - 40;
        float top = getHeight() - 350;
        String[] labels = {"SUB", "PULSE", "GRAIN", "DRONE"};
        for (int i=0; i<4; i++) {
            float x = left + (i%2)*(w/2f + 8);
            float y = top + (i/2)*(h/2f + 8);
            padRects[i].set(x, y, x + w/2f - 8, y + h/2f - 8);
            p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.5f); p.setColor(0xb0d6f6ff); c.drawRoundRect(padRects[i], 8, 8, p);
            p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(19); p.setColor(0xe8ffffff); c.drawText(labels[i], padRects[i].centerX(), padRects[i].centerY()+7, p);
        }
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getAction()==MotionEvent.ACTION_DOWN) {
            lastX=e.getX(); lastY=e.getY(); lastDist=0;
            if (listenRect.contains(lastX,lastY)) { listening=!listening; if(listening) startListening(); else pause(); return true; }
            if (persistRect.contains(lastX,lastY)) { persist=!persist; return true; }
            if (decayRect.contains(lastX,lastY)) { decayMode=(decayMode+1)%6; return true; }
            if (padToggleRect.contains(lastX,lastY)) { padOpen=!padOpen; return true; }
            if (padOpen) for (int i=0;i<4;i++) if (padRects[i].contains(lastX,lastY)) { padHit(i, 1f); return true; }
            handlePiano(e.getX(), e.getY());
        }
        if (e.getPointerCount()==2) {
            float dx=e.getX(0)-e.getX(1), dy=e.getY(0)-e.getY(1);
            float d=(float)Math.sqrt(dx*dx+dy*dy);
            if(lastDist>0) zoom *= Math.max(.8f, Math.min(1.2f, d/lastDist));
            zoom=Math.max(.35f, Math.min(4f, zoom)); lastDist=d; return true;
        }
        if (e.getAction()==MotionEvent.ACTION_MOVE && e.getPointerCount()==1) {
            cameraYaw += (e.getX()-lastX)*.005f; cameraPitch += (e.getY()-lastY)*.0035f; cameraPitch=Math.max(-1.2f, Math.min(1.2f,cameraPitch)); lastX=e.getX(); lastY=e.getY(); return true;
        }
        return true;
    }

    private void handlePiano(float x, float y) {
        float py = getHeight()-105;
        if (y < py || y > py+85) return;
        float key=(getWidth()-80)/13f;
        int idx=(int)((x-40)/key);
        if(idx>=0 && idx<13) midiNote(60+idx, 115);
    }

    private float randn(){ return (rng.nextFloat()+rng.nextFloat()+rng.nextFloat()-1.5f); }
    private float randGaussian(){
        float u1 = Math.max(0.0001f, rng.nextFloat());
        float u2 = rng.nextFloat();
        return (float)(Math.sqrt(-2.0 * Math.log(u1)) * Math.cos(2.0 * Math.PI * u2)) * .42f;
    }
    private float pseudoGaussian(int i, float salt){
        float a = (float)Math.sin(i * 12.9898f + salt * 78.233f) * 43758.5453f;
        float b = (float)Math.sin(i * 93.989f + salt * 19.17f) * 24634.6345f;
        float c = (float)Math.sin(i * 41.123f + salt * 61.73f) * 13531.915f;
        return ((fract(a) + fract(b) + fract(c)) - 1.5f) * .9f;
    }
    private float fract(float v){ return v - (float)Math.floor(v); }
    private char proceduralGlyph(int type, float energy, float salt){
        int poolIndex;
        float r = fract((float)Math.sin((cloudSeed + type * 97) * 0.017f + salt * 11.13f + energy * 3.7f) * 9137.11f);
        if (energy < .22f) poolIndex = 0;
        else if (energy < .55f) poolIndex = (r < .65f ? 1 : 0);
        else if (energy < .9f) poolIndex = (r < .5f ? 2 : 1);
        else poolIndex = (r < .72f ? 2 : 3);
        char[] pool = glyphPools[Math.max(0, Math.min(glyphPools.length-1, poolIndex))];
        int idx = Math.abs((int)((r + salt + energy) * 10000)) % pool.length;
        return pool[idx];
    }
    private int proceduralColor(int type, float energy){
        int base = Math.abs((cloudSeed + type * 31 + (int)(energy * 1000) + rng.nextInt(99))) % palette.length;
        return palette[base];
    }
    private int applyAlpha(int color, int a){ return (Math.max(0,Math.min(255,a))<<24) | (color & 0x00ffffff); }
    private int tint(int color, float f, int a){ int r=(int)(((color>>16)&255)*f); int g=(int)(((color>>8)&255)*f); int b=(int)((color&255)*f); return (Math.max(0,Math.min(255,a))<<24)|(Math.min(255,r)<<16)|(Math.min(255,g)<<8)|Math.min(255,b); }

    interface AudioSink { void onFrame(AudioFrame f); }
    static class AudioFrame { float rms,bass,mid,high,onset,sustain,silence; AudioFrame(){} AudioFrame(float r,float b,float m,float h,float o,float s,float si){rms=r; bass=b; mid=m; high=h; onset=o; sustain=s; silence=si;} }
    static class Particle { float x,y,z,vx,vy,vz,energy,mass,age,life,scale,sigma,spin; int type,color; char ch; }
    static class SolidGlyph { float x,y,z,size,energy,life,angle,curve,age,sigma; int type,color; char ch; }
    static class DrawItem { float x,y,depth,size; char ch; int color,shadow; DrawItem(float x,float y,float d,float s,char c,int col,int sh){this.x=x;this.y=y;depth=d;size=s;ch=c;color=col;shadow=sh;} }

    static class SynthEngine {
        private final int sr = 22050;
        void playMidiNote(int note, float velocity, int ms) { playTone((float)(440.0 * Math.pow(2, (note - 69) / 12.0)), velocity, ms, 0); }
        void releaseNote(int note) { }
        void playPad(int pad, float velocity) {
            float[] freqs = {55f, 110f, 880f, 146.83f};
            int[] waves = {1, 0, 2, 3};
            playTone(freqs[Math.max(0, Math.min(3, pad))], velocity, pad==3 ? 420 : 180, waves[Math.max(0, Math.min(3, pad))]);
        }
        private void playTone(float freq, float velocity, int ms, int wave) {
            new Thread(() -> {
                int samples = Math.max(256, sr * ms / 1000);
                short[] data = new short[samples];
                for (int i=0; i<samples; i++) {
                    double t = i / (double)sr;
                    double env = Math.sin(Math.PI * i / samples);
                    double phase = 2.0 * Math.PI * freq * t;
                    double s;
                    if (wave == 1) s = Math.sin(phase) * .72 + Math.sin(phase*.5) * .28;
                    else if (wave == 2) s = ((i * freq / sr) % 1.0) * 2.0 - 1.0;
                    else if (wave == 3) s = Math.sin(phase) * .55 + Math.sin(phase*1.5) * .25 + Math.sin(phase*.25) * .2;
                    else s = Math.sin(phase);
                    data[i] = (short)(s * env * Math.min(1f, velocity) * 9000);
                }
                AudioTrack track = new AudioTrack(AudioManager.STREAM_MUSIC, sr, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT, data.length * 2, AudioTrack.MODE_STATIC);
                try { track.write(data, 0, data.length); track.play(); SystemClock.sleep(ms + 40); } catch(Exception ignored) {} finally { try { track.release(); } catch(Exception ignored) {} }
            }, "wemwem-synth").start();
        }
    }

    static class AudioThread extends Thread {
        private final AudioSink sink; private boolean running=true; private float prevRms=0, sustain=0;
        AudioThread(AudioSink s){sink=s;}
        public void close(){running=false; interrupt();}
        @Override public void run(){
            int sr=22050; int n=512;
            int min=AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            AudioRecord rec=new AudioRecord(MediaRecorder.AudioSource.MIC, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min,n*4));
            short[] buf=new short[n];
            try { rec.startRecording(); } catch(Exception e){ return; }
            while(running){
                int read=rec.read(buf,0,n); if(read<=0) continue;
                double sum=0,b=0,m=0,h=0; int zc=0; short last=0;
                for(int i=0;i<read;i++){
                    float v=buf[i]/32768f; float av=Math.abs(v); sum+=v*v;
                    if(i>0 && ((v>0)!=(last>0))) zc++; last=buf[i];
                    if(i%12<4) b+=av; else if(i%12<8) m+=av; else h+=av;
                }
                float rms=(float)Math.sqrt(sum/read);
                float onset=Math.max(0, rms-prevRms)*8f; prevRms=prevRms*.84f+rms*.16f;
                if(rms>.04f) sustain=Math.min(1f,sustain+.028f); else sustain=Math.max(0,sustain-.05f);
                AudioFrame f=new AudioFrame();
                f.rms=clamp(rms*3.2f); f.bass=clamp((float)(b/read)*3.8f); f.mid=clamp((float)(m/read)*3.6f); f.high=clamp((float)(h/read)*3.4f + zc/900f); f.onset=clamp(onset); f.sustain=sustain; f.silence=1f-f.rms;
                sink.onFrame(f);
            }
            try{rec.stop(); rec.release();}catch(Exception ignored){}
        }
        private float clamp(float v){return Math.max(0,Math.min(1,v));}
    }
}
