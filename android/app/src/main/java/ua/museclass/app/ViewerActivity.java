package ua.museclass.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

import ua.museclass.app.api.Dto;
import ua.museclass.app.sheet.SheetColors;
import ua.museclass.app.sheet.SheetPainter;
import ua.museclass.app.sheet.SystemView;
import ua.museclass.app.ui.Page;
import ua.museclass.app.ui.Uk;
import ua.museclass.app.ui.PillSwitch;
import ua.museclass.app.ui.Toasts;
import ua.museclass.engraving.Sheet;
import ua.museclass.musicxml.MusicXmlReader;
import ua.museclass.musicxml.Part;
import ua.museclass.musicxml.Score;

/**
 * Переглядач (renderViewer): верхня панель з кнопками, Партитура / Партія,
 * рядок партій, аркуш з нашим рендером, нижня панель плеєра, шторки
 * «Відтворення» і «Партії». Справжнє: ноти партії, ★ (/me/saved), вибір
 * партії, номери тактів, «тримати екран». Звуку ще немає — ▶ і метроном
 * лише кнопки; режим «Партитура», експорт, олівець, витягання — заглушки.
 */
public class ViewerActivity extends BaseActivity {
    public static final String EXTRA_ID = "scoreId";
    public static final String EXTRA_TITLE = "title";

    /** Одиниця верстки в мм: проміжок 1,4 мм (див. android.md, «Рендер»). */
    private static final float UNIT_MM = 0.14f;
    private static final String PREFS = "player";

    private final Adapter adapter = new Adapter();
    private SharedPreferences prefs;
    private String id;
    private Dto.ScoreView view;
    private Score score;
    private List<String> mine = new ArrayList<>();
    private int part;
    private boolean saved;
    private boolean pencil;
    private int tempo = 96;
    private int laidWidth = -1;
    private float unitPx;
    private int sidePx;
    private SheetPainter painter;

    private TextView title, sub, save, pencilBtn, partsBtn, vsetBtn, metroBtn, bpm, error;
    private LinearLayout modebar, partbar, panelIn;
    private View panel;
    private RecyclerView sheet;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_viewer);
        prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        id = getIntent().getStringExtra(EXTRA_ID);

        View column = findViewById(R.id.column);
        View dock = findViewById(R.id.dock);
        panelIn = findViewById(R.id.panel_in);
        float d = getResources().getDisplayMetrics().density;
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.root), (v, insets) -> {
            Insets b = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            column.setPadding(b.left, b.top, b.right, 0);
            dock.setPadding(dock.getPaddingLeft(), Math.round(10 * d), dock.getPaddingRight(), Math.round(10 * d) + b.bottom);
            panelIn.setPadding(panelIn.getPaddingLeft(), panelIn.getPaddingTop(), panelIn.getPaddingRight(),
                    Math.round(16 * d) + b.bottom);
            return WindowInsetsCompat.CONSUMED;
        });

        title = findViewById(R.id.title);
        sub = findViewById(R.id.sub);
        save = findViewById(R.id.save);
        pencilBtn = findViewById(R.id.pencil);
        partsBtn = findViewById(R.id.parts);
        vsetBtn = findViewById(R.id.vset);
        metroBtn = findViewById(R.id.metro);
        bpm = findViewById(R.id.bpm);
        error = findViewById(R.id.error);
        modebar = findViewById(R.id.modebar);
        partbar = findViewById(R.id.partbar);
        panel = findViewById(R.id.panel);
        sheet = findViewById(R.id.sheet);
        progress = findViewById(R.id.progress);
        title.setText(getIntent().getStringExtra(EXTRA_TITLE));

        painter = new SheetPainter(SheetColors.of(this));
        findViewById(R.id.page).setBackgroundColor(painter.colors().paper);
        error.setTextColor(painter.colors().ink);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        unitPx = UNIT_MM * dm.xdpi / 25.4f;
        sidePx = Math.round(12 * d);
        sheet.setLayoutManager(new LinearLayoutManager(this));
        sheet.setAdapter(adapter);
        sheet.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> sheet.post(this::relayout));

        findViewById(R.id.close).setOnClickListener(v -> finish());
        findViewById(R.id.export).setOnClickListener(v -> toast(R.string.stub_export));
        pencilBtn.setOnClickListener(v -> {
            pencil = !pencil;
            pencilBtn.setSelected(pencil);
            toast(pencil ? R.string.v_pencil_on : R.string.v_pencil_off);
        });
        save.setOnClickListener(v -> toggleSave());
        partsBtn.setOnClickListener(v -> showPanel(partsBtn.isSelected() ? null : "parts"));
        vsetBtn.setOnClickListener(v -> showPanel(vsetBtn.isSelected() ? null : "vset"));
        panel.setOnClickListener(v -> showPanel(null));
        findViewById(R.id.play).setOnClickListener(v -> toast(R.string.stub_play));
        metroBtn.setSelected(prefs.getBoolean("metro", false));
        metroBtn.setOnClickListener(v -> {
            setPref("metro", !prefs.getBoolean("metro", false));
            metroBtn.setSelected(prefs.getBoolean("metro", false));
        });
        SeekBar tempoBar = findViewById(R.id.tempo);
        tempoBar.setProgress(tempo - 40);
        bpm.setText(getString(R.string.v_bpm, tempo));
        tempoBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar s, int p, boolean user) {
                tempo = 40 + p;
                bpm.setText(getString(R.string.v_bpm, tempo));
            }

            @Override
            public void onStartTrackingTouch(SeekBar s) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar s) {
            }
        });
        findViewById(R.id.mode_score).setOnClickListener(v -> toast(R.string.stub_score_mode));
        findViewById(R.id.mode_part).setSelected(true);
        keepAwake();
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                if (panel.getVisibility() == View.VISIBLE) showPanel(null);
                else finish();
            }
        });

        background(() -> {
            Object[] r = new Object[3];
            r[0] = api().score(id);
            r[1] = MusicXmlReader.read(app().scoreFiles().get(id));
            r[2] = api().me();
            return r;
        }, r -> {
            view = (Dto.ScoreView) r[0];
            score = (Score) r[1];
            Dto.Profile me = (Dto.Profile) r[2];
            mine = me.instruments == null ? new ArrayList<>() : me.instruments;
            saved = view.score.saved;
            part = firstMine();
            progress.setVisibility(View.GONE);
            title.setText(view.score.title);
            showAll();
        }, e -> {
            progress.setVisibility(View.GONE);
            error.setText(messageOf(e));
            error.setVisibility(View.VISIBLE);
        });
    }

    // ---- дані ----

    /** Код інструмента партії за позицією — від сервера (він їх впізнає). */
    private String partCode(int i) {
        if (view == null || view.parts == null) return null;
        for (Dto.PartInfo p : view.parts) if (p.position == i) return p.instrument;
        return null;
    }

    private boolean isMine(int i) {
        String c = partCode(i);
        return c != null && mine.contains(c);
    }

    /** Як openScore у прототипі: відкривається партія під мій інструмент, інакше перша. */
    private int firstMine() {
        for (int i = 0; i < score.parts().size(); i++) if (isMine(i)) return i;
        return 0;
    }

    private String author() {
        Dto.ScoreInfo s = view.score;
        if (s.composer != null && !s.composer.isEmpty()) return s.composer;
        if (s.arranger != null && !s.arranger.isEmpty()) return s.arranger;
        return s.ownerName == null ? "" : s.ownerName;
    }

    /** Стрій для підпису: «звучить на 2 півтони нижче»; null — у реальній тональності. */
    private String sounding(Part p) {
        int t = p.transpose();
        if (t == 0) return null;
        int n = Math.abs(t);
        // transpose() — зі знаком прототипу: додатне — звучить нижче за написане
        return getString(t > 0 ? R.string.v_sounds_lower : R.string.v_sounds_higher,
                Uk.plural(this, R.plurals.semitones, n));
    }

    // ---- малювання ----

    private void showAll() {
        Part p = score.parts().get(part);
        String s = author() + " · " + p.name();
        String snd = sounding(p);
        if (snd != null) s += " · " + snd;
        sub.setText(s);
        save.setText(saved ? "★" : "☆");
        modebar.setVisibility(score.parts().size() > 1 ? View.VISIBLE : View.GONE);
        ((TextView) findViewById(R.id.mhint)).setText(p.name());

        partbar.removeAllViews();
        float d = getResources().getDisplayMetrics().density;
        for (int i = 0; i < score.parts().size(); i++) {
            TextView c = new TextView(this, null, 0, R.style.Mc_PartChip);
            c.setText(score.parts().get(i).name() + (isMine(i) ? " ·" : ""));
            c.setSelected(i == part);
            final int k = i;
            c.setOnClickListener(v -> choose(k));
            addChip(c, d);
        }
        TextView add = new TextView(this, null, 0, R.style.Mc_PartChip);
        add.setBackgroundResource(R.drawable.bg_partchip_add);
        add.setTextColor(ContextCompat.getColor(this, R.color.peri));
        add.setText(R.string.v_add_part);
        add.setOnClickListener(v -> toast(R.string.stub_add_part));
        addChip(add, d);

        laidWidth = -1;
        relayout();
    }

    private void addChip(TextView c, float d) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (partbar.getChildCount() > 0) lp.setMarginStart(Math.round(7 * d));
        partbar.addView(c, lp);
    }

    private void choose(int i) {
        part = i;
        showPanel(null);
        showAll();
        sheet.scrollToPosition(0);
    }

    private void relayout() {
        int w = sheet.getWidth();
        if (score == null || w == 0 || w == laidWidth) return;
        laidWidth = w;
        Sheet.Options o = new Sheet.Options();
        o.barNumbers = prefs.getBoolean("barnum", true);
        double units = (w - 2.0 * sidePx) / unitPx;
        adapter.set(Sheet.layout(score.parts().get(part), units, o));
    }

    // ---- кнопки ----

    private void toast(int text) {
        Toasts.show(this, getString(text));
    }

    private void toggleSave() {
        if (view == null) return;
        boolean on = !saved;
        save.setEnabled(false);
        background(() -> {
            api().setSaved(id, on);
            return on;
        }, r -> {
            saved = on;
            save.setEnabled(true);
            save.setText(saved ? "★" : "☆");
            toast(saved ? R.string.v_saved : R.string.v_unsaved);
        }, e -> {
            save.setEnabled(true);
            Toasts.show(this, messageOf(e));
        });
    }

    private void setPref(String key, boolean v) {
        prefs.edit().putBoolean(key, v).apply();
    }

    private void keepAwake() {
        if (prefs.getBoolean("awake", true)) getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        else getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    // ---- шторки ----

    private void showPanel(String which) {
        partsBtn.setSelected("parts".equals(which));
        vsetBtn.setSelected("vset".equals(which));
        panelIn.removeAllViews();
        if (which == null || score == null) {
            panel.setVisibility(View.GONE);
            return;
        }
        if ("vset".equals(which)) fillSettings();
        else fillParts();
        panel.setVisibility(View.VISIBLE);
    }

    /** Рядок заголовка шторки: жирна назва і ✕. */
    private void panelHead(CharSequence text, String which) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        TextView t = new TextView(this, null, 0, R.style.Mc_Strong);
        t.setText(text);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 15);
        r.addView(t, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView x = (TextView) LayoutInflater.from(this).inflate(R.layout.item_icbtn, r, false);
        x.setText("✕");
        x.setContentDescription(getString(R.string.v_close_panel));
        x.setOnClickListener(v -> showPanel(null));
        r.addView(x);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Math.round(("vset".equals(which) ? 4 : 6) * getResources().getDisplayMetrics().density);
        panelIn.addView(r, lp);
    }

    private void switchRow(int label, String key, boolean def, Runnable after) {
        TextView l = Page.kv(panelIn, getString(label), null);
        LinearLayout row = (LinearLayout) l.getParent();
        PillSwitch sw = new PillSwitch(this, null);
        sw.setSelected(prefs.getBoolean(key, def));
        sw.setContentDescription(getString(label));
        sw.setListener((s, on) -> {
            setPref(key, on);
            if (after != null) after.run();
        });
        row.addView(sw);
    }

    private void fillSettings() {
        Part p = score.parts().get(part);
        panelHead(getString(R.string.v_playback), "vset");
        switchRow(R.string.v_metro, "metro", false, () -> metroBtn.setSelected(prefs.getBoolean("metro", false)));
        switchRow(R.string.v_tosound, "tosound", true, null);
        String snd = sounding(p);
        Page.sub(panelIn, snd == null ? getString(R.string.v_tosound_none) : p.name() + " " + snd + ".", -4, 0)
                .setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        switchRow(R.string.v_dorep, "dorep", true, null);
        switchRow(R.string.v_dotempo, "dotempo", true, null);
        switchRow(R.string.v_barnum, "barnum", true, () -> {
            laidWidth = -1;
            relayout();
        });
        switchRow(R.string.v_compress, "compress", true, null);
        switchRow(R.string.v_awake, "awake", true, () -> {
            keepAwake();
            toast(prefs.getBoolean("awake", true) ? R.string.v_awake_on : R.string.v_awake_off);
        });
        Page.sub(panelIn, getString(R.string.v_tempo_hint), 8, 0).setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
    }

    private void fillParts() {
        panelHead(getString(R.string.v_parts_title, score.parts().size()), "parts");
        float d = getResources().getDisplayMetrics().density;
        LinearLayout list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        for (int i = 0; i < score.parts().size(); i++) {
            View row = LayoutInflater.from(this).inflate(R.layout.item_prow, list, false);
            Part p = score.parts().get(i);
            TextView pn = row.findViewById(R.id.pn);
            pn.setText(p.name());
            row.findViewById(R.id.mine).setVisibility(isMine(i) ? View.VISIBLE : View.GONE);
            ((TextView) row.findViewById(R.id.ps)).setText(summary(p));
            if (i == part) row.setBackgroundResource(R.drawable.bg_prow_on);
            else if (i == score.parts().size() - 1) row.setBackground(null); // :last-child без лінії
            final int k = i;
            row.findViewById(R.id.pmain).setOnClickListener(v -> choose(k));
            row.findViewById(R.id.pull).setOnClickListener(v -> toast(R.string.stub_extract));
            list.addView(row);
        }
        androidx.core.widget.NestedScrollView sc = new androidx.core.widget.NestedScrollView(this);
        sc.addView(list);
        int max = Math.round(getResources().getDisplayMetrics().heightPixels * .46f);
        panelIn.addView(sc, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.min(max, Math.round(score.parts().size() * 52 * d))));
        if (score.parts().size() > 1) {
            Page.button(panelIn, R.layout.btn_ghost, getString(R.string.v_whole_score), 12)
                    .setOnClickListener(v -> toast(R.string.stub_score_mode));
        }
        Page.button(panelIn, R.layout.btn_amber, getString(R.string.v_pull_all), 8)
                .setOnClickListener(v -> toast(R.string.stub_extract));
        Page.button(panelIn, R.layout.btn_ghost, getString(R.string.v_whole_file), 8)
                .setOnClickListener(v -> toast(R.string.stub_export));
        Page.sub(panelIn, getString(R.string.v_parts_note), 8, 0).setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
    }

    /** partSummary: ключ (басовий / ударний), стрій, кількість тактів. */
    private String summary(Part p) {
        List<String> bits = new ArrayList<>();
        if ("bass".equals(p.clef())) bits.add(getString(R.string.clef_bass));
        else if ("perc".equals(p.clef())) bits.add(getString(R.string.v_perc_staff));
        String snd = sounding(p);
        if (snd != null) bits.add(getString(R.string.v_tuning, snd));
        bits.add(getString(R.string.v_bars, p.measures().size()));
        return String.join(" · ", bits);
    }

    // ---- аркуш ----

    private final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final List<Sheet.System> systems = new ArrayList<>();

        @SuppressWarnings("NotifyDataSetChanged") // уся верстка міняється разом
        void set(List<Sheet.System> s) {
            systems.clear();
            systems.addAll(s);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            SystemView v = new SystemView(parent.getContext());
            v.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            v.setPadding(sidePx, 0, sidePx, 0);
            return new RecyclerView.ViewHolder(v) { };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder h, int position) {
            ((SystemView) h.itemView).bind(systems.get(position), unitPx, painter);
        }

        @Override
        public int getItemCount() {
            return systems.size();
        }
    }
}
