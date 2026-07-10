package com.mangoscam.ascii3d;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.Gravity;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

public class MainActivity extends Activity {
    private AsciiModelerView modelerView;
    private TextView statusView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window window = getWindow();
        window.setStatusBarColor(Color.rgb(5, 5, 9));
        window.setNavigationBarColor(Color.rgb(5, 5, 9));

        modelerView = new AsciiModelerView(this);

        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(Color.rgb(5, 5, 9));
        root.addView(modelerView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
        ));

        statusView = new TextView(this);
        statusView.setText("ASCII3D LAB · Orbit mode");
        statusView.setTextColor(Color.rgb(180, 255, 210));
        statusView.setTextSize(12f);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(16, 8, 16, 8);
        statusView.setBackgroundColor(Color.argb(145, 0, 0, 0));
        FrameLayout.LayoutParams statusParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        statusParams.gravity = Gravity.TOP;
        root.addView(statusView, statusParams);

        HorizontalScrollView scroller = new HorizontalScrollView(this);
        scroller.setFillViewport(false);
        scroller.setHorizontalScrollBarEnabled(false);
        scroller.setBackgroundColor(Color.argb(190, 0, 0, 0));

        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(10, 10, 10, 10);

        addButton(toolbar, "Cubo", () -> {
            modelerView.loadCube();
            setStatus("Primitive: cube");
        });
        addButton(toolbar, "Pira", () -> {
            modelerView.loadPyramid();
            setStatus("Primitive: pyramid");
        });
        addButton(toolbar, "Esfera", () -> {
            modelerView.loadSphere();
            setStatus("Primitive: low-poly sphere");
        });
        addButton(toolbar, "Extrude", () -> {
            modelerView.extrudeFrontFace();
            setStatus("Extruded face · grow the ASCII beast");
        });
        addButton(toolbar, "Mutar", () -> {
            modelerView.mutateMesh();
            setStatus("Mesh mutation applied");
        });
        addButton(toolbar, "Editar", () -> {
            boolean editing = modelerView.toggleEditMode();
            setStatus(editing ? "Edit mode · drag a vertex" : "Orbit mode · drag to rotate");
        });
        addButton(toolbar, "Chars", () -> {
            modelerView.nextPalette();
            setStatus("ASCII palette changed");
        });
        addButton(toolbar, "Reset", () -> {
            modelerView.resetView();
            setStatus("View reset");
        });
        addButton(toolbar, "Share", this::shareAscii);

        scroller.addView(toolbar, new HorizontalScrollView.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        ));

        FrameLayout.LayoutParams toolbarParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        toolbarParams.gravity = Gravity.BOTTOM;
        root.addView(scroller, toolbarParams);

        setContentView(root);
    }

    private void addButton(LinearLayout toolbar, String label, Runnable action) {
        Button button = new Button(this);
        button.setText(label);
        button.setAllCaps(false);
        button.setTextSize(11f);
        button.setTextColor(Color.rgb(220, 255, 235));
        button.setMinHeight(0);
        button.setMinimumHeight(0);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(18, 10, 18, 10);
        button.setBackground(makeButtonBackground());
        button.setOnClickListener(view -> action.run());

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
        );
        params.setMargins(5, 0, 5, 0);
        toolbar.addView(button, params);
    }

    private GradientDrawable makeButtonBackground() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(Color.rgb(18, 21, 29));
        drawable.setStroke(1, Color.rgb(87, 255, 150));
        drawable.setCornerRadius(18f);
        return drawable;
    }

    private void setStatus(String status) {
        statusView.setText("ASCII3D LAB · " + status);
    }

    private void shareAscii() {
        Intent intent = new Intent(Intent.ACTION_SEND);
        intent.setType("text/plain");
        intent.putExtra(Intent.EXTRA_SUBJECT, "ASCII3D Lab export");
        intent.putExtra(Intent.EXTRA_TEXT, modelerView.exportAsciiSnapshot());
        startActivity(Intent.createChooser(intent, "Export ASCII model"));
    }
}
