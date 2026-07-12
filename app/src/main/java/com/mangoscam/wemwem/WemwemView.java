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
    private final Random rng = new Random(37106);
    private final ArrayList<Particle> particles = new ArrayList<>();
    private final ArrayList<SolidGlyph> solids = new ArrayList<>();
    private final char[] ascii = new char[]{'.', ':', '-', '=', '+', '*', '#', '%', '@'};
    private final int[] palette = new int[]{0xff9dfacb, 0xffd6f6ff, 0xffff5f5f, 0xff7f9cff, 0xfff4df75, 0xffffffff};

    private AudioThread audioThread;
    private volatile AudioFrame latest = new AudioFrame();
    private String midiStatus = "MIDI · WAITING";
    private boolean listening = false;
    private boolean persist = false;
    private int decayMode = 3; // 0 instant, 5 very slow
    private float cameraYaw = 0.15f;
    private float cameraPitch = -0.25f;
    private float zoom = 1.0f;
    private float lastX, lastY;
    private float lastDist = 0;
    private long lastFrame = SystemClock.uptimeMillis();
    private float soundMemory = 0;
    private float uiAlpha = 1f;
    private RectF listenRect = new RectF(), persistRect = new RectF(), decayRect = new RectF();

    public WemwemView(Context c) {
        super(c);
        setFocusable(true);
        setFocusableInTouchMode(true);
        p.setTypeface(Typeface.MONOSPACE);
        startListening();
    }

    public void resume() { lastFrame = SystemClock.uptimeMillis(); startListening(); invalidate(); }
    public void pause() { if (audioThread != null) audioThread.close(); audioThread = null; }

    public void setMidiStatus(String s) { midiStatus = s; }

    public void startListening() {
        if (audioThread != null) return;
        if (getContext().checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return;
        audioThread = new AudioThread(frame -> latest = frame);
        audioThread.start();
        listening = true;
    }

    public void manualBurst(float power) {
        spawnBurst(new AudioFrame(power, power * .8f, power * .7f, power * .5f, 1f, 0f, 0f), getWidth() * .5f, getHeight() * .5f);
    }

    public void prune() {
        for (int i = particles.size() - 1; i >= 0; i -= 2) particles.remove(i);
        for (SolidGlyph g : solids) g.life *= .78f;
    }

    public void midiNote(int note, int velocity) {
        float v = Math.max(0.05f, velocity / 127f);
        AudioFrame f = new AudioFrame(v, v * ((note < 55) ? 1f : .25f), v * .7f, v * ((note > 72) ? 1f : .35f), 1f, .2f, 0f);
        spawnBurst(f, getWidth() * (.25f + ((note % 12) / 12f) * .5f), getHeight() * .55f);
        latest = f;
    }

    @Override protected void onDraw(Canvas canvas) {
        long now = SystemClock.uptimeMillis();
        float dt = Math.min(.05f, (now - lastFrame) / 1000f);
        lastFrame = now;

        drawTerminalBackground(canvas);
        updateWorld(dt);
        renderWorld(canvas);
        drawUI(canvas);
        invalidate();
    }

    private void updateWorld(float dt) {
        AudioFrame a = latest;
        float energy = Math.max(a.rms, Math.max(a.bass, Math.max(a.mid, a.high)));
        boolean hasSound = listening && energy > 0.035f;
        soundMemory = soundMemory * .94f + energy * .06f;

        if (hasSound) spawnFromAudio(a, dt);
        simulateParticles(a, dt, hasSound);
        condenseParticles(a, hasSound);
        erode(dt, hasSound);
        cameraYaw += dt * (0.04f + soundMemory * 0.16f);
    }

    private void spawnFromAudio(AudioFrame a, float dt) {
        int births = Math.min(36, (int)(2 + a.rms * 40 + a.onset * 26));
        for (int i = 0; i < births; i++) {
            int type = chooseType(a);
            Particle q = new Particle();
            float spread = 1.7f + a.mid * 1.5f + a.high;
            q.x = randn() * .10f;
            q.y = randn() * .10f;
            q.z = randn() * .10f;
            float ang = (float)(rng.nextFloat() * Math.PI * 2);
            float elev = (rng.nextFloat() - .5f) * .9f;
            float speed = .12f + a.rms * .9f + a.onset * 1.3f;
            q.vx = (float)Math.cos(ang) * speed * spread;
            q.vy = elev * speed + (type == 2 ? -.25f : 0);
            q.vz = (float)Math.sin(ang) * speed * spread;
            q.energy = .35f + a.rms + rng.nextFloat() * .35f;
            q.mass = type == 0 ? 1.6f : type == 1 ? 1.0f : .55f;
            q.type = type;
            q.life = 1f;
            q.age = 0f;
            particles.add(q);
        }
        while (particles.size() > 900) particles.remove(0);
    }

    private void spawnBurst(AudioFrame a, float sx, float sy) {
        for (int i=0; i<90; i++) {
            Particle q = new Particle();
            float rx = ((sx / Math.max(1, getWidth())) - .5f) * 2f;
            float ry = ((sy / Math.max(1, getHeight())) - .5f) * -2f;
            q.x = rx * 1.2f + randn() * .1f;
            q.y = ry * 1.6f + randn() * .1f;
            q.z = randn() * .2f;
            float ang = (float)(rng.nextFloat() * Math.PI * 2);
            float speed = .5f + rng.nextFloat() * 1.8f;
            q.vx = (float)Math.cos(ang) * speed;
            q.vy = (rng.nextFloat() - .5f) * speed;
            q.vz = (float)Math.sin(ang) * speed;
            q.energy = .8f + a.rms;
            q.mass = .7f + rng.nextFloat();
            q.type = chooseType(a);
            q.life = 1f;
            particles.add(q);
        }
    }

    private int chooseType(AudioFrame a) {
        float total = a.bass + a.mid + a.high + .001f;
        float r = rng.nextFloat() * total;
        if (r < a.bass) return 0;
        if (r < a.bass + a.mid) return 1;
        return 2;
    }

    private void simulateParticles(AudioFrame a, float dt, boolean hasSound) {
        float cohesion = .18f + a.sustain * .45f;
        float orbit = .35f + a.high * .7f;
        for (Particle q : particles) {
            float d = (float)Math.sqrt(q.x*q.x + q.y*q.y + q.z*q.z) + .001f;
            float pull = cohesion * dt / d;
            q.vx -= q.x * pull;
            q.vy -= q.y * pull;
            q.vz -= q.z * pull;
            q.vx += -q.z * orbit * dt * .2f;
            q.vz += q.x * orbit * dt * .2f;
            if (q.type == 0) q.vy += .05f * dt;      // bass sinks into mass
            if (q.type == 2) q.vy -= .07f * dt;      // highs climb into accents
            q.x += q.vx * dt;
            q.y += q.vy * dt;
            q.z += q.vz * dt;
            q.vx *= .985f;
            q.vy *= .985f;
            q.vz *= .985f;
            q.age += dt;
            q.life -= dt * (hasSound ? .035f : decayRate() * .34f);
        }
        for (int i = particles.size()-1; i>=0; i--) if (particles.get(i).life <= 0) particles.remove(i);
    }

    private void condenseParticles(AudioFrame a, boolean hasSound) {
        if (!hasSound) return;
        float threshold = .55f - a.sustain * .18f;
        if (particles.size() < 24) return;
        int tries = Math.min(8, particles.size() / 45 + (int)(a.onset * 5));
        for (int t=0; t<tries; t++) {
            Particle seed = particles.get(rng.nextInt(particles.size()));
            int nearby = 0;
            float cx=0, cy=0, cz=0;
            int bass=0, mid=0, high=0;
            for (Particle q: particles) {
                float dx=q.x-seed.x, dy=q.y-seed.y, dz=q.z-seed.z;
                if (dx*dx+dy*dy+dz*dz < threshold*threshold) {
                    nearby++;
                    cx+=q.x; cy+=q.y; cz+=q.z;
                    if(q.type==0) bass++; else if(q.type==1) mid++; else high++;
                }
            }
            if (nearby > 16 + solids.size()/4) {
                SolidGlyph g = new SolidGlyph();
                g.x = cx/nearby; g.y = cy/nearby; g.z = cz/nearby;
                g.size = .16f + nearby * .006f + a.bass * .28f;
                g.energy = Math.min(1.5f, a.rms + nearby/80f);
                g.type = bass > mid && bass > high ? 0 : mid >= high ? 1 : 2;
                g.curve = (rng.nextFloat()-.5f) * (1.2f + a.mid);
                g.angle = rng.nextFloat() * 6.28f;
                g.life = 1f;
                solids.add(g);
                if (solids.size() > 180) solids.remove(0);
            }
        }
    }

    private void erode(float dt, boolean hasSound) {
        float rate = hasSound ? .012f : decayRate();
        for (SolidGlyph g : solids) {
            g.age += dt;
            g.angle += dt * (.12f + g.energy * .1f);
            g.life -= dt * rate;
            if (persist && g.life < .18f) g.life = .18f;
        }
        for (int i=solids.size()-1; i>=0; i--) if (solids.get(i).life <= 0) solids.remove(i);
        if (!persist && !hasSound && latest.rms < .02f && particles.size()==0) {
            // Absolute black is allowed. No seed. No hidden sculpture.
        }
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
        ArrayList<DrawItem> list = new ArrayList<>();
        for (SolidGlyph g : solids) collectSolid(list, g);
        for (Particle q : particles) collectParticle(list, q);
        Collections.sort(list, (a,b) -> Float.compare(b.depth, a.depth));
        p.setTypeface(Typeface.MONOSPACE);
        p.setTextAlign(Paint.Align.CENTER);
        for (DrawItem d : list) {
            p.setTextSize(d.size);
            p.setColor(d.shadow);
            c.drawText(String.valueOf(d.ch), d.x + 2.5f, d.y + 3.5f, p);
            p.setColor(d.color);
            c.drawText(String.valueOf(d.ch), d.x, d.y, p);
        }
    }

    private void collectParticle(ArrayList<DrawItem> out, Particle q) {
        float[] pr = project(q.x, q.y, q.z);
        if (pr == null) return;
        char ch = ascii[Math.max(0, Math.min(ascii.length-1, (int)(q.energy * q.life * 7)))];
        int col = palette[q.type == 0 ? 4 : q.type == 1 ? 0 : 3];
        int alpha = (int)(Math.max(0, Math.min(1, q.life)) * 210);
        out.add(new DrawItem(pr[0], pr[1], pr[2], 12f * pr[3], ch, applyAlpha(col, alpha), 0x88000000));
    }

    private void collectSolid(ArrayList<DrawItem> out, SolidGlyph g) {
        int steps = g.type == 2 ? 10 : 18;
        for (int i=0; i<steps; i++) {
            float t = (i/(float)(steps-1)-.5f);
            float wave = (float)Math.sin(t * Math.PI * 2 + g.angle) * g.curve * .18f;
            float x = g.x + (float)Math.cos(g.angle) * t * g.size * 2.6f + wave;
            float y = g.y + (g.type==0 ? (float)Math.sin(t*Math.PI)*g.size*.8f : t * g.size * .8f);
            float z = g.z + (float)Math.sin(g.angle) * t * g.size * 2.6f - wave*.4f;
            float[] pr = project(x,y,z);
            if (pr == null) continue;
            float shade = .45f + .55f * (float)Math.max(0, Math.sin(g.angle + i*.3f));
            int base = palette[g.type==0 ? 5 : g.type==1 ? 0 : 2];
            int alpha = (int)(Math.max(0, Math.min(1, g.life)) * 245);
            char ch = ascii[Math.max(1, Math.min(ascii.length-1, (int)((g.life*shade) * 8)))];
            out.add(new DrawItem(pr[0], pr[1], pr[2], (16f + g.size*18f) * pr[3], ch, tint(base, shade, alpha), 0xaa000000));
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
        float sy2 = getHeight()*.55f + y1*scale;
        return new float[]{sx, sy2, z2, Math.max(.55f, Math.min(1.8f, 4f/z2))};
    }

    private void drawTerminalBackground(Canvas c) {
        c.drawColor(Color.BLACK);
        p.setStrokeWidth(1);
        p.setColor(0x1519ffcc);
        int gap = 18;
        for (int x=0; x<getWidth(); x+=gap) c.drawLine(x,0,x,getHeight(),p);
        for (int y=0; y<getHeight(); y+=gap) c.drawLine(0,y,getWidth(),y,p);
        p.setColor(0x0800ff99);
        for (int x=0; x<getWidth(); x+=gap*4) c.drawLine(x,0,x,getHeight(),p);
        for (int y=0; y<getHeight(); y+=gap*4) c.drawLine(0,y,getWidth(),y,p);
    }

    private void drawUI(Canvas c) {
        p.setTypeface(Typeface.MONOSPACE);
        p.setTextAlign(Paint.Align.LEFT);
        p.setColor(0xd8e9f6ef);
        p.setTextSize(30);
        c.drawText("WEMWEM", 40, 58, p);
        p.setTextSize(18);
        p.setColor(0xa4d6d6d6);
        c.drawText("NO SEED · SOUND BUILDS EVERYTHING", 40, 95, p);
        c.drawText(midiStatus, 40, 130, p);
        c.drawText("PARTICLES " + particles.size() + " · MATTER " + solids.size(), 40, 164, p);

        listenRect.set(40, getHeight()-170, 190, getHeight()-118);
        persistRect.set(210, getHeight()-170, 380, getHeight()-118);
        decayRect.set(400, getHeight()-170, 560, getHeight()-118);
        drawButton(c, listenRect, listening ? "LISTEN ON" : "LISTEN");
        drawButton(c, persistRect, persist ? "PERSIST" : "VANISH");
        drawButton(c, decayRect, "DECAY " + decayLabel());

        drawPiano(c);
        p.setTextAlign(Paint.Align.RIGHT);
        p.setColor(0xb8ffffff);
        p.setTextSize(18);
        c.drawText("sound births ASCII particles · silence erodes to black", getWidth()-38, getHeight()-32, p);
    }

    private String decayLabel(){ String[] s={"NOW","VERY FAST","FAST","MED","SLOW","GHOST"}; return s[decayMode]; }
    private void drawButton(Canvas c, RectF r, String label){ p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1.2f); p.setColor(0x88d6f6ff); c.drawRoundRect(r, 2,2,p); p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(17); p.setColor(0xd8ffffff); c.drawText(label, r.centerX(), r.centerY()+6, p); }

    private void drawPiano(Canvas c) {
        float y = getHeight() - 105;
        float w = getWidth() - 80;
        float key = w / 13f;
        p.setStyle(Paint.Style.STROKE); p.setStrokeWidth(1); p.setColor(0x556dffe8);
        for(int i=0;i<13;i++) c.drawRect(40+i*key,y,40+(i+1)*key,y+55,p);
        p.setStyle(Paint.Style.FILL); p.setTextAlign(Paint.Align.CENTER); p.setTextSize(14); p.setColor(0x88ffffff);
        String[] k={"A","W","S","E","D","F","T","G","Y","H","U","J","K"};
        for(int i=0;i<13;i++) c.drawText(k[i],40+i*key+key/2,y+78,p);
    }

    @Override public boolean onTouchEvent(android.view.MotionEvent e) {
        if (e.getAction()==MotionEvent.ACTION_DOWN) {
            lastX=e.getX(); lastY=e.getY(); lastDist=0;
            if (listenRect.contains(lastX,lastY)) { listening=!listening; if(listening) startListening(); else pause(); return true; }
            if (persistRect.contains(lastX,lastY)) { persist=!persist; return true; }
            if (decayRect.contains(lastX,lastY)) { decayMode=(decayMode+1)%6; return true; }
            handlePiano(e.getX(), e.getY());
        }
        if (e.getPointerCount()==2) {
            float dx=e.getX(0)-e.getX(1), dy=e.getY(0)-e.getY(1);
            float d=(float)Math.sqrt(dx*dx+dy*dy);
            if(lastDist>0) zoom *= Math.max(.8f, Math.min(1.2f, d/lastDist));
            zoom=Math.max(.35f, Math.min(4f, zoom)); lastDist=d; return true;
        }
        if (e.getAction()==MotionEvent.ACTION_MOVE && e.getPointerCount()==1) {
            cameraYaw += (e.getX()-lastX)*.006f; cameraPitch += (e.getY()-lastY)*.004f; cameraPitch=Math.max(-1.2f, Math.min(1.2f,cameraPitch)); lastX=e.getX(); lastY=e.getY(); return true;
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
    private int applyAlpha(int color, int a){ return (a<<24) | (color & 0x00ffffff); }
    private int tint(int color, float f, int a){ int r=(int)(((color>>16)&255)*f); int g=(int)(((color>>8)&255)*f); int b=(int)((color&255)*f); return (a<<24)|(Math.min(255,r)<<16)|(Math.min(255,g)<<8)|Math.min(255,b); }

    interface AudioSink { void onFrame(AudioFrame f); }
    static class AudioFrame { float rms,bass,mid,high,onset,sustain,silence; AudioFrame(){} AudioFrame(float r,float b,float m,float h,float o,float s,float si){rms=r; bass=b; mid=m; high=h; onset=o; sustain=s; silence=si;} }
    static class Particle { float x,y,z,vx,vy,vz,energy,mass,age,life; int type; }
    static class SolidGlyph { float x,y,z,size,energy,life,angle,curve,age; int type; }
    static class DrawItem { float x,y,depth,size; char ch; int color,shadow; DrawItem(float x,float y,float d,float s,char c,int col,int sh){this.x=x;this.y=y;depth=d;size=s;ch=c;color=col;shadow=sh;} }

    static class AudioThread extends Thread {
        private final AudioSink sink; private boolean running=true; private float prevRms=0, sustain=0;
        AudioThread(AudioSink s){sink=s;}
        public void close(){running=false; interrupt();}
        @Override public void run(){
            int sr=44100; int n=1024;
            int min=AudioRecord.getMinBufferSize(sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT);
            AudioRecord rec=new AudioRecord(MediaRecorder.AudioSource.MIC, sr, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, Math.max(min,n*4));
            short[] buf=new short[n];
            try { rec.startRecording(); } catch(Exception e){ return; }
            while(running){
                int read=rec.read(buf,0,n); if(read<=0) continue;
                double sum=0,b=0,m=0,h=0;
                int zc=0; short last=0;
                for(int i=0;i<read;i++){
                    float v=buf[i]/32768f; sum+=v*v;
                    if(i>0 && ((v>0)!=(last>0))) zc++; last=buf[i];
                    if(i%12<4) b+=Math.abs(v); else if(i%12<8) m+=Math.abs(v); else h+=Math.abs(v);
                }
                float rms=(float)Math.sqrt(sum/read);
                float onset=Math.max(0, rms-prevRms)*8f; prevRms=prevRms*.8f+rms*.2f;
                if(rms>.04f) sustain=Math.min(1f,sustain+.035f); else sustain=Math.max(0,sustain-.055f);
                AudioFrame f=new AudioFrame();
                f.rms=clamp(rms*3.2f); f.bass=clamp((float)(b/read)*3.8f); f.mid=clamp((float)(m/read)*3.6f); f.high=clamp((float)(h/read)*3.4f + zc/900f); f.onset=clamp(onset); f.sustain=sustain; f.silence=1f-f.rms;
                sink.onFrame(f);
            }
            try{rec.stop(); rec.release();}catch(Exception ignored){}
        }
        private float clamp(float v){return Math.max(0,Math.min(1,v));}
    }
}
