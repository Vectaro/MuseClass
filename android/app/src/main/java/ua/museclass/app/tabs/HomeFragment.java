package ua.museclass.app.tabs;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.List;

import ua.museclass.app.BaseActivity;
import ua.museclass.app.Instruments;
import ua.museclass.app.R;
import ua.museclass.app.api.Dto;
import ua.museclass.app.ui.Page;
import ua.museclass.app.ui.ScoreViews;

/**
 * Головна (renderHome): пошук і чипи жанрів зверху; без запиту — стрічки «Під
 * твій інструмент» і «Популярне зараз» та «Свої ноти», із запитом або жанром —
 * «Знайдено N» списком. Дані — каталог сервера (тільки публічні). «Популярне»
 * поки в порядку каталогу: відтворень сервер не рахує.
 */
public class HomeFragment extends TabFragment {
    /** Чипи прототипу без «Ансамблю» — такого жанру на сервері немає. */
    private static final int[] KIND_LABELS = {R.string.kind_all, R.string.kind_cover, R.string.kind_folk,
            R.string.kind_classical, R.string.kind_technique};
    private static final String[] KIND_CODES = {null, "cover", "folk", "classical", "technique"};

    private final Handler main = new Handler(Looper.getMainLooper());
    private EditText q;
    private TextView qclear;
    private LinearLayout chips;
    private LinearLayout content;
    private int kind;
    private List<String> mine = new ArrayList<>();
    private int request;
    private final Runnable search = this::load;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle state) {
        View v = inf.inflate(R.layout.fragment_home, parent, false);
        q = v.findViewById(R.id.q);
        qclear = v.findViewById(R.id.qclear);
        chips = v.findViewById(R.id.chips);
        content = v.findViewById(R.id.content);

        for (int i = 0; i < KIND_LABELS.length; i++) {
            TextView c = new TextView(requireContext(), null, 0, R.style.Mc_Chip);
            c.setText(KIND_LABELS[i]);
            final int k = i;
            c.setOnClickListener(x -> {
                kind = k;
                markChips();
                load();
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (i > 0) lp.setMarginStart(Math.round(7 * getResources().getDisplayMetrics().density));
            chips.addView(c, lp);
        }
        markChips();
        q.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void onTextChanged(CharSequence s, int a, int b, int c) {
            }

            @Override
            public void afterTextChanged(Editable s) {
                qclear.setVisibility(s.toString().trim().isEmpty() ? View.GONE : View.VISIBLE);
                main.removeCallbacks(search);
                main.postDelayed(search, 300);
            }
        });
        qclear.setOnClickListener(x -> q.setText(""));

        // спершу свої інструменти — від них «Під твій інструмент» і «є партія»
        background(() -> api().me(), p -> {
            mine = p.instruments == null ? new ArrayList<>() : p.instruments;
            load();
        }, e -> load());
        return v;
    }

    @Override
    public void onDestroyView() {
        main.removeCallbacks(search);
        super.onDestroyView();
    }

    private void markChips() {
        for (int i = 0; i < chips.getChildCount(); i++) chips.getChildAt(i).setSelected(i == kind);
    }

    private String query() {
        return q.getText().toString().trim();
    }

    private void load() {
        final int id = ++request;
        String text = query();
        String k = KIND_CODES[kind];
        boolean searching = !text.isEmpty() || k != null;
        background(() -> api().catalog(text, k, 30, 0), list -> {
            if (id != request) return; // уже набрали інше
            if (searching) showResults(list);
            else showRails(list);
        }, e -> {
            if (id != request) return;
            content.removeAllViews();
            Page.empty(content, BaseActivity.messageOf(e));
        });
    }

    private void showRails(List<Dto.Summary> all) {
        content.removeAllViews();
        List<Dto.Summary> fits = new ArrayList<>();
        for (Dto.Summary s : all) if (s.fits) fits.add(s);
        if (!fits.isEmpty()) {
            Page.sec(content, getString(R.string.home_for_you), 26, 10);
            List<String> names = new ArrayList<>();
            for (String c : mine) names.add(Instruments.name(requireContext(), c));
            Page.sub(content, String.join(", ", names), -6, 10);
            LinearLayout rail = Page.rail(content);
            for (int i = 0; i < fits.size(); i++) {
                Page.addToRail(rail, ScoreViews.card(requireActivity(), rail, fits.get(i), i, mine));
            }
        }
        Page.rule(content);
        Page.sec(content, getString(R.string.home_popular), 0, 10);
        LinearLayout rail = Page.rail(content);
        for (int i = 0; i < all.size(); i++) {
            Page.addToRail(rail, ScoreViews.card(requireActivity(), rail, all.get(i), i, mine));
        }
        if (all.isEmpty()) Page.empty(content, getString(R.string.home_catalog_empty));
        Page.rule(content);
        Page.sec(content, getString(R.string.home_own), 0, 10);
        Page.sub(content, getString(R.string.home_own_sub), -4, 12);
        Page.button(content, R.layout.btn_ghost, getString(R.string.open_editor), 0)
                .setOnClickListener(x -> toast(getString(R.string.stub_editor)));
        Page.button(content, R.layout.btn_ghost, getString(R.string.import_xml), 9)
                .setOnClickListener(x -> toast(getString(R.string.stub_import)));
    }

    private void showResults(List<Dto.Summary> list) {
        content.removeAllViews();
        if (list.isEmpty()) {
            Page.empty(content, getString(R.string.home_nothing));
            Page.button(content, R.layout.btn_ghost, getString(R.string.write_notes), 0)
                    .setOnClickListener(x -> toast(getString(R.string.stub_editor)));
            return;
        }
        Page.sec(content, getString(R.string.home_found, list.size()));
        LinearLayout l = Page.list(content);
        for (int i = 0; i < list.size(); i++) l.addView(ScoreViews.row(requireActivity(), l, list.get(i), i));
    }
}
