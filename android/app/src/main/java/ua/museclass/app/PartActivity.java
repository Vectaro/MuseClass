package ua.museclass.app;

import android.os.Bundle;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;

import java.util.ArrayList;
import java.util.List;

import ua.museclass.app.sheet.SheetColors;
import ua.museclass.app.sheet.SheetPainter;
import ua.museclass.app.sheet.SystemView;
import ua.museclass.engraving.Sheet;
import ua.museclass.musicxml.MusicXmlReader;
import ua.museclass.musicxml.Part;
import ua.museclass.musicxml.Score;

/**
 * Ноти однієї партії: системи одна під одною, перенос за шириною екрана.
 * Поки без плеєра. Файл — з кешу ScoreFiles, тож повторно не тягнеться.
 */
public class PartActivity extends BaseActivity {
    static final String EXTRA_ID = "scoreId";
    static final String EXTRA_PART = "part";
    static final String EXTRA_TITLE = "title";

    /**
     * Одиниця верстки (міжлінійний проміжок = 10) у міліметрах: проміжок
     * 1,4 мм — стан заввишки 5,6 мм, звичний розмір нот на телефоні.
     */
    private static final float UNIT_MM = 0.14f;
    private static final int SIDE_DP = 8;

    private final Adapter adapter = new Adapter();
    private RecyclerView sheet;
    private ProgressBar progress;
    private TextView error;
    private MaterialToolbar toolbar;
    private Part part;
    private SheetPainter painter;
    private float unitPx;
    private int sidePx;
    private int laidWidth = -1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_part);
        padForSystemBars(findViewById(R.id.root));
        toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setTitle(getIntent().getStringExtra(EXTRA_TITLE));

        sheet = findViewById(R.id.sheet);
        progress = findViewById(R.id.progress);
        error = findViewById(R.id.error);

        painter = new SheetPainter(SheetColors.of(this));
        findViewById(R.id.page).setBackgroundColor(painter.colors().paper);
        DisplayMetrics dm = getResources().getDisplayMetrics();
        unitPx = UNIT_MM * dm.xdpi / 25.4f;
        sidePx = Math.round(SIDE_DP * dm.density);

        sheet.setLayoutManager(new LinearLayoutManager(this));
        sheet.setAdapter(adapter);
        // не посеред layout самого RecyclerView — оновлення списку там заборонене
        sheet.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> sheet.post(this::relayout));

        String id = getIntent().getStringExtra(EXTRA_ID);
        int index = getIntent().getIntExtra(EXTRA_PART, 0);
        background(() -> {
            Score s = MusicXmlReader.read(app().scoreFiles().get(id));
            if (index < 0 || index >= s.parts().size()) throw new IllegalStateException("немає партії " + index);
            return s.parts().get(index);
        }, p -> {
            part = p;
            toolbar.setSubtitle(p.name());
            progress.setVisibility(View.GONE);
            relayout();
        }, e -> {
            progress.setVisibility(View.GONE);
            error.setText(messageOf(e));
            error.setVisibility(View.VISIBLE);
        });
    }

    /** Перекласти системи, якщо змінилась ширина (поворот, вікна). */
    private void relayout() {
        int w = sheet.getWidth();
        if (part == null || w == 0 || w == laidWidth) return;
        laidWidth = w;
        double units = (w - 2.0 * sidePx) / unitPx;
        adapter.set(Sheet.layout(part, units, new Sheet.Options()));
    }

    private final class Adapter extends RecyclerView.Adapter<Holder> {
        private final List<Sheet.System> systems = new ArrayList<>();

        @SuppressWarnings("NotifyDataSetChanged") // уся верстка міняється разом
        void set(List<Sheet.System> s) {
            systems.clear();
            systems.addAll(s);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            SystemView v = new SystemView(parent.getContext());
            v.setLayoutParams(new RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            v.setPadding(sidePx, 0, sidePx, 0);
            return new Holder(v);
        }

        @Override
        public void onBindViewHolder(@NonNull Holder h, int position) {
            ((SystemView) h.itemView).bind(systems.get(position), unitPx, painter);
        }

        @Override
        public int getItemCount() {
            return systems.size();
        }
    }

    private static final class Holder extends RecyclerView.ViewHolder {
        Holder(View v) {
            super(v);
        }
    }
}
