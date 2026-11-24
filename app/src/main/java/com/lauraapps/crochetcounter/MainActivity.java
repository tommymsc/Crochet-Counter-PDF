package com.lauraapps.crochetcounter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
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
    private ImageView pdfImage;
    private FrameLayout pdfFrame;
    private PdfRenderer renderer;
    private ParcelFileDescriptor file;
    private String openedUri = "";
    private int lastValue = -1;
    private boolean hasUndo = false;

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
            for (int i = 0; i < a.length(); i++) projects.add(Project.from(a.getJSONObject(i)));
        } catch (Exception ignored) { projects.clear(); }
        if (projects.isEmpty()) {
            Project p = new Project(); p.name = getString(R.string.project_default);
            projects.add(p);
        }
        selectedId = prefs.getString("selected", projects.get(0).id);
        boolean found = false;
        for (Project p : projects) if (p.id.equals(selectedId)) found = true;
        if (!found) selectedId = projects.get(0).id;
        save();
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
    private void build() {
        Project p = current();
        root = new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(14), dp(8), dp(14), dp(18));
        root.setBackgroundColor(Color.rgb(255, 249, 253));
        ScrollView sc = new ScrollView(this); sc.setFillViewport(true);
        sc.addView(root); setContentView(sc);

        TextView heading = text("🧶  " + getString(R.string.app_name), 24, Color.rgb(99, 44, 99));
        add(root, heading);
        add(root, button(getString(R.string.project) + ": " + p.name + "  ▾", this::chooseProject));
        add(root, button("+ " + getString(R.string.new_project), this::newProject));
        countView = text("", 66, Color.rgb(89, 37, 86)); add(root, countView);
        add(root, text(getString(R.string.rows), 17, Color.DKGRAY));
        add(root, button(getString(R.string.add_row), () -> changeRows(1)));
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        Button undo = button(getString(R.string.undo), this::undo);
        Button reset = button(getString(R.string.reset), () -> new AlertDialog.Builder(this)
            .setMessage(R.string.reset_confirm).setPositiveButton(R.string.reset, (d,w) -> changeRows(-current().rows))
            .setNegativeButton(R.string.cancel, null).show());
        actions.addView(undo, new LinearLayout.LayoutParams(0, -2, 1));
        actions.addView(reset, new LinearLayout.LayoutParams(0, -2, 1));
        add(root, actions);
        progressView = text("", 15, Color.DKGRAY); add(root, progressView);
        add(root, button(getString(R.string.target) + ": " + p.target, this::setTarget));
        add(root, button("📄  " + getString(R.string.open_pdf), this::pickPdf));

        if (p.uri.isEmpty()) add(root, text(getString(R.string.no_pdf), 15, Color.GRAY));
        else {
            pageView = text("", 15, Color.DKGRAY); add(root, pageView);
            LinearLayout pages = new LinearLayout(this);
            pages.addView(button("◀ " + getString(R.string.previous_page), () -> changePage(-1)),
                new LinearLayout.LayoutParams(0, -2, 1));
            pages.addView(button(getString(R.string.next_page) + " ▶", () -> changePage(1)),
                new LinearLayout.LayoutParams(0, -2, 1));
            add(root, pages);
            LinearLayout zoom = new LinearLayout(this);
            zoom.addView(button(getString(R.string.zoom_out), () -> changeZoom(-.25f)),
                new LinearLayout.LayoutParams(0, -2, 1));
            zoom.addView(button(getString(R.string.zoom_in), () -> changeZoom(.25f)),
                new LinearLayout.LayoutParams(0, -2, 1));
            add(root, zoom);
            add(root, text(getString(R.string.guide), 13, Color.GRAY));
            HorizontalScrollView horizontal = new HorizontalScrollView(this);
            pdfFrame = new FrameLayout(this);
            pdfImage = new ImageView(this); pdfImage.setAdjustViewBounds(true);
            pdfImage.setScaleType(ImageView.ScaleType.FIT_XY);
            pdfFrame.addView(pdfImage);
            View guide = new View(this); guide.setBackgroundColor(Color.argb(200, 238, 45, 147));
            pdfFrame.addView(guide, new FrameLayout.LayoutParams(-1, dp(4)));
            final View guideView = guide;
            pdfFrame.setOnTouchListener((v,event) -> {
                if (event.getAction() == MotionEvent.ACTION_DOWN || event.getAction() == MotionEvent.ACTION_MOVE) {
                    Project pr = current();
                    pr.guide = Math.max(0, Math.min(1, event.getY() / Math.max(1, pdfFrame.getHeight())));
                    guideView.setTranslationY(pr.guide * Math.max(0, pdfFrame.getHeight()-dp(4)));
                    if (event.getAction() == MotionEvent.ACTION_UP) save();
                    return true;
                }
                if (event.getAction() == MotionEvent.ACTION_UP) { save(); return true; }
                return true;
            });
            horizontal.addView(pdfFrame);
            add(root, horizontal);
            renderPdf();
        }
        add(root, button(getString(R.string.delete), this::deleteProject));
        updateCounter();
    }
    private void updateCounter() {
        Project p = current();
        if (countView != null) countView.setText(String.valueOf(p.rows));
        if (progressView != null) progressView.setText(getString(R.string.progress, p.rows, p.target));
    }
    private void changeRows(int amount) {
        Project p = current();
        lastValue = p.rows; hasUndo = true;
        p.rows = (int) Math.max(0, Math.min(Integer.MAX_VALUE, (long) p.rows + amount));
        save(); updateCounter();
    }
    private void undo() {
        if (!hasUndo) return;
        Project p = current(); int before = p.rows;
        p.rows = lastValue; lastValue = before;
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
                Bitmap bm = Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888);
                bm.eraseColor(Color.WHITE);
                page.render(bm,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY);
                pdfImage.setImageBitmap(bm);
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
        if (renderer != null) { try { renderer.close(); } catch (Exception ignored) {} renderer = null; }
        if (file != null) { try { file.close(); } catch (Exception ignored) {} file = null; }
        openedUri = "";
    }
    @Override protected void onDestroy() { closePdf(); super.onDestroy(); }
}
