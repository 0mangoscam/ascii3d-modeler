package com.mangoscam.sculpturegarden;

import android.content.Context;
import android.graphics.BlurMaskFilter;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
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

    private Sculpture current;
    private final List<Sculpture> garden = new ArrayList<>();
    private final List<Sculpture> descendants = new ArrayList<>();
    private boolean choosingDescendant = false;
    private boolean museumMode = false;
    private boolean relaxMode = false;
    private boolean running = true;
    private boolean splitDuringPinch = false;

    private float orbitX = -0.34f;
    private float orbitY = 0.72f;
    private float zoom = 1.0f;
    private float targetZoom = 1.0f;
    private float lastDragDistance = 0f;
    private long birth = System.currentTimeMillis();
    private int forestIndex = 1;

    public SculptureGardenView(Context context) {
        super(context);
        setFocusable(true);
        setFocusableInTouchMode(true);
        if (Build.VERSION.SDK_INT >= 11) {
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
        }

        textPaint.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        textPaint.setLetterSpacing(0.08f);

        current = Sculpture.seed();
        garden.add(current);

        gestures = new GestureDetector(context, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDown(MotionEvent e) {
                lastDragDistance = 0f;
                return true;
            }

            @Override
            public void onLongPress(MotionEvent e) {
                if (!choosingDescendant) {
                    increaseMass();
                }
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                relaxMode = !relaxMode;
                pulse("RELAX MODE");
                invalidate();
                return true;
            }

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                handleTap(e.getX(), e.getY());
                return true;
            }

            @Override
            public boolean onFling(MotionEvent e1, MotionEvent e2, float velocityX, float velocityY) {
                if (!choosingDescendant && Math.abs(velocityX) + Math.abs(velocityY) > 900f) {
                    growNewBranch();
                    return true;
                }
                return false;
            }

            @Override
            public boolean onScroll(MotionEvent e1, MotionEvent e2, float distanceX, float distanceY) {
                if (!choosingDescendant) {
                    orbitY -= distanceX * 0.006f;
                    orbitX -= distanceY * 0.004f;
                    orbitX = clamp(orbitX, -1.25f, 1.25f);
                    lastDragDistance += Math.abs(distanceX) + Math.abs(distanceY);
                    current.dna.twist += distanceX * 0.0009f;
                    if (lastDragDistance > 120f) {
                        current.growBranch(0.55f);
                        lastDragDistance = 0f;
                    }
                    invalidate();
                    return true;
                }
                return false;
            }
        });

        scaleGesture = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScaleBegin(ScaleGestureDetector detector) {
                splitDuringPinch = false;
                return true;
            }

            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                targetZoom *= detector.getScaleFactor();
                targetZoom = clamp(targetZoom, 0.45f, 2.8f);
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

    public void pause() {
        running = false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
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

        if (evolveRect.contains(x, y)) {
            evolve();
            return;
        }
        if (museumRect.contains(x, y)) {
            museumMode = !museumMode;
            pulse(museumMode ? "WHITE MUSEUM" : "VOID GALLERY");
            invalidate();
            return;
        }
        if (relaxRect.contains(x, y)) {
            relaxMode = !relaxMode;
            pulse(relaxMode ? "SILENCE" : "GARDEN");
            invalidate();
        }
    }

    private int hitDescendant(float x, float y) {
        if (descendants.isEmpty()) return -1;
        float w = getWidth();
        float h = getHeight();
        float rowY = h * 0.54f;
        for (int i = 0; i < descendants.size(); i++) {
            float cx = w * (0.14f + i * 0.18f);
            RectF r = new RectF(cx - w * 0.075f, rowY - h * 0.14f, cx + w * 0.075f, rowY + h * 0.16f);
            if (r.contains(x, y)) return i;
        }
        return -1;
    }

    public void growNewBranch() {
        if (choosingDescendant) return;
        current.growBranch(1.0f);
        current.dna.branchFrequency = clamp(current.dna.branchFrequency + 0.025f, 0.08f, 0.94f);
        pulse("BRANCH AWAKENED");
        invalidate();
    }

    public void collapseBranch() {
        if (choosingDescendant) return;
        current.collapseBranch();
        current.dna.density = clamp(current.dna.density - 0.035f, 0.12f, 0.92f);
        pulse("A LIMB RETURNED");
        invalidate();
    }

    public void mutateFromShake() {
        if (choosingDescendant) return;
        current = current.createChild(1, 0.16f);
        current.generation++;
        garden.add(current);
        pulse("MUTATION");
        invalidate();
    }

    private void increaseMass() {
        current.dna.thickness = clamp(current.dna.thickness + 0.07f, 0.08f, 1.0f);
        current.dna.density = clamp(current.dna.density + 0.04f, 0.12f, 0.92f);
        current.rebuild();
        pulse("MASS INCREASED");
        invalidate();
    }

    private void splitStructure() {
        current.dna.branchFrequency = clamp(current.dna.branchFrequency + 0.055f, 0.08f, 0.94f);
        current.dna.balance = clamp(current.dna.balance + 0.025f, 0.1f, 0.95f);
        current.growBranch(0.75f);
        pulse("STRUCTURE SPLIT");
    }

    private String pulseText = "THE SEED";
    private long pulseAt = System.currentTimeMillis();

    private void pulse(String text) {
        pulseText = text;
        pulseAt = System.currentTimeMillis();
    }

    private void evolve() {
        descendants.clear();
        for (int i = 0; i < 5; i++) {
            descendants.add(current.createChild(i + 1, 0.105f));
        }
        choosingDescendant = true;
        pulse("CHOOSE THE SURVIVOR");
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        if (w <= 0 || h <= 0) return;

        float t = (System.currentTimeMillis() - birth) / 1000f;
        zoom += (targetZoom - zoom) * 0.08f;
        if (relaxMode) {
            orbitY += 0.0014f;
            orbitX += Math.sin(t * 0.19f) * 0.0007f;
        }

        drawAtmosphere(canvas, w, h, t);
        if (choosingDescendant) {
            drawDescendants(canvas, w, h, t);
        } else {
            drawGardenGhosts(canvas, w, h, t);
            drawSculpture(canvas, current, w * 0.50f, h * 0.51f, Math.min(w, h) * 0.34f * zoom, 1.0f, orbitX, orbitY, t, false);
            drawSeedGlow(canvas, w, h, t);
        }
        if (!relaxMode) {
            drawInterface(canvas, w, h, t);
        } else {
            drawRelaxWhisper(canvas, w, h, t);
        }

        if (running) postInvalidateOnAnimation();
    }

    private void drawAtmosphere(Canvas canvas, float w, float h, float t) {
        int top = museumMode ? Color.rgb(235, 234, 229) : Color.rgb(3, 4, 5);
        int mid = museumMode ? Color.rgb(214, 213, 207) : Color.rgb(11, 12, 14);
        int bottom = museumMode ? Color.rgb(193, 190, 181) : Color.rgb(2, 2, 3);
        paint.setShader(new LinearGradient(0, 0, 0, h, new int[]{top, mid, bottom}, new float[]{0f, 0.55f, 1f}, Shader.TileMode.CLAMP));
        canvas.drawRect(0, 0, w, h, paint);
        paint.setShader(null);

        float horizon = h * 0.66f;
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(museumMode ? Color.argb(38, 20, 20, 18) : Color.argb(70, 255, 255, 255));
        paint.setStrokeWidth(1f);
        canvas.drawLine(w * 0.08f, horizon, w * 0.92f, horizon, paint);

        paint.setColor(museumMode ? Color.argb(30, 0, 0, 0) : Color.argb(22, 255, 255, 255));
        for (int i = 1; i < 7; i++) {
            float y = horizon + i * i * h * 0.0068f;
            canvas.drawLine(w * 0.14f, y, w * 0.86f, y, paint);
        }

        paint.setColor(museumMode ? Color.argb(26, 0, 0, 0) : Color.argb(18, 255, 255, 255));
        Path left = new Path();
        left.moveTo(0, h);
        left.lineTo(w * 0.15f, horizon);
        left.lineTo(w * 0.25f, horizon);
        left.lineTo(w * 0.05f, h);
        left.close();
        canvas.drawPath(left, paint);
        Path right = new Path();
        right.moveTo(w, h);
        right.lineTo(w * 0.85f, horizon);
        right.lineTo(w * 0.75f, horizon);
        right.lineTo(w * 0.95f, h);
        right.close();
        canvas.drawPath(right, paint);

        float cx = w * 0.5f + (float)Math.sin(t * 0.11f) * w * 0.1f;
        float cy = h * 0.27f;
        paint.setShader(new RadialGradient(cx, cy, Math.max(w, h) * 0.65f,
                museumMode ? Color.argb(55, 255, 255, 255) : Color.argb(68, 210, 220, 220),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, Math.max(w, h) * 0.65f, paint);
        paint.setShader(null);
    }

    private void drawSeedGlow(Canvas canvas, float w, float h, float t) {
        if (current.branches.size() > 8) return;
        float cx = w * 0.5f;
        float cy = h * 0.51f;
        float r = Math.min(w, h) * (0.027f + 0.004f * (float)Math.sin(t * 1.7f));
        paint.setShader(new RadialGradient(cx, cy, r * 4f,
                museumMode ? Color.argb(190, 255, 255, 255) : Color.argb(220, 232, 226, 207),
                Color.TRANSPARENT,
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, cy, r * 4f, paint);
        paint.setShader(null);
        paint.setColor(museumMode ? Color.rgb(40, 40, 35) : Color.rgb(246, 240, 219));
        canvas.drawCircle(cx, cy, r, paint);
    }

    private void drawGardenGhosts(Canvas canvas, float w, float h, float t) {
        int count = Math.min(garden.size() - 1, 8);
        if (count <= 0) return;
        for (int i = 0; i < count; i++) {
            Sculpture s = garden.get(Math.max(0, garden.size() - 2 - i));
            float lane = (i % 2 == 0) ? -1f : 1f;
            float depth = 1f + i * 0.42f;
            float x = w * 0.5f + lane * w * (0.22f + i * 0.025f);
            float y = h * (0.62f - i * 0.018f);
            float scale = Math.min(w, h) * 0.105f / depth;
            drawSculpture(canvas, s, x, y, scale, 0.13f, orbitX * 0.3f, orbitY + i * 0.7f, t, true);
        }
    }

    private void drawDescendants(Canvas canvas, float w, float h, float t) {
        drawTextCentered(canvas, "FIVE DESCENDANTS", w * 0.5f, h * 0.16f, 15f, 0.78f);
        drawTextCentered(canvas, "touch one organism to let it survive", w * 0.5f, h * 0.195f, 11f, 0.52f);
        float rowY = h * 0.54f;
        for (int i = 0; i < descendants.size(); i++) {
            float cx = w * (0.14f + i * 0.18f);
            float localOrbit = orbitY + i * 0.62f + t * 0.08f;
            drawSculpture(canvas, descendants.get(i), cx, rowY, Math.min(w, h) * 0.105f, 0.92f, orbitX * 0.5f, localOrbit, t, false);
            drawTextCentered(canvas, String.format(Locale.US, "GEN %02d", descendants.get(i).generation), cx, rowY + h * 0.19f, 10f, 0.48f);
        }
    }

    private void drawSculpture(Canvas canvas, Sculpture sculpture, float cx, float cy, float scale, float alpha, float rx, float ry, float time, boolean ghost) {
        List<RenderSegment> segments = new ArrayList<>();
        for (Branch branch : sculpture.branches) {
            for (int i = 0; i < branch.points.size() - 1; i++) {
                Vec3 a = branch.points.get(i);
                Vec3 b = branch.points.get(i + 1);
                Vec3 ar = rotate(a, rx + sculpture.dna.twist * 0.15f, ry + sculpture.dna.twist + time * 0.025f);
                Vec3 br = rotate(b, rx + sculpture.dna.twist * 0.15f, ry + sculpture.dna.twist + time * 0.025f);
                float az = ar.z;
                float bz = br.z;
                float pa = 1.0f / (1.85f + az * 0.45f);
                float pb = 1.0f / (1.85f + bz * 0.45f);
                RenderSegment seg = new RenderSegment();
                seg.x1 = cx + ar.x * scale * pa;
                seg.y1 = cy - ar.y * scale * pa;
                seg.x2 = cx + br.x * scale * pb;
                seg.y2 = cy - br.y * scale * pb;
                seg.depth = (az + bz) * 0.5f;
                seg.energy = branch.energy;
                seg.radius = Math.max(1f, branch.radius * scale * 0.035f * (pa + pb) * 0.5f);
                seg.branchDepth = branch.depth;
                segments.add(seg);
            }
        }
        Collections.sort(segments, new Comparator<RenderSegment>() {
            @Override
            public int compare(RenderSegment a, RenderSegment b) {
                return Float.compare(b.depth, a.depth);
            }
        });

        Material material = Material.from(sculpture.dna.materialIndex, museumMode, sculpture.dna.paletteShift);
        float shadowAlpha = alpha * sculpture.dna.shadowSoftness;
        softPaint.setStyle(Paint.Style.STROKE);
        softPaint.setStrokeCap(Paint.Cap.ROUND);
        softPaint.setStrokeJoin(Paint.Join.ROUND);
        softPaint.setMaskFilter(new BlurMaskFilter(ghost ? 8f : 17f, BlurMaskFilter.Blur.NORMAL));
        softPaint.setColor(Color.argb((int)(70 * shadowAlpha), 0, 0, 0));
        for (RenderSegment seg : segments) {
            softPaint.setStrokeWidth(seg.radius * (ghost ? 2.7f : 4.2f));
            canvas.drawLine(seg.x1, seg.y1 + scale * 0.18f, seg.x2, seg.y2 + scale * 0.18f, softPaint);
        }
        softPaint.setMaskFilter(null);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        for (RenderSegment seg : segments) {
            float light = clamp(0.55f + (seg.depth * 0.18f) + seg.energy * 0.08f, 0.25f, 1f);
            int color = blend(material.deep, material.light, light);
            int a = (int)(alpha * (ghost ? 62 : 230));
            paint.setColor(withAlpha(color, a));
            paint.setStrokeWidth(seg.radius * (ghost ? 0.85f : 1.0f));
            if (material.translucent && !ghost) {
                paint.setAlpha((int)(a * 0.68f));
            }
            canvas.drawLine(seg.x1, seg.y1, seg.x2, seg.y2, paint);

            if (!ghost && seg.radius > 3f && seg.branchDepth < 3) {
                paint.setColor(withAlpha(material.highlight, (int)(alpha * 85)));
                paint.setStrokeWidth(Math.max(1f, seg.radius * 0.23f));
                canvas.drawLine(seg.x1, seg.y1 - seg.radius * 0.18f, seg.x2, seg.y2 - seg.radius * 0.18f, paint);
            }
        }

        if (!ghost) {
            paint.setStyle(Paint.Style.FILL);
            paint.setShader(new RadialGradient(cx, cy, scale * 0.5f, withAlpha(material.light, 74), Color.TRANSPARENT, Shader.TileMode.CLAMP));
            canvas.drawCircle(cx, cy, scale * 0.50f, paint);
            paint.setShader(null);
        }
    }

    private void drawInterface(Canvas canvas, float w, float h, float t) {
        float pad = Math.max(22f, w * 0.055f);
        textPaint.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        textPaint.setTextAlign(Paint.Align.LEFT);
        textPaint.setTextSize(sp(12));
        textPaint.setColor(museumMode ? Color.argb(140, 20, 20, 20) : Color.argb(150, 235, 231, 222));
        canvas.drawText("SCULPTURE GARDEN", pad, pad + sp(8), textPaint);

        String dnaLine = "GEN " + current.generation + "  ·  " + current.materialName() + "  ·  DNA " + current.dna.shortCode();
        textPaint.setTextSize(sp(10));
        textPaint.setColor(museumMode ? Color.argb(110, 30, 30, 30) : Color.argb(118, 235, 231, 222));
        canvas.drawText(dnaLine, pad, h - pad, textPaint);

        String help = "swipe grow/orbit  ·  pinch split  ·  hold mass  ·  shake mutate  ·  volume branches";
        textPaint.setTextSize(sp(9));
        textPaint.setColor(museumMode ? Color.argb(78, 30, 30, 30) : Color.argb(86, 235, 231, 222));
        canvas.drawText(help, pad, h - pad + sp(17), textPaint);

        textPaint.setTextAlign(Paint.Align.RIGHT);
        textPaint.setTextSize(sp(14));
        textPaint.setColor(museumMode ? Color.argb(168, 15, 15, 15) : Color.argb(190, 255, 248, 226));
        String evolve = "EVOLVE";
        float ex = w - pad;
        float ey = h - pad;
        canvas.drawText(evolve, ex, ey, textPaint);
        float ew = textPaint.measureText(evolve) + sp(30);
        evolveRect.set(ex - ew, ey - sp(30), ex + sp(10), ey + sp(18));

        textPaint.setTextSize(sp(9));
        String museum = museumMode ? "VOID" : "MUSEUM";
        canvas.drawText(museum, w - pad, pad + sp(8), textPaint);
        float mw = textPaint.measureText(museum) + sp(28);
        museumRect.set(w - pad - mw, pad - sp(16), w - pad + sp(10), pad + sp(22));

        String relax = "RELAX";
        canvas.drawText(relax, w - pad, pad + sp(29), textPaint);
        float rw = textPaint.measureText(relax) + sp(28);
        relaxRect.set(w - pad - rw, pad + sp(6), w - pad + sp(10), pad + sp(42));

        long age = System.currentTimeMillis() - pulseAt;
        if (age < 1800L) {
            float a = 1f - age / 1800f;
            drawTextCentered(canvas, pulseText, w * 0.5f, h * 0.82f, 12f, a * 0.62f);
        }
    }

    private void drawRelaxWhisper(Canvas canvas, float w, float h, float t) {
        long age = System.currentTimeMillis() - pulseAt;
        float a = age < 2200L ? 1f - age / 2200f : 0.12f + 0.05f * (float)Math.sin(t * 0.35f);
        drawTextCentered(canvas, pulseText, w * 0.5f, h * 0.88f, 10f, a * 0.36f);
    }

    private void drawTextCentered(Canvas canvas, String text, float x, float y, float sizeSp, float alpha) {
        textPaint.setTypeface(Typeface.create("sans", Typeface.NORMAL));
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(sp(sizeSp));
        int base = museumMode ? Color.rgb(22, 22, 20) : Color.rgb(240, 236, 224);
        textPaint.setColor(withAlpha(base, (int)(255 * clamp(alpha, 0f, 1f))));
        canvas.drawText(text, x, y, textPaint);
    }

    private float sp(float v) {
        return v * getResources().getDisplayMetrics().scaledDensity;
    }

    private static Vec3 rotate(Vec3 p, float ax, float ay) {
        float cosY = (float)Math.cos(ay);
        float sinY = (float)Math.sin(ay);
        float cosX = (float)Math.cos(ax);
        float sinX = (float)Math.sin(ax);
        float x = p.x * cosY - p.z * sinY;
        float z = p.x * sinY + p.z * cosY;
        float y = p.y * cosX - z * sinX;
        z = p.y * sinX + z * cosX;
        return new Vec3(x, y, z);
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

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

    private static class Vec3 {
        float x;
        float y;
        float z;
        Vec3(float x, float y, float z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }
        Vec3 add(Vec3 other) {
            return new Vec3(x + other.x, y + other.y, z + other.z);
        }
        Vec3 scale(float s) {
            return new Vec3(x * s, y * s, z * s);
        }
        Vec3 normalized() {
            float len = (float)Math.sqrt(x * x + y * y + z * z);
            if (len < 0.0001f) return new Vec3(0f, 1f, 0f);
            return new Vec3(x / len, y / len, z / len);
        }
    }

    private static class Branch {
        final List<Vec3> points = new ArrayList<>();
        float radius;
        float energy;
        int depth;
    }

    private static class RenderSegment {
        float x1;
        float y1;
        float x2;
        float y2;
        float depth;
        float radius;
        float energy;
        int branchDepth;
    }

    private static class Sculpture {
        static int nextId = 1;
        int id = nextId++;
        int parentId = 0;
        int generation = 1;
        final List<Integer> childrenIds = new ArrayList<>();
        DNA dna;
        final List<Branch> branches = new ArrayList<>();

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
            child.rebuild();
            return child;
        }

        String materialName() {
            return Material.name(dna.materialIndex);
        }

        void growBranch(float influence) {
            dna.growth = clamp(dna.growth + 0.035f * influence, 0.08f, 1.0f);
            dna.seed += 31L + (long)(influence * 1000f) + branches.size();
            rebuild();
        }

        void collapseBranch() {
            dna.growth = clamp(dna.growth - 0.055f, 0.08f, 1.0f);
            dna.branchFrequency = clamp(dna.branchFrequency - 0.045f, 0.08f, 0.94f);
            rebuild();
        }

        void rebuild() {
            branches.clear();
            Random random = new Random(dna.seed);
            int roots = 2 + Math.round(dna.balance * 4f);
            for (int i = 0; i < roots; i++) {
                float theta = (float)(Math.PI * 2.0 * i / roots + dna.rhythm * 1.7f);
                float outward = 0.22f + dna.chaos * 0.11f;
                Vec3 start = new Vec3((float)Math.cos(theta) * outward, -0.12f, (float)Math.sin(theta) * outward);
                Vec3 dir = new Vec3((float)Math.cos(theta) * 0.26f, 0.95f + dna.elasticity * 0.25f, (float)Math.sin(theta) * 0.26f).normalized();
                generateBranch(random, start, dir, 0, 0.55f + dna.thickness * 0.9f, theta);
            }
        }

        private void generateBranch(Random random, Vec3 start, Vec3 dir, int depth, float radius, float theta) {
            if (depth > 4) return;
            Branch branch = new Branch();
            branch.radius = radius;
            branch.depth = depth;
            branch.energy = 1f - depth * 0.13f;
            branch.points.add(start);

            int steps = 5 + Math.round(dna.density * 7f) - depth;
            steps = Math.max(4, steps);
            float length = (0.15f + dna.growth * 0.18f) * (1f - depth * 0.10f);
            Vec3 pos = start;
            Vec3 currentDir = dir;
            for (int i = 1; i <= steps; i++) {
                float p = i / (float) steps;
                float wave = (float)Math.sin((p * Math.PI * 2f * (1.0f + dna.rhythm * 1.8f)) + theta + dna.twist);
                float spiral = theta + p * dna.curvature * 2.8f + depth * 0.8f;
                Vec3 curve = new Vec3(
                        (float)Math.cos(spiral) * dna.curvature * 0.20f * wave,
                        0.58f + dna.elasticity * 0.36f,
                        (float)Math.sin(spiral) * dna.curvature * 0.20f * wave
                ).normalized();
                currentDir = new Vec3(
                        currentDir.x * 0.78f + curve.x * 0.22f,
                        currentDir.y * 0.82f + curve.y * 0.18f,
                        currentDir.z * 0.78f + curve.z * 0.22f
                ).normalized();
                float noise = (random.nextFloat() - 0.5f) * dna.surfaceNoise * 0.035f;
                pos = pos.add(currentDir.scale(length + noise));
                branch.points.add(pos);

                boolean canFork = depth < 4 && i > 2 && i < steps;
                float chance = dna.branchFrequency * (0.31f - depth * 0.045f);
                if (canFork && random.nextFloat() < chance) {
                    float side = random.nextBoolean() ? 1f : -1f;
                    float turn = theta + side * (0.72f + dna.balance * 0.68f) + random.nextFloat() * dna.chaos * 0.65f;
                    Vec3 fork = new Vec3(
                            currentDir.x * 0.38f + (float)Math.cos(turn) * 0.62f,
                            currentDir.y * (0.54f + dna.elasticity * 0.15f),
                            currentDir.z * 0.38f + (float)Math.sin(turn) * 0.62f
                    ).normalized();
                    generateBranch(random, pos, fork, depth + 1, radius * (0.68f + dna.thickness * 0.05f), turn);
                }
            }
            branches.add(branch);
        }
    }

    private static class DNA {
        long seed;
        float curvature;
        float elasticity;
        float chaos;
        float rhythm;
        float density;
        float thickness;
        float balance;
        float growth;
        float branchFrequency;
        float shadowSoftness;
        float surfaceNoise;
        float twist;
        float paletteShift;
        int materialIndex;

        static DNA seed() {
            DNA dna = new DNA();
            dna.seed = 730241L;
            dna.curvature = 0.52f;
            dna.elasticity = 0.46f;
            dna.chaos = 0.26f;
            dna.rhythm = 0.38f;
            dna.density = 0.46f;
            dna.thickness = 0.32f;
            dna.balance = 0.64f;
            dna.growth = 0.24f;
            dna.branchFrequency = 0.24f;
            dna.shadowSoftness = 0.68f;
            dna.surfaceNoise = 0.32f;
            dna.twist = 0.0f;
            dna.paletteShift = 0.15f;
            dna.materialIndex = 0;
            return dna;
        }

        DNA mutated(int childIndex, float intensity) {
            DNA d = copy();
            Random r = new Random(seed + childIndex * 104729L + 991L);
            d.seed = seed + 1009L * childIndex + r.nextInt(9000);
            d.curvature = mutateNice(r, curvature, intensity, 0.12f, 0.92f);
            d.elasticity = mutateNice(r, elasticity, intensity, 0.10f, 0.88f);
            d.chaos = mutateNice(r, chaos, intensity * 0.80f, 0.08f, 0.58f);
            d.rhythm = mutateNice(r, rhythm, intensity, 0.06f, 0.93f);
            d.density = mutateNice(r, density, intensity * 0.75f, 0.18f, 0.86f);
            d.thickness = mutateNice(r, thickness, intensity * 0.72f, 0.11f, 0.88f);
            d.balance = mutateNice(r, balance, intensity * 0.52f, 0.20f, 0.92f);
            d.growth = mutateNice(r, growth + 0.055f, intensity * 0.55f, 0.10f, 1.0f);
            d.branchFrequency = mutateNice(r, branchFrequency, intensity * 0.72f, 0.08f, 0.84f);
            d.shadowSoftness = mutateNice(r, shadowSoftness, intensity * 0.44f, 0.35f, 0.95f);
            d.surfaceNoise = mutateNice(r, surfaceNoise, intensity * 0.70f, 0.08f, 0.62f);
            d.twist += (r.nextFloat() - 0.5f) * intensity * 3.2f;
            d.paletteShift = mutateNice(r, paletteShift, intensity * 0.8f, 0.0f, 1.0f);
            if (r.nextFloat() < 0.22f) {
                d.materialIndex = (d.materialIndex + 1 + r.nextInt(Material.COUNT - 1)) % Material.COUNT;
            }
            return d;
        }

        private static float mutateNice(Random r, float base, float intensity, float min, float max) {
            float harmonic = (r.nextFloat() - 0.5f) * 2f;
            harmonic = (float)Math.sin(harmonic * Math.PI * 0.5f);
            return clamp(base + harmonic * intensity, min, max);
        }

        DNA copy() {
            DNA d = new DNA();
            d.seed = seed;
            d.curvature = curvature;
            d.elasticity = elasticity;
            d.chaos = chaos;
            d.rhythm = rhythm;
            d.density = density;
            d.thickness = thickness;
            d.balance = balance;
            d.growth = growth;
            d.branchFrequency = branchFrequency;
            d.shadowSoftness = shadowSoftness;
            d.surfaceNoise = surfaceNoise;
            d.twist = twist;
            d.paletteShift = paletteShift;
            d.materialIndex = materialIndex;
            return d;
        }

        String shortCode() {
            long code = Math.abs(seed % 100000L);
            return String.format(Locale.US, "%05d-%02d", code, materialIndex + 1);
        }
    }

    private static class Material {
        static final int COUNT = 9;
        final int deep;
        final int light;
        final int highlight;
        final boolean translucent;
        Material(int deep, int light, int highlight, boolean translucent) {
            this.deep = deep;
            this.light = light;
            this.highlight = highlight;
            this.translucent = translucent;
        }
        static String name(int index) {
            String[] names = {
                    "Porcelain", "Clay", "Liquid Metal", "Glass", "Wax", "Stone", "Paper", "Synthetic Skin", "Foam"
            };
            return names[Math.floorMod(index, names.length)];
        }
        static Material from(int index, boolean museum, float shift) {
            int i = Math.floorMod(index, COUNT);
            switch (i) {
                case 1:
                    return new Material(Color.rgb(92, 66, 53), Color.rgb(213, 164, 126), Color.rgb(247, 214, 180), false);
                case 2:
                    return new Material(Color.rgb(88, 91, 94), Color.rgb(214, 218, 219), Color.rgb(255, 255, 255), false);
                case 3:
                    return new Material(Color.rgb(75, 90, 96), Color.rgb(187, 225, 223), Color.rgb(245, 255, 255), true);
                case 4:
                    return new Material(Color.rgb(113, 92, 55), Color.rgb(226, 197, 142), Color.rgb(255, 241, 198), false);
                case 5:
                    return new Material(Color.rgb(58, 58, 55), Color.rgb(170, 166, 153), Color.rgb(229, 225, 210), false);
                case 6:
                    return new Material(Color.rgb(126, 123, 112), Color.rgb(226, 220, 199), Color.rgb(255, 252, 233), false);
                case 7:
                    return new Material(Color.rgb(126, 85, 82), Color.rgb(222, 174, 164), Color.rgb(255, 220, 211), false);
                case 8:
                    return new Material(Color.rgb(118, 119, 106), Color.rgb(210, 214, 190), Color.rgb(246, 249, 225), true);
                default:
                    if (museum) {
                        return new Material(Color.rgb(92, 92, 88), Color.rgb(230, 228, 219), Color.rgb(255, 255, 248), false);
                    }
                    return new Material(Color.rgb(108, 105, 96), Color.rgb(238, 233, 216), Color.rgb(255, 252, 236), false);
            }
        }
    }
}
