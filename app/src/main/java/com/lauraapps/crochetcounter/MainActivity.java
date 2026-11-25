package com.lauraapps.crochetcounter;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.Context;
import android.content.res.Configuration;
import android.text.InputType;
import java.util.Locale;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.content.res.ColorStateList;
import android.graphics.drawable.Drawable;
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
    private AdsController ads;
    private TextView countView, progressView, pageView, percentageView, goalCelebration;
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
        int rows = 0, target = 20, page = 0;
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
            p.target = Math.max(1, o.optInt("target", 20));
            p.page = Math.max(0, o.optInt("page", 0));
            p.zoom = (float) Math.max(.75, Math.min(3, o.optDouble("zoom", 1)));
            p.guide = (float) Math.max(0, Math.min(1, o.optDouble("guide", .35)));
            return p;
        }
    }
    private String displayProjectName(Project p) {
        if (p.name.equals("My first project") ||
            p.name.equals("Mi primer proyecto") ||
            p.name.equals("Meu primeiro projeto")) {
            return getString(R.string.project_default);
        }
        return p.name;
    }
    private Project current() {
        for (Project p : projects) if (p.id.equals(selectedId)) return p;
        return projects.get(0);
    }
    @Override protected void attachBaseContext(Context base) {
        String choice = base.getSharedPreferences("ui_settings", MODE_PRIVATE)
            .getString("language", "system");
        if (!choice.equals("es") && !choice.equals("en") && !choice.equals("pt")) {
            super.attachBaseContext(base);
            return;
        }
        Configuration config = new Configuration(base.getResources().getConfiguration());
        config.setLocale(new Locale(choice));
        super.attachBaseContext(base.createConfigurationContext(config));
    }
    private String languageCode() {
        return getSharedPreferences("ui_settings", MODE_PRIVATE).getString("language", "system");
    }
    private void changeLanguage() {
        final String[] choices = {getString(R.string.language_system), "Español", "English", "Português"};
        final String[] codes = {"system", "es", "en", "pt"};
        int selected = 0;
        for (int i = 0; i < codes.length; i++) if (codes[i].equals(languageCode())) selected = i;
        new AlertDialog.Builder(this)
            .setTitle(R.string.language_title)
            .setSingleChoiceItems(choices, selected, (dialog, which) -> {
                dialog.dismiss();
                if (!codes[which].equals(languageCode())) {
                    getSharedPreferences("ui_settings", MODE_PRIVATE).edit()
                        .putString("language", codes[which]).apply();
                    closePdf();
                    recreate();
                }
            })
            .setNegativeButton(R.string.cancel, null)
            .show();
    }
    @Override public void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().setStatusBarColor(Color.rgb(100, 53, 93));
        getWindow().setNavigationBarColor(Color.rgb(54, 33, 61));
        prefs = getSharedPreferences("projects_v1", MODE_PRIVATE);
        load();
        ads = new AdsController(this);
        build();
        ads.start();
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
        l.setPadding(dp(16),dp(14),dp(16),dp(14));
        l.setBackground(shape(WHITE, Color.rgb(235,226,244), 24));
        l.setElevation(dp(1));
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
        b.setMinimumHeight(dp(48));
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
        root.setPadding(dp(16),dp(10),dp(16),dp(24));
        root.setBackgroundColor(LIGHT);
        ScrollView sc = new ScrollView(this);
        sc.setFillViewport(true);
        sc.setClipToPadding(false);
        sc.addView(root);
        LinearLayout outer = stack();
        outer.setBackgroundColor(LIGHT);
        outer.setFitsSystemWindows(true);
        outer.addView(sc, new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout adHost = stack();
        adHost.setPadding(0,dp(12),0,dp(8));
        outer.addView(adHost,new LinearLayout.LayoutParams(-1,-2));
        Button privacy = button(getString(R.string.ad_privacy), () -> {});
        add(adHost,privacy);
        setContentView(outer);
        if (ads != null) ads.bind(adHost,privacy);

        LinearLayout header = row();
        ImageView symbol = new ImageView(this);
        symbol.setImageResource(R.drawable.ic_launcher);
        symbol.setScaleType(ImageView.ScaleType.CENTER_CROP);
        symbol.setContentDescription(getString(R.string.app_name));
        header.addView(symbol,new LinearLayout.LayoutParams(dp(54),dp(54)));
        LinearLayout headerWords = stack();
        add(headerWords,styled(getString(R.string.header_title),21,INK,true));
        add(headerWords,styled(getString(R.string.header_subtitle),12,MUTED,false));
        LinearLayout.LayoutParams words = new LinearLayout.LayoutParams(0,-2,1);
        words.leftMargin = dp(8);
        header.addView(headerWords,words);
        Button language = action("🌐",Color.rgb(241,230,249),PURPLE,this::changeLanguage);
        language.setContentDescription(getString(R.string.language_title));
        language.setTextSize(19);
        header.addView(language,new LinearLayout.LayoutParams(dp(54),dp(48)));
        add(root,header);

        LinearLayout project = card();
        TextView cap = styled(getString(R.string.project).toUpperCase(java.util.Locale.getDefault()),11,MUTED,true);
        add(project,cap);
        LinearLayout choose = row();
        TextView projectName = styled(displayProjectName(p) + "  ▾",20,INK,true);
        choose.addView(projectName,new LinearLayout.LayoutParams(0,-2,1));
        addWeighted(choose,action("＋",Color.rgb(244,230,248),PURPLE,this::newProject),.23f,0);
        add(project,choose);
        projectName.setOnClickListener(v -> chooseProject());
        cap.setOnClickListener(v -> chooseProject());
        section(root,project,12);

        LinearLayout hero = stack();
        hero.setPadding(dp(18),dp(14),dp(18),dp(14));
        hero.setBackground(gradient(Color.rgb(110,74,164),Color.rgb(159,99,180),28));
        TextView kicker = styled(getString(R.string.rows).toUpperCase(java.util.Locale.getDefault()),14,Color.rgb(246,223,255),true);
        kicker.setGravity(Gravity.CENTER);
        add(hero,kicker);
        countView = text("",62,WHITE);
        countView.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        countView.setGravity(Gravity.CENTER);
        add(hero,countView);
        Button editGoal = action("✎  " + getString(R.string.goal_value,p.target),Color.rgb(126,82,166),WHITE,this::setTarget);
        editGoal.setContentDescription(getString(R.string.change_target));
        add(hero,editGoal);
        goalCelebration = text(getString(R.string.goal_celebration), 14, WHITE);
        goalCelebration.setTypeface(Typeface.DEFAULT, Typeface.BOLD);
        goalCelebration.setVisibility(p.rows >= p.target ? View.VISIBLE : View.GONE);
        add(hero, goalCelebration);
        section(root,hero,12);

        Button plus = action("+  " + getString(R.string.add_row).replace("+ ",""),PURPLE,WHITE,() -> {
            changeRows(1);
            if (countView != null) {
                countView.setScaleX(.94f); countView.setScaleY(.94f);
                countView.animate().scaleX(1f).scaleY(1f).setDuration(150).start();
            }
        });
        plus.setTextSize(20);
        plus.setMinimumHeight(dp(62));
        plus.setBackground(gradient(Color.rgb(229,91,155),Color.rgb(201,73,140),20));
        section(root,plus,10);

        LinearLayout quick = row();
        addWeighted(quick,action("− 1",WHITE,INK,() -> changeRows(-1)),1,3);
        undoButton = action("↶ " + getString(R.string.undo),WHITE,PURPLE,this::undo);
        addWeighted(quick,undoButton,1,3);
        section(root,quick,8);

        LinearLayout progress = card();
        LinearLayout progressRow = row();
        progressView = styled("✦  " + getString(R.string.progress,p.rows,p.target),14,INK,true);
        progressView.setPadding(0,0,dp(4),0);
        progressRow.addView(progressView,
            new LinearLayout.LayoutParams(0,dp(30),1));
        percentageView = styled(Math.min(100,p.rows * 100L / p.target) + "%",14,PURPLE,true);
        percentageView.setGravity(Gravity.CENTER);
        percentageView.setPadding(dp(8),dp(4),dp(8),dp(4));
        percentageView.setBackground(shape(Color.rgb(249,234,246),0,12));
        progressRow.addView(percentageView,new LinearLayout.LayoutParams(-2,dp(30)));
        progress.addView(progressRow,new LinearLayout.LayoutParams(-1,dp(34)));
        progressBar = new ProgressBar(this,null,android.R.attr.progressBarStyleHorizontal);
        progressBar.setMax(1000);
        progressBar.setProgressTintList(ColorStateList.valueOf(PINK));
        progressBar.setProgressBackgroundTintList(ColorStateList.valueOf(Color.rgb(240,229,245)));
        // Android's horizontal ProgressBar has device-dependent intrinsic height.
        // An explicit 8dp layout height prevents it from stretching this card.
        LinearLayout.LayoutParams barParams = new LinearLayout.LayoutParams(-1,dp(8));
        barParams.topMargin = dp(8);
        progress.addView(progressBar,barParams);
        LinearLayout progressBtns = row();
        addWeighted(progressBtns,action("✎  " + getString(R.string.change_target),Color.rgb(247,239,252),PURPLE,this::setTarget),1,2);
        addWeighted(progressBtns,action("↺  " + getString(R.string.reset),Color.rgb(247,239,252),INK,
            () -> new AlertDialog.Builder(this).setMessage(R.string.reset_confirm)
                .setPositiveButton(R.string.reset,(d,w)->changeRows(-current().rows))
                .setNegativeButton(R.string.cancel,null).show()),1,2);
        section(progress,progressBtns,9);
        section(root,progress,12);

        LinearLayout pdfCard = card();
        LinearLayout pdfHeading = row();
        TextView pdfTitle = styled("📄  " + getString(R.string.pdf_section_title),18,INK,true);
        pdfHeading.addView(pdfTitle,new LinearLayout.LayoutParams(0,-2,1));
        Button explain = action("ⓘ",Color.rgb(247,239,252),PURPLE,this::explainPdf);
        explain.setContentDescription(getString(R.string.pdf_help_title));
        explain.setMinimumHeight(dp(40));
        pdfHeading.addView(explain,new LinearLayout.LayoutParams(dp(46),dp(40)));
        add(pdfCard,pdfHeading);
        TextView description = styled(getString(R.string.pdf_purpose),13,MUTED,false);
        description.setLineSpacing(dp(2),1f);
        add(pdfCard,description);
        if (!p.uri.isEmpty()) add(pdfCard,styled(getString(R.string.guide),12,PURPLE,false));
        section(pdfCard,action(p.uri.isEmpty() ? "＋  " + getString(R.string.open_pdf)
            : "↻  " + getString(R.string.open_pdf),Color.rgb(240,230,248),PURPLE,this::pickPdf),10);
        if (p.uri.isEmpty()) {
            TextView empty = text(getString(R.string.no_pdf),14,MUTED);
            empty.setPadding(dp(12),dp(10),dp(12),dp(10));
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
        section(root,pdfCard,12);
        TextView manage = text("⚙  " + getString(R.string.delete),13,MUTED);
        manage.setOnClickListener(v->deleteProject());
        section(root,manage,14);
        updateCounter();
        if(!p.uri.isEmpty()) renderPdf();
    }
    private void explainPdf() {
        new AlertDialog.Builder(this)
            .setTitle(R.string.pdf_help_title)
            .setMessage(R.string.pdf_help_body)
            .setPositiveButton(android.R.string.ok,null)
            .show();
    }
    private void updateCounter() {
        Project p = current();
        if (countView != null) countView.setText(String.valueOf(p.rows));
        if (progressView != null) progressView.setText("✦  " + getString(R.string.progress, p.rows, p.target));
        if (percentageView != null) percentageView.setText(Math.min(100,100L*p.rows/p.target) + "%");
        if (progressBar != null) progressBar.setProgress((int)Math.min(1000, 1000L * p.rows / p.target));
        if (undoButton != null) undoButton.setEnabled(hasUndo);
        if (goalCelebration != null) goalCelebration.setVisibility(p.rows >= p.target ? View.VISIBLE : View.GONE);
    }
    private void changeRows(int amount) {
        Project p = current();
        int newRows = (int) Math.max(0, Math.min(Integer.MAX_VALUE, (long) p.rows + amount));
        if (newRows == p.rows) return;
        lastValue = p.rows; hasUndo = true;
        boolean reached = p.rows < p.target && newRows >= p.target;
        p.rows = newRows;
        save(); updateCounter();
        if (reached && goalCelebration != null) {
            goalCelebration.animate().cancel();
            goalCelebration.setAlpha(0f);
            goalCelebration.setScaleX(.86f);
            goalCelebration.setScaleY(.86f);
            goalCelebration.animate().alpha(1f).scaleX(1f).scaleY(1f).setDuration(420).start();
            goalCelebration.announceForAccessibility(getString(R.string.goal_celebration));
        }
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
        e.setInputType(InputType.TYPE_CLASS_NUMBER);
        e.setSelectAllOnFocus(true);
        e.setSingleLine(true);
        e.setText(String.valueOf(current().target));
        e.setHint(R.string.target_hint);
        new AlertDialog.Builder(this).setTitle(R.string.change_target).setMessage(R.string.target_explanation).setView(e)
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
        e.setHint(R.string.project_name);
        EditText goal = new EditText(this);
        goal.setInputType(InputType.TYPE_CLASS_NUMBER);
        goal.setSingleLine(true);
        goal.setHint(R.string.target_hint);
        goal.setText("20");
        LinearLayout fields = stack();
        fields.setPadding(dp(18),0,dp(18),0);
        add(fields,e);
        add(fields,goal);
        new AlertDialog.Builder(this).setTitle(R.string.new_project).setView(fields)
            .setPositiveButton(R.string.create, (d,w) -> {
                String name = e.getText().toString().trim();
                if (name.isEmpty()) { Toast.makeText(this,R.string.project_name,Toast.LENGTH_SHORT).show(); return; }
                int target;
                try { target = Integer.parseInt(goal.getText().toString().trim()); }
                catch (NumberFormatException ex) { target = 20; }
                if (target < 1) { Toast.makeText(this,R.string.invalid_target,Toast.LENGTH_SHORT).show(); return; }
                Project p = new Project(); p.name = name; p.target = target;
                projects.add(p); selectedId = p.id;
                hasUndo = false; closePdf(); save(); build();
                if (ads != null) root.postDelayed(() -> { if (ads != null && !isFinishing() && !isDestroyed()) ads.projectCreated(); }, 350);
            }).setNegativeButton(R.string.cancel, null).show();
    }
    private void chooseProject() {
        String[] names = new String[projects.size()];
        for (int i = 0; i < names.length; i++) names[i] = displayProjectName(projects.get(i));
        new AlertDialog.Builder(this).setTitle(R.string.select_project)
            .setItems(names, (d, which) -> {
                boolean changed = !selectedId.equals(projects.get(which).id);
                selectedId = projects.get(which).id;
                hasUndo = false; closePdf(); save(); build();
                if (changed && ads != null) root.postDelayed(() -> { if (ads != null && !isFinishing()) ads.projectTransition(); }, 350);
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
    @Override protected void onPause() { if (ads != null) ads.pause(); super.onPause(); }
    @Override protected void onResume() { super.onResume(); if (ads != null) ads.resume(); }
    @Override protected void onDestroy() { if (ads != null) { ads.destroy(); ads = null; } closePdf(); super.onDestroy(); }
}
