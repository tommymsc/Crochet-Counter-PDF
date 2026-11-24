package com.lauraapps.crochetcounter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.graphics.pdf.PdfRenderer;
import android.net.Uri;
import android.os.Bundle;
import android.os.ParcelFileDescriptor;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.os.Build;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.UUID;

public class MainActivity extends Activity {
    private static final int PICK_PDF = 41;
    private final ArrayList<Project> projects = new ArrayList<>();
    private String selectedId;
    private SharedPreferences prefs;
    private LinearLayout root;
    private TextView countView, progressView, pageView;
    private ProgressBar progressBar;
    private Button undoButton;
    private ImageView pdfImage;
    private FrameLayout pdfFrame;
    private PdfRenderer renderer;
    private ParcelFileDescriptor file;
    private String openedUri = "";
    private Bitmap visiblePdfBitmap;
    private int lastValue = -1;
    private boolean hasUndo = false;
    private static final int INK = Color.rgb(58, 40, 76);
    private static final int PURPLE = Color.rgb(111, 75, 169);
    private static final int LIGHT = Color.rgb(248, 245, 252);
    private static final int MUTED = Color.rgb(124, 108, 138);
    private static final int PINK = Color.rgb(234, 105, 160);
    private static final int WHITE = Color.WHITE;

    private static final class Project {
        String id = UUID.randomUUID().toString();
        String name;
        String uri = "";
        int rows = 0, target = 100, page = 0;
        float zoom = 1f, guide = .35f;
        JSONObject json() throws Exception {
            JSONObject o = new JSONObject();
            o.put("id", id); o.put("name", name); o.put("uri", uri);
            o.put("rows", rows); o.put("target", target); o.put("page", page);
            o.put("zoom", (double) zoom); o.put("guide", (double) guide);
            return o;
        }
        static Project from(JSONObject o) {
            Project p = new Project();
            p.id = o.optString("id", p.id);
            p.name = o.optString("name", "Project");
            p.uri = o.optString("uri", "");
            p.rows = Math.max(0, o.optInt("rows", 0));
            p.target = Math.max(1, o.optInt("target", 100));
            p.page = Math.max(0, o.optInt("page", 0));
            p.zoom = (float) Math.max(.75, Math.min(3, o.optDouble("zoom", 1)));
            p.guide = (float) Math.max(0, Math.min(1, o.optDouble("guide", .35)));
            return p;
        }
    }
    private Project current() {
        for (Project p : projects) if (p.id.equals(selectedId)) return p;
        return projects.get(0);
    }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(100, 53, 93));
        getWindow().setNavigationBarColor(Color.rgb(54, 33, 61));
        prefs = getSharedPreferences("projects_v1", MODE_PRIVATE);
        load();
        build();
    }
    private void load() {
        try {
            JSONArray a = new JSONArray(prefs.getString("projects", "[]"));
            for (int i = 0; i < a.length(); i++) {
                try { projects.add(Project.from(a.getJSONObject(i))); }
                catch (Exception badEntry) { android.util.Log.w("CrochetCounter", "Skipping invalid saved project", badEntry); }
            }
        } catch (Exception error) {
            android.util.Log.e("CrochetCounter", "Saved projects could not be parsed", error);
            // Do not overwrite a malformed preferences file before recovery.
        }
        if (projects.isEmpty()) {
            Project p = new Project(); p.name = getString(R.string.project_default);
            projects.add(p);
        }
        selectedId = prefs.getString("selected", projects.get(0).id);
        boolean found = false;
        for (Project p : projects) if (p.id.equals(selectedId)) found = true;
        if (!found) selectedId = projects.get(0).id;
        if (prefs.getString("projects", null) == null) save();
    }
    private void save() {
        JSONArray a = new JSONArray();
        for (Project p : projects) {
            try { a.put(p.json()); } catch (Exception ignored) { }
        }
        prefs.edit().putString("projects", a.toString()).putString("selected", selectedId).commit();
    }
    private int dp(float n) { return (int) (getResources().getDisplayMetrics().density * n + .5f); }
    private TextView text(String s, int size, int color) {
        TextView t = new TextView(this);
        t.setText(s); t.setTextSize(size); t.setTextColor(color);
        t.setGravity(Gravity.CENTER); t.setPadding(dp(8), dp(8), dp(8), dp(8));
        return t;
    }
    private Button button(String label, Runnable action) {
        Button b = new Button(this); b.setAllCaps(false);
        b.setText(label); b.setOnClickListener(v -> action.run()); return b;
    }
    private void add(LinearLayout l, View v) {
        l.addView(v, new LinearLayout.LayoutParams(-1, -2));
    }
    private GradientDrawable shape(int fill, int stroke, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radius));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }
    private GradientDrawable gradient(int first, int last, int radius) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[]{first,last});
        g.setCornerRadius(dp(radius));
        return g;
    }
    private TextView styled(String label, int size, int color, boolean heavy) {
        TextView t = text(label, size, color);
        if (heavy) t.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        t.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
        return t;
    }
    private LinearLayout stack() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }
    private LinearLayout row() {
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }
    private LinearLayout card() {
        LinearLayout l = stack();
        l.setPadding(dp(18),dp(18),dp(18),dp(18));
        l.setBackground(shape(WHITE, Color.rgb(235,226,244), 24));
        l.setElevation(dp(2));
        return l;
    }
    private void section(LinearLayout into, View view, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1,-2);
        lp.topMargin = dp(top);
        into.addView(view,lp);
    }
    private Button action(String label, int fill, int foreground, Runnable callback) {
        Button b = new Button(this);
        b.setText(label);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        b.setTextColor(foreground);
        b.setBackground(shape(fill,0,16));
        b.setMinHeight(dp(54));
        b.setPadding(dp(12),dp(10),dp(12),dp(10));
        b.setOnClickListener(v -> callback.run());
        return b;
    }
    private void addWeighted(LinearLayout row, View v, float weight, int margin) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,-2,weight);
        lp.setMargins(dp(margin),0,dp(margin),0);
        row.addView(v,lp);
    }
    private void build() {
        Project p = current();
        pageView = null; pdfImage = null; pdfFrame = null;
        getWindow().setStatusBarColor(Color.rgb(77,48,110));
        getWindow().setNavigationBarColor(Color.rgb(246,241,250));
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR);
        }
        root = stack();
        root.setPadding(dp(18),dp(14),dp(18),dp(30));
        root.setBackgroundColor(LIGHT);
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.setClipToPadding(false);
        sc.addView(root);
        setContentView(sc);

        LinearLayout header = row();
        TextView symbol = styled("🧶", 29, PURPLE, true);
        symbol.setGravity(Gravity.CENTER);
        symbol.setBackground(shape(Color.rgb(239,226,247),0,18));
        header.addView(symbol,new LinearLayout.LayoutParams(dp(54),dp(54)));
        LinearLayout headerWords = stack();
        add(headerWords,styled("Crochet Counter",23,INK,true));
        add(headerWords,styled("& PDF · Craft companion",12,MUTED,false));
        LinearLayout.LayoutParams words = new LinearLayout.LayoutParams(0,-2,1);
        words.leftMargin = dp(8);
        header.addView(headerWords,words);
        add(root,header);

        LinearLayout project = card();
        TextView cap = styled(getString(R.string.project).toUpperCase(java.util.Locale.getDefault()),11,MUTED,true);
        add(project,cap);
        LinearLayout choose = row();
        TextView projectName = styled(p.name + "  ▾",20,INK,true);
        choose.addView(projectName,new LinearLayout.LayoutParams(0,-2,1));
        addWeighted(choose,action("＋",Color.rgb(244,230,248),PURPLE,this::newProject),.23f,0);
        add(project,choose);
        projectName.setOnClickListener(v -> chooseProject());
        cap.setOnClickListener(v -> chooseProject());
        section(root,project,20);

        LinearLayout hero = stack();
        hero.setPadding(dp(18),dp(22),dp(18),dp(22));
        hero.setBackground(gradient(Color.rgb(110,74,164),Color.rgb(159,99,180),28));
        TextView kicker = styled(getString(R.string.rows).toUpperCase(java.util.Locale.getDefault()),14,Color.rgb(246,223,255),true);
        kicker.setGravity(Gravity.CENTER);
        add(hero,kicker);
        countView = text("",79,WHITE);
        countView.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        countView.setGravity(Gravity.CENTER);
        add(hero,countView);
        add(hero,text("✦  " + getString(R.string.target) + ": " + p.target,15,WHITE));
        section(root,hero,16);

        Button plus = action("+  " + getString(R.string.add_row).replace("+ ",""),PURPLE,WHITE,() -> {
            changeRows(1);
            if (countView != null) {
                countView.setScaleX(.94f); countView.setScaleY(.94f);
                countView.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
            }
        });
        plus.setTextSize(20);
        plus.setMinHeight(dp(74));
        plus.setBackground(gradient(Color.rgb(229,91,155),Color.rgb(201,73,140),20));
        section(root,plus,12);

        LinearLayout quick = row();
        addWeighted(quick,action("− 1",WHITE,INK,() -> changeRows(-1)),1,3);
        undoButton = action("↶ " + getString(R.string.undo),WHITE,PURPLE,this::undo);
        addWeighted(quick,undoButton,1,3);
        section(root,quick,10);

        LinearLayout progress = card();
        LinearLayout progressRow = row();
        progressRow.addView(styled("✦  " + getString(R.string.progress,p.rows,p.target),14,INK,true),
            new LinearLayout.LayoutParams(0,-2,1));
        TextView pct = styled(Math.min(100,p.rows * 100L / p.target) + "%",14,PURPLE,true);
        add(progressRow,pct);
        add(progress,progressRow);
        progressView = text("",1,Color.TRANSPARENT);
        progressView.setVisibility(View.GONE);
        add(progress,progressView);
        progressBar = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        progressBar.setProgressTintList(ColorStateList.valueOf(PINK));
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(240,229,245)));
        progressBar.setMinHeight(dp(10));
        section(progress,progressBar,12);
        LinearLayout progressBtns = row();
        addWeighted(progressBtns,action("◎  " + getString(R.string.target),Color.rgb(247,239,252),PURPLE,this::setTarget),1,2);
        addWeighted(progressBtns,action("↺  " + getString(R.string.reset),Color.rgb(247,239,252),INK,
            () -> new AlertDialog.Builder(this).setMessage(R.string.reset_confirm)
                .setPositiveButton(R.string.reset,(d,w)->changeRows(-current().rows))
                .setNegativeButton(R.string.cancel,null).show()),1,2);
        section(progress,progressBtns,10);
        section(root,progress,16);

        LinearLayout pdfCard = card();
        add(pdfCard,styled("📄  " + getString(R.string.open_pdf),20,INK,true));
        add(pdfCard,styled(getString(R.string.guide),12,MUTED,false));
        section(pdfCard,action(p.uri.isEmpty() ? "＋  " + getString(R.string.open_pdf)
            : "↻  " + getString(R.string.open_pdf),Color.rgb(240,230,248),PURPLE,this::pickPdf),10);
        if (p.uri.isEmpty()) {
            TextView empty = text(getString(R.string.no_pdf),14,MUTED);
            empty.setPadding(dp(16),dp(20),dp(16),dp(20));
            empty.setBackground(shape(Color.rgb(251,248,253),Color.rgb(239,230,244),16));
            section(pdfCard,empty,12);
        } else {
            pageView = text("",14,INK);
            section(pdfCard,pageView,8);
            LinearLayout pages = row();
            addWeighted(pages,action("◀  " + getString(R.string.previous_page),
                Color.rgb(245,237,250),PURPLE,()->changePage(-1)),1,2);
            addWeighted(pages,action(getString(R.string.next_page) + "  ▶",
                Color.rgb(245,237,250),PURPLE,()->changePage(1)),1,2);
            section(pdfCard,pages,6);
            LinearLayout zoom = row();
            addWeighted(zoom,action(getString(R.string.zoom_out),WHITE,PURPLE,()->changeZoom(-.25f)),1,3);
            addWeighted(zoom,action(getString(R.string.zoom_in),WHITE,PURPLE,()->changeZoom(.25f)),1,3);
            section(pdfCard,zoom,8);
            HorizontalScrollView horizontal = new HorizontalScrollView(this);
            horizontal.setFillViewport(false);
            pdfFrame = new FrameLayout(this);
            pdfImage = new ImageView(this);
            pdfImage.setAdjustViewBounds(true);
            pdfImage.setScaleType(ImageView.ScaleType.FIT_XY);
            pdfFrame.addView(pdfImage);
            View guide = new View(this);
            guide.setBackgroundColor(Color.argb(195,232,74,158));
            pdfFrame.addView(guide,new FrameLayout.LayoutParams(-1,dp(4)));
            final View guideView = guide;
            pdfFrame.setOnTouchListener((v,event) -> {
                if (event.getAction()==MotionEvent.ACTION_DOWN ||
                    event.getAction()==MotionEvent.ACTION_MOVE) {
                    p.guide = Math.max(0,Math.min(1,event.getY()/Math.max(1,pdfFrame.getHeight())));
                    guideView.setTranslationY(p.guide*Math.max(0,pdfFrame.getHeight()-dp(4)));
                    return true;
                }
                if(event.getAction()==MotionEvent.ACTION_UP){save();v.performClick();return true;}
                return false;
            });
            horizontal.addView(pdfFrame);
            section(pdfCard,horizontal,12);
        }
        section(root,pdfCard,16);
        TextView manage = text("⚙  " + getString(R.string.delete),13,MUTED);
        manage.setOnClickListener(v->deleteProject());
        section(root,manage,14);
        updateCounter();
        if(!p.uri.isEmpty()) renderPdf();
    }
    private void updateCounter() {
        Project p = current();
        if (countView != null) countView.setText(String.valueOf(p.rows));
        if (progressView != null) progressView.setText(getString(R.string.progress, p.rows, p.target));
        if (progressBar != null) progressBar.setProgress((int)Math.min(1000, 1000L * p.rows / p.target));
        if (undoButton != null) undoButton.setEnabled(hasUndo);
    }
    private void changeRows(int amount) {
        Project p = current();
        int newRows = (int) Math.max(0, Math.min(Integer.MAX_VALUE, (long) p.rows + amount));
        if (newRows == p.rows) return;
        lastValue = p.rows; hasUndo = true;
        p.rows = newRows;
        save(); updateCounter();
    }
    private void undo() {
        if (!hasUndo) return;
        Project p = current();
        p.rows = lastValue;
        hasUndo = false;
        save(); updateCounter();
    }
    private void setTarget() {
        EditText e = new EditText(this);
        e.setInputType(2); e.setText(String.valueOf(current().target));
        new AlertDialog.Builder(this).setTitle(R.string.target).setView(e)
            .setPositiveButton(R.string.save, (d,w) -> {
                try {
                    int n = Integer.parseInt(e.getText().toString());
                    if (n < 1) throw new NumberFormatException();
                    current().target = n; save(); build();
                } catch (Exception ex) { Toast.makeText(this, R.string.invalid_target, Toast.LENGTH_SHORT).show(); }
            }).setNegativeButton(R.string.cancel, null).show();
    }
    private void newProject() {
        EditText e = new EditText(this); e.setSingleLine(true);
        new AlertDialog.Builder(this).setTitle(R.string.new_project).setView(e)
            .setPositiveButton(R.string.create, (d,w) -> {
                String name = e.getText().toString().trim();
                if (name.isEmpty()) return;
                Project p = new Project(); p.name = name;
                projects.add(p); selectedId = p.id;
                hasUndo = false; closePdf(); save(); build();
            }).setNegativeButton(R.string.cancel, null).show();
    }
    private void chooseProject() {
        String[] names = new String[projects.size()];
        for (int i = 0; i < names.length; i++) names[i] = projects.get(i).name;
        new AlertDialog.Builder(this).setTitle(R.string.select_project)
            .setItems(names, (d, which) -> {
                selectedId = projects.get(which).id;
                hasUndo = false; closePdf(); save(); build();
            }).show();
    }
    private void deleteProject() {
        new AlertDialog.Builder(this).setMessage(R.string.delete_confirm)
            .setPositiveButton(R.string.delete, (d,w) -> {
                closePdf(); projects.remove(current());
                if (projects.isEmpty()) {
                    Project p = new Project(); p.name = getString(R.string.project_default);
                    projects.add(p);
                }
                selectedId = projects.get(0).id; hasUndo = false; save(); build();
            }).setNegativeButton(R.string.cancel, null).show();
    }
    private void pickPdf() {
        Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("application/pdf");
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(intent, PICK_PDF);
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request == PICK_PDF && result == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                getContentResolver().takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
            } catch (SecurityException ignored) { }
            closePdf();
            Project p = current(); p.uri = uri.toString();
            p.page = 0; p.zoom = 1; p.guide = .35f;
            save(); build();
        }
    }
    private void changePage(int step) {
        if (renderer == null) return;
        Project p = current();
        p.page = Math.max(0, Math.min(renderer.getPageCount()-1, p.page+step));
        save(); renderPdf();
    }
    private void changeZoom(float step) {
        Project p = current();
        p.zoom = Math.max(.75f, Math.min(3f, p.zoom + step));
        save(); renderPdf();
    }
    private void renderPdf() {
        Project p = current();
        if (p.uri.isEmpty() || pdfImage == null) return;
        try {
            if (renderer == null || !openedUri.equals(p.uri)) {
                closePdf();
                file = getContentResolver().openFileDescriptor(Uri.parse(p.uri), "r");
                if (file == null) throw new Exception("Missing PDF descriptor");
                renderer = new PdfRenderer(file);
                openedUri = p.uri;
            }
            if (renderer.getPageCount() <= 0) throw new Exception("Empty PDF");
            p.page = Math.max(0, Math.min(p.page, renderer.getPageCount()-1));
            try (PdfRenderer.Page page = renderer.openPage(p.page)) {
                float scale = Math.min(2.2f, getResources().getDisplayMetrics().widthPixels / (float) page.getWidth());
                int w = Math.min(2400, Math.max(1, (int)(page.getWidth() * scale * p.zoom)));
                int h = Math.min(3200, Math.max(1, (int)(page.getHeight() * scale * p.zoom)));
                // Cap bitmap pixels to keep older phones safe from excessive PDF zoom.
                long area = (long)w * h;
                if (area > 4000000L) {
                    double shrink = Math.sqrt(4000000.0 / area);
                    w = Math.max(1, (int)(w * shrink));
                    h = Math.max(1, (int)(h * shrink));
                }
                Bitmap bm = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                bm.eraseColor(Color.WHITE);
                page.render(bm,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                Bitmap previous = visiblePdfBitmap;
                pdfImage.setImageBitmap(bm);
                visiblePdfBitmap = bm;
                if (previous != null && previous != bm && !previous.isRecycled()) previous.recycle();
                ViewGroup.LayoutParams lp = pdfImage.getLayoutParams();
                lp.width = w; lp.height = h; pdfImage.setLayoutParams(lp);
                pdfFrame.getLayoutParams().width = w;
                pdfFrame.getLayoutParams().height = h;
                pdfFrame.requestLayout();
                pdfFrame.post(() -> {
                    if (pdfFrame.getChildCount() > 1)
                        pdfFrame.getChildAt(1).setTranslationY(current().guide * Math.max(0,pdfFrame.getHeight()-dp(4)));
                });
            }
            pageView.setText(getString(R.string.page, p.page+1, renderer.getPageCount()));
        } catch (Exception ex) {
            closePdf();
            Toast.makeText(this, R.string.pdf_error, Toast.LENGTH_LONG).show();
            if (pageView != null) pageView.setText(getString(R.string.pdf_error));
        }
    }
    private void closePdf() {
        if (pdfImage != null) pdfImage.setImageDrawable(null);
        if (visiblePdfBitmap != null && !visiblePdfBitmap.isRecycled()) visiblePdfBitmap.recycle();
        visiblePdfBitmap = null;
        if (renderer != null) { try { renderer.close(); } catch (Exception ignored) {} renderer = null; }
        if (file != null) { try { file.close(); } catch (Exception ignored) {} file = null; }
        openedUri = "";
    }
    @Override protected void onDestroy() { closePdf(); super.onDestroy(); }
}
