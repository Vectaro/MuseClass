package ua.museclass.app;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

import com.google.android.material.appbar.MaterialToolbar;

import ua.museclass.app.api.Dto;
import ua.museclass.musicxml.MusicXmlReader;
import ua.museclass.musicxml.Part;
import ua.museclass.musicxml.Score;

/**
 * Партитура: поки без рендеру. Тягнемо картку й файл, розбираємо файл своїм
 * парсером і показуємо назву, партії та кількість тактів з нього.
 */
public class ScoreActivity extends BaseActivity {
    static final String EXTRA_ID = "scoreId";
    static final String EXTRA_TITLE = "title";

    /** Що прийшло з сервера і що з нього розібрано. */
    private static final class Loaded {
        Dto.ScoreView view;
        Score score;
    }

    private TextView title;
    private TextView subtitle;
    private TextView stats;
    private TextView error;
    private ProgressBar progress;
    private LinearLayout parts;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_score);
        padForSystemBars(findViewById(R.id.root));
        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());

        title = findViewById(R.id.title);
        subtitle = findViewById(R.id.subtitle);
        stats = findViewById(R.id.stats);
        error = findViewById(R.id.error);
        progress = findViewById(R.id.progress);
        parts = findViewById(R.id.parts);

        String id = getIntent().getStringExtra(EXTRA_ID);
        title.setText(getIntent().getStringExtra(EXTRA_TITLE));
        background(() -> {
            Loaded l = new Loaded();
            l.view = api().score(id);
            l.score = MusicXmlReader.read(app().scoreFiles().get(id));
            return l;
        }, this::show, e -> {
            progress.setVisibility(View.GONE);
            error.setText(messageOf(e));
            error.setVisibility(View.VISIBLE);
        });
    }

    private void show(Loaded l) {
        progress.setVisibility(View.GONE);
        Dto.ScoreInfo info = l.view.score;
        Score s = l.score;
        title.setText(info.title);
        StringBuilder sub = new StringBuilder();
        if (info.composer != null) sub.append(info.composer);
        if (info.arranger != null) sub.append(sub.length() > 0 ? " · " : "").append(info.arranger);
        subtitle.setText(sub);
        subtitle.setVisibility(sub.length() > 0 ? View.VISIBLE : View.GONE);

        int measures = s.measureCount();
        stats.setText(getString(R.string.dot_join,
                getResources().getQuantityString(R.plurals.measures, measures, measures),
                getResources().getQuantityString(R.plurals.parts, s.parts().size(), s.parts().size())));
        stats.setVisibility(View.VISIBLE);

        LayoutInflater inf = LayoutInflater.from(this);
        parts.removeAllViews();
        for (Part p : s.parts()) {
            View row = inf.inflate(R.layout.item_part, parts, false);
            ((TextView) row.findViewById(R.id.name)).setText(p.name());
            StringBuilder d = new StringBuilder(clefName(p.clef()));
            d.append(" · ").append(p.meter());
            if (p.fifths() != 0) {
                d.append(" · ").append(Math.abs(p.fifths())).append(p.fifths() > 0 ? " ♯" : " ♭");
            }
            if (!p.instrument().equals(p.name())) d.append(" · ").append(p.instrument());
            ((TextView) row.findViewById(R.id.details)).setText(d);
            parts.addView(row);
        }
    }

    private String clefName(String clef) {
        switch (clef) {
            case "bass":
                return getString(R.string.clef_bass);
            case "alto":
                return getString(R.string.clef_alto);
            case "perc":
                return getString(R.string.clef_perc);
            default:
                return getString(R.string.clef_treble);
        }
    }
}
