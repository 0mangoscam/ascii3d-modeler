package com.mangoscam.ascii3d;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class AsciiModelerView extends View {
    private final Paint asciiPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint overlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final ScaleGestureDetector scaleDetector;
    private final Random random = new Random(1337);

    private Mesh mesh = Mesh.cube();
    private double rotX = -0.45;
    private double rotY = 0.72;
    private double zoom = 1.0;
    private float lastX;
    private float lastY;
    private boolean editMode = false;
    private int selectedVertex = -1;
    private int paletteIndex = 0;
    private int lastCols = 80;
    private int lastRows = 42;

    private final String[] palettes = new String[]{
            " .:-=+*#%@",
            " `'^\",:;Il!i><~+_-?][}{1)(|\\/tfjrxnuvczXYUJCLQ0OZmwqpdbkhao*#MW&8%B@$",
            "  ...,,,---+++***###@@@",
            " ·░▒▓█"
    };

    public AsciiModelerView(Context context) {
        super(context);
        setFocusable(true);
        setBackgroundColor(Color.rgb(5, 5, 9));

        asciiPaint.setColor(Color.rgb(165, 255, 195));
        asciiPaint.setTypeface(Typeface.MONOSPACE);
        asciiPaint.setTextSize(17f);
        asciiPaint.setSubpixelText(true);

        overlayPaint.setColor(Color.rgb(88, 255, 150));
        overlayPaint.setTypeface(Typeface.MONOSPACE);
        overlayPaint.setTextSize(28f);

        scaleDetector = new ScaleGestureDetector(context, new ScaleGestureDetector.SimpleOnScaleGestureListener() {
            @Override
            public boolean onScale(ScaleGestureDetector detector) {
                zoom *= detector.getScaleFactor();
                zoom = clamp(zoom, 0.35, 3.2);
                invalidate();
                return true;
            }
        });
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float charWidth = Math.max(1f, asciiPaint.measureText("M"));
        Paint.FontMetrics metrics = asciiPaint.getFontMetrics();
        float lineHeight = Math.max(1f, metrics.descent - metrics.ascent);

        int cols = Math.max(32, Math.min(132, (int) (getWidth() / charWidth)));
        int rows = Math.max(18, Math.min(74, (int) ((getHeight() - 116f) / lineHeight)));
        lastCols = cols;
        lastRows = rows;

        RenderResult result = renderAscii(cols, rows);
        float y = 62f;
        for (String line : result.lines) {
            canvas.drawText(line, 8f, y, asciiPaint);
            y += lineHeight;
        }

        drawOverlay(canvas, result);
    }

    private void drawOverlay(Canvas canvas, RenderResult result) {
        overlayPaint.setTextSize(12f);
        overlayPaint.setColor(Color.rgb(88, 255, 150));
        String mode = editMode ? "EDIT" : "ORBIT";
        String text = String.format(Locale.US,
                "%s · V:%d F:%d · zoom %.2f · drag/orbit · pinch/scale",
                mode, mesh.vertices.size(), mesh.faces.size(), zoom);
        canvas.drawText(text, 14f, getHeight() - 86f, overlayPaint);

        overlayPaint.setColor(Color.argb(130, 88, 255, 150));
        canvas.drawText("ASCII3D_MODEL_BUFFER::" + result.signature, 14f, getHeight() - 66f, overlayPaint);
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        scaleDetector.onTouchEvent(event);
        if (event.getPointerCount() > 1) {
            return true;
        }

        float x = event.getX();
        float y = event.getY();

        switch (event.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                lastX = x;
                lastY = y;
                if (editMode) {
                    selectedVertex = findNearestVertex(x, y);
                }
                return true;
            case MotionEvent.ACTION_MOVE:
                float dx = x - lastX;
                float dy = y - lastY;
                if (editMode && selectedVertex >= 0) {
                    moveSelectedVertex(dx, dy);
                } else {
                    rotY += dx * 0.0105;
                    rotX += dy * 0.0105;
                    rotX = clamp(rotX, -1.45, 1.45);
                }
                lastX = x;
                lastY = y;
                invalidate();
                return true;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_CANCEL:
                selectedVertex = -1;
                return true;
            default:
                return true;
        }
    }

    public void loadCube() {
        mesh = Mesh.cube();
        selectedVertex = -1;
        invalidate();
    }

    public void loadPyramid() {
        mesh = Mesh.pyramid();
        selectedVertex = -1;
        invalidate();
    }

    public void loadSphere() {
        mesh = Mesh.sphere(7, 12);
        selectedVertex = -1;
        invalidate();
    }

    public void extrudeFrontFace() {
        mesh.extrudeMostVisibleFace();
        mesh.normalizeSoft();
        selectedVertex = -1;
        invalidate();
    }

    public void mutateMesh() {
        for (int i = 0; i < mesh.vertices.size(); i++) {
            Vec3 v = mesh.vertices.get(i);
            double wave = Math.sin(i * 1.71 + mesh.faces.size() * 0.23);
            v.x += wave * 0.12 + (random.nextDouble() - 0.5) * 0.09;
            v.y += Math.cos(i * 1.19) * 0.11 + (random.nextDouble() - 0.5) * 0.09;
            v.z += Math.sin(i * 0.73) * 0.14 + (random.nextDouble() - 0.5) * 0.09;
        }
        mesh.normalizeSoft();
        invalidate();
    }

    public boolean toggleEditMode() {
        editMode = !editMode;
        selectedVertex = -1;
        invalidate();
        return editMode;
    }

    public void nextPalette() {
        paletteIndex = (paletteIndex + 1) % palettes.length;
        invalidate();
    }

    public void resetView() {
        rotX = -0.45;
        rotY = 0.72;
        zoom = 1.0;
        selectedVertex = -1;
        invalidate();
    }

    public String exportAsciiSnapshot() {
        RenderResult result = renderAscii(Math.max(48, lastCols), Math.max(24, lastRows));
        StringBuilder builder = new StringBuilder();
        builder.append("ASCII3D Lab export\n");
        builder.append("vertices=").append(mesh.vertices.size())
                .append(" faces=").append(mesh.faces.size())
                .append(" palette=").append(paletteIndex)
                .append("\n\n");
        for (String line : result.lines) {
            builder.append(line).append('\n');
        }
        builder.append("\n# vertex cloud\n");
        for (int i = 0; i < mesh.vertices.size(); i++) {
            Vec3 v = mesh.vertices.get(i);
            builder.append(String.format(Locale.US, "v %02d %.3f %.3f %.3f\n", i, v.x, v.y, v.z));
        }
        builder.append("\n# faces\n");
        for (int i = 0; i < mesh.faces.size(); i++) {
            int[] face = mesh.faces.get(i);
            builder.append("f ");
            for (int index : face) {
                builder.append(index).append(' ');
            }
            builder.append('\n');
        }
        return builder.toString();
    }

    private RenderResult renderAscii(int cols, int rows) {
        char[][] buffer = new char[rows][cols];
        double[][] depth = new double[rows][cols];
        for (int r = 0; r < rows; r++) {
            Arrays.fill(buffer[r], ' ');
            Arrays.fill(depth[r], -1_000_000.0);
        }

        drawGridFrame(buffer);

        Projected[] projected = new Projected[mesh.vertices.size()];
        Vec3[] cameraVertices = new Vec3[mesh.vertices.size()];
        for (int i = 0; i < mesh.vertices.size(); i++) {
            Vec3 camera = rotate(mesh.vertices.get(i));
            cameraVertices[i] = camera;
            projected[i] = project(camera, cols, rows);
        }

        for (int f = 0; f < mesh.faces.size(); f++) {
            int[] face = mesh.faces.get(f);
            if (face.length < 3) {
                continue;
            }
            Vec3 normal = faceNormal(cameraVertices, face);
            double brightness = clamp(Math.abs(normal.z * 0.78 - normal.y * 0.20 + normal.x * 0.10), 0.06, 1.0);
            char fill = shadeChar(brightness);
            char edge = shadeChar(clamp(brightness + 0.24, 0.0, 1.0));

            for (int i = 1; i < face.length - 1; i++) {
                drawTriangle(buffer, depth, projected[face[0]], projected[face[i]], projected[face[i + 1]], fill);
            }
            for (int i = 0; i < face.length; i++) {
                Projected a = projected[face[i]];
                Projected b = projected[face[(i + 1) % face.length]];
                drawLine(buffer, depth, a, b, edge);
            }
        }

        for (int i = 0; i < projected.length; i++) {
            char vertexChar = (i == selectedVertex) ? 'X' : 'o';
            put(buffer, depth, (int) Math.round(projected[i].x), (int) Math.round(projected[i].y), projected[i].depth + 0.01, vertexChar);
        }

        String[] lines = new String[rows];
        int ink = 0;
        for (int r = 0; r < rows; r++) {
            lines[r] = new String(buffer[r]);
            for (int c = 0; c < cols; c++) {
                if (buffer[r][c] != ' ') {
                    ink++;
                }
            }
        }
        return new RenderResult(lines, Integer.toHexString(ink * 31 + mesh.vertices.size() * 17 + mesh.faces.size()));
    }

    private void drawGridFrame(char[][] buffer) {
        int rows = buffer.length;
        int cols = buffer[0].length;
        for (int x = 0; x < cols; x += 4) {
            buffer[0][x] = '.';
            buffer[rows - 1][x] = '.';
        }
        for (int y = 0; y < rows; y += 3) {
            buffer[y][0] = ':';
            buffer[y][cols - 1] = ':';
        }
    }

    private void drawTriangle(char[][] buffer, double[][] depth, Projected a, Projected b, Projected c, char ch) {
        int rows = buffer.length;
        int cols = buffer[0].length;
        int minX = Math.max(0, (int) Math.floor(Math.min(a.x, Math.min(b.x, c.x))));
        int maxX = Math.min(cols - 1, (int) Math.ceil(Math.max(a.x, Math.max(b.x, c.x))));
        int minY = Math.max(0, (int) Math.floor(Math.min(a.y, Math.min(b.y, c.y))));
        int maxY = Math.min(rows - 1, (int) Math.ceil(Math.max(a.y, Math.max(b.y, c.y))));
        double area = edgeFunction(a.x, a.y, b.x, b.y, c.x, c.y);
        if (Math.abs(area) < 0.0001) {
            return;
        }
        for (int y = minY; y <= maxY; y++) {
            for (int x = minX; x <= maxX; x++) {
                double px = x + 0.5;
                double py = y + 0.5;
                double w0 = edgeFunction(b.x, b.y, c.x, c.y, px, py) / area;
                double w1 = edgeFunction(c.x, c.y, a.x, a.y, px, py) / area;
                double w2 = edgeFunction(a.x, a.y, b.x, b.y, px, py) / area;
                if (w0 >= -0.015 && w1 >= -0.015 && w2 >= -0.015) {
                    double z = w0 * a.depth + w1 * b.depth + w2 * c.depth;
                    put(buffer, depth, x, y, z, ch);
                }
            }
        }
    }

    private void drawLine(char[][] buffer, double[][] depth, Projected a, Projected b, char ch) {
        int steps = Math.max(1, (int) (Math.max(Math.abs(a.x - b.x), Math.abs(a.y - b.y)) * 2.0));
        for (int i = 0; i <= steps; i++) {
            double t = i / (double) steps;
            double x = a.x + (b.x - a.x) * t;
            double y = a.y + (b.y - a.y) * t;
            double z = a.depth + (b.depth - a.depth) * t;
            put(buffer, depth, (int) Math.round(x), (int) Math.round(y), z + 0.02, ch);
        }
    }

    private void put(char[][] buffer, double[][] depth, int x, int y, double z, char ch) {
        if (y < 0 || y >= buffer.length || x < 0 || x >= buffer[0].length) {
            return;
        }
        if (z >= depth[y][x]) {
            depth[y][x] = z;
            buffer[y][x] = ch;
        }
    }

    private double edgeFunction(double ax, double ay, double bx, double by, double cx, double cy) {
        return (cx - ax) * (by - ay) - (cy - ay) * (bx - ax);
    }

    private Vec3 rotate(Vec3 v) {
        double cosY = Math.cos(rotY);
        double sinY = Math.sin(rotY);
        double cosX = Math.cos(rotX);
        double sinX = Math.sin(rotX);

        double x1 = v.x * cosY + v.z * sinY;
        double z1 = -v.x * sinY + v.z * cosY;
        double y1 = v.y * cosX - z1 * sinX;
        double z2 = v.y * sinX + z1 * cosX;
        return new Vec3(x1, y1, z2);
    }

    private Projected project(Vec3 v, int cols, int rows) {
        double perspective = 1.0 / Math.max(0.28, 3.8 - v.z * 0.22);
        double scale = Math.min(cols, rows) * 1.36 * zoom * perspective;
        double x = cols * 0.5 + v.x * scale;
        double y = rows * 0.48 - v.y * scale * 0.72;
        return new Projected(x, y, v.z);
    }

    private Vec3 faceNormal(Vec3[] verts, int[] face) {
        Vec3 a = verts[face[0]];
        Vec3 b = verts[face[1]];
        Vec3 c = verts[face[2]];
        Vec3 ab = b.minus(a);
        Vec3 ac = c.minus(a);
        return ab.cross(ac).normalized();
    }

    private char shadeChar(double brightness) {
        String palette = palettes[paletteIndex];
        int index = (int) Math.round(clamp(brightness, 0.0, 1.0) * (palette.length() - 1));
        return palette.charAt(index);
    }

    private int findNearestVertex(float touchX, float touchY) {
        float charWidth = Math.max(1f, asciiPaint.measureText("M"));
        Paint.FontMetrics metrics = asciiPaint.getFontMetrics();
        float lineHeight = Math.max(1f, metrics.descent - metrics.ascent);
        int cols = lastCols;
        int rows = lastRows;
        double best = 58.0 * 58.0;
        int bestIndex = -1;
        for (int i = 0; i < mesh.vertices.size(); i++) {
            Projected p = project(rotate(mesh.vertices.get(i)), cols, rows);
            double px = 8.0 + p.x * charWidth;
            double py = 62.0 + p.y * lineHeight;
            double dx = px - touchX;
            double dy = py - touchY;
            double dist = dx * dx + dy * dy;
            if (dist < best) {
                best = dist;
                bestIndex = i;
            }
        }
        return bestIndex;
    }

    private void moveSelectedVertex(float dx, float dy) {
        if (selectedVertex < 0 || selectedVertex >= mesh.vertices.size()) {
            return;
        }
        Vec3 v = mesh.vertices.get(selectedVertex);
        double factor = 0.006 / Math.max(0.35, zoom);
        double cosY = Math.cos(-rotY);
        double sinY = Math.sin(-rotY);
        double localX = dx * factor;
        double localY = -dy * factor;
        v.x += localX * cosY;
        v.z += localX * sinY;
        v.y += localY;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    private static final class RenderResult {
        final String[] lines;
        final String signature;

        RenderResult(String[] lines, String signature) {
            this.lines = lines;
            this.signature = signature;
        }
    }

    private static final class Projected {
        final double x;
        final double y;
        final double depth;

        Projected(double x, double y, double depth) {
            this.x = x;
            this.y = y;
            this.depth = depth;
        }
    }

    private static final class Vec3 {
        double x;
        double y;
        double z;

        Vec3(double x, double y, double z) {
            this.x = x;
            this.y = y;
            this.z = z;
        }

        Vec3 plus(Vec3 other) {
            return new Vec3(x + other.x, y + other.y, z + other.z);
        }

        Vec3 minus(Vec3 other) {
            return new Vec3(x - other.x, y - other.y, z - other.z);
        }

        Vec3 times(double scalar) {
            return new Vec3(x * scalar, y * scalar, z * scalar);
        }

        Vec3 cross(Vec3 other) {
            return new Vec3(
                    y * other.z - z * other.y,
                    z * other.x - x * other.z,
                    x * other.y - y * other.x
            );
        }

        Vec3 normalized() {
            double length = Math.sqrt(x * x + y * y + z * z);
            if (length < 0.00001) {
                return new Vec3(0, 0, 1);
            }
            return new Vec3(x / length, y / length, z / length);
        }
    }

    private static final class Mesh {
        final List<Vec3> vertices = new ArrayList<>();
        final List<int[]> faces = new ArrayList<>();

        static Mesh cube() {
            Mesh mesh = new Mesh();
            mesh.vertices.add(new Vec3(-1, -1, -1));
            mesh.vertices.add(new Vec3(1, -1, -1));
            mesh.vertices.add(new Vec3(1, 1, -1));
            mesh.vertices.add(new Vec3(-1, 1, -1));
            mesh.vertices.add(new Vec3(-1, -1, 1));
            mesh.vertices.add(new Vec3(1, -1, 1));
            mesh.vertices.add(new Vec3(1, 1, 1));
            mesh.vertices.add(new Vec3(-1, 1, 1));
            mesh.faces.add(new int[]{0, 1, 2, 3});
            mesh.faces.add(new int[]{4, 7, 6, 5});
            mesh.faces.add(new int[]{0, 4, 5, 1});
            mesh.faces.add(new int[]{1, 5, 6, 2});
            mesh.faces.add(new int[]{2, 6, 7, 3});
            mesh.faces.add(new int[]{3, 7, 4, 0});
            return mesh;
        }

        static Mesh pyramid() {
            Mesh mesh = new Mesh();
            mesh.vertices.add(new Vec3(-1.15, -1, -1.15));
            mesh.vertices.add(new Vec3(1.15, -1, -1.15));
            mesh.vertices.add(new Vec3(1.15, -1, 1.15));
            mesh.vertices.add(new Vec3(-1.15, -1, 1.15));
            mesh.vertices.add(new Vec3(0, 1.25, 0));
            mesh.faces.add(new int[]{0, 1, 2, 3});
            mesh.faces.add(new int[]{0, 4, 1});
            mesh.faces.add(new int[]{1, 4, 2});
            mesh.faces.add(new int[]{2, 4, 3});
            mesh.faces.add(new int[]{3, 4, 0});
            return mesh;
        }

        static Mesh sphere(int latitudes, int longitudes) {
            Mesh mesh = new Mesh();
            for (int lat = 0; lat <= latitudes; lat++) {
                double theta = Math.PI * lat / latitudes;
                double y = Math.cos(theta);
                double ring = Math.sin(theta);
                for (int lon = 0; lon < longitudes; lon++) {
                    double phi = Math.PI * 2.0 * lon / longitudes;
                    double x = Math.cos(phi) * ring;
                    double z = Math.sin(phi) * ring;
                    mesh.vertices.add(new Vec3(x, y, z));
                }
            }
            for (int lat = 0; lat < latitudes; lat++) {
                for (int lon = 0; lon < longitudes; lon++) {
                    int a = lat * longitudes + lon;
                    int b = lat * longitudes + ((lon + 1) % longitudes);
                    int c = (lat + 1) * longitudes + ((lon + 1) % longitudes);
                    int d = (lat + 1) * longitudes + lon;
                    mesh.faces.add(new int[]{a, b, c, d});
                }
            }
            mesh.normalizeSoft();
            return mesh;
        }

        void extrudeMostVisibleFace() {
            if (faces.isEmpty()) {
                return;
            }
            int bestFaceIndex = 0;
            double bestScore = -1_000_000.0;
            for (int i = 0; i < faces.size(); i++) {
                int[] face = faces.get(i);
                double score = 0.0;
                for (int index : face) {
                    score += vertices.get(index).z;
                }
                score /= face.length;
                if (score > bestScore) {
                    bestScore = score;
                    bestFaceIndex = i;
                }
            }

            int[] face = faces.get(bestFaceIndex);
            Vec3 normal = normalForFace(face);
            double amount = 0.38;
            int[] cap = new int[face.length];
            for (int i = 0; i < face.length; i++) {
                Vec3 source = vertices.get(face[i]);
                Vec3 extruded = source.plus(normal.times(amount));
                cap[i] = vertices.size();
                vertices.add(extruded);
            }

            faces.add(cap);
            for (int i = 0; i < face.length; i++) {
                int a = face[i];
                int b = face[(i + 1) % face.length];
                int c = cap[(i + 1) % cap.length];
                int d = cap[i];
                faces.add(new int[]{a, b, c, d});
            }
        }

        Vec3 normalForFace(int[] face) {
            if (face.length < 3) {
                return new Vec3(0, 0, 1);
            }
            Vec3 a = vertices.get(face[0]);
            Vec3 b = vertices.get(face[1]);
            Vec3 c = vertices.get(face[2]);
            return b.minus(a).cross(c.minus(a)).normalized();
        }

        void normalizeSoft() {
            if (vertices.isEmpty()) {
                return;
            }
            double max = 0.0001;
            for (Vec3 v : vertices) {
                max = Math.max(max, Math.abs(v.x));
                max = Math.max(max, Math.abs(v.y));
                max = Math.max(max, Math.abs(v.z));
            }
            if (max > 1.75) {
                double scale = 1.75 / max;
                for (Vec3 v : vertices) {
                    v.x *= scale;
                    v.y *= scale;
                    v.z *= scale;
                }
            }
        }
    }
}
