package ua.museclass.app.tabs;

import android.graphics.Typeface;
import android.os.Bundle;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.content.ContextCompat;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;

import ua.museclass.app.BaseActivity;
import ua.museclass.app.R;
import ua.museclass.app.TabsActivity;
import ua.museclass.app.api.Dto;
import ua.museclass.app.ui.ClassDialogs;
import ua.museclass.app.ui.Page;
import ua.museclass.app.ui.ScoreViews;

/**
 * Бібліотека (renderLib): класи, які веду (картки з кодом), класи, де вчуся
 * (видане), «Збережене», «Мої ноти», а без жодного класу — картка вступу за
 * кодом. Дані — /classes, /me/library, /me/saved, /scores/mine.
 */
public class LibraryFragment extends TabFragment {
    private static final class Data {
        List<Dto.ClassInfo> classes;
        List<Dto.LibraryEntry> library;
        List<Dto.Summary> saved;
        List<Dto.Summary> mine;
    }

    private LinearLayout page;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inf, @Nullable ViewGroup parent, @Nullable Bundle state) {
        View v = inf.inflate(R.layout.fragment_page, parent, false);
        ((TextView) v.findViewById(R.id.title)).setText(R.string.tab_lib);
        page = v.findViewById(R.id.page);
        background(() -> {
            Data d = new Data();
            d.classes = api().classes();
            d.library = api().library();
            d.saved = api().saved();
            d.mine = api().mine();
            return d;
        }, this::show, e -> Page.empty(page, BaseActivity.messageOf(e)));
        return v;
    }

    private static List<Dto.Summary> issued(Data d, String classId) {
        List<Dto.Summary> out = new ArrayList<>();
        for (Dto.LibraryEntry e : d.library) if (classId.equals(e.classId)) out.add(e.score);
        return out;
    }

    private void rows(LinearLayout list, List<Dto.Summary> scores) {
        for (int i = 0; i < scores.size(); i++) list.addView(ScoreViews.row(requireActivity(), list, scores.get(i), i));
    }

    /** Жирний рядок (strong у прототипі). */
    private TextView strong(ViewGroup parent, CharSequence text, float sp) {
        TextView t = new TextView(requireContext());
        t.setText(text);
        t.setTextColor(ContextCompat.getColor(requireContext(), R.color.text));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(t.getTypeface(), Typeface.BOLD);
        parent.addView(t);
        return t;
    }

    /** Бейдж «b-class» (periwinkle з білим). */
    private TextView classBadge(int text) {
        TextView b = new TextView(requireContext(), null, 0, R.style.Mc_Badge);
        b.setBackgroundResource(R.drawable.bg_badge_class);
        b.setTextColor(ContextCompat.getColor(requireContext(), android.R.color.white));
        b.setText(text);
        return b;
    }

    private void show(Data d) {
        List<Dto.ClassInfo> teaching = new ArrayList<>(), learning = new ArrayList<>();
        for (Dto.ClassInfo c : d.classes) ("teacher".equals(c.role) ? teaching : learning).add(c);

        if (!teaching.isEmpty()) {
            Page.rule(page);
            LinearLayout head = new LinearLayout(requireContext());
            head.setOrientation(LinearLayout.HORIZONTAL);
            head.setGravity(Gravity.CENTER_VERTICAL);
            page.addView(head);
            TextView sec = Page.sec(head, getString(R.string.lib_teaching, teaching.size()), 0, 0);
            LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams) sec.getLayoutParams();
            lp.width = 0;
            lp.weight = 1;
            TextView more = classBadge(R.string.lib_more_class);
            more.setOnClickListener(x -> ClassDialogs.create(host(), c -> reload()));
            head.addView(more);
        }
        for (Dto.ClassInfo c : teaching) {
            LinearLayout card = Page.card(page, 12);
            LinearLayout top = new LinearLayout(requireContext());
            top.setOrientation(LinearLayout.HORIZONTAL);
            card.addView(top);
            TextView name = strong(top, c.name, 15);
            name.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
            top.addView(classBadge(R.string.lib_you_teach));
            Page.sub(card, getResources().getQuantityString(R.plurals.students, c.students, c.students)
                    + " · " + getString(R.string.lib_code, c.code), 2, 8);
            List<Dto.Summary> list = issued(d, c.id);
            LinearLayout l = Page.list(card);
            if (list.isEmpty()) {
                TextView e = Page.empty(l, getString(R.string.lib_class_empty));
                int p = Math.round(8 * getResources().getDisplayMetrics().density);
                e.setPadding(p, 2 * p, p, 2 * p);
            }
            rows(l, list);
            Page.button(card, R.layout.btn_peri, getString(R.string.lib_assign), 10)
                    .setOnClickListener(x -> toast(getString(R.string.stub_assign)));
        }
        for (Dto.ClassInfo c : learning) {
            List<Dto.Summary> list = issued(d, c.id);
            Page.rule(page);
            Page.sec(page, c.name, 0, 0);
            Page.sub(page, c.teacherName + " · " + getResources().getQuantityString(R.plurals.issued,
                    list.size(), list.size()), 13.5f, 13.5f); // <p class="sub"> з типовим відступом 1em
            rows(Page.list(page), list);
        }

        Page.rule(page);
        Page.sec(page, getString(R.string.lib_saved), 0, 0);
        LinearLayout saved = Page.list(page);
        if (d.saved.isEmpty()) Page.empty(saved, getString(R.string.lib_saved_empty));
        rows(saved, d.saved);

        Page.rule(page);
        Page.sec(page, getString(R.string.lib_mine), 0, 0);
        rows(Page.list(page), d.mine);
        MaterialButton[] own = Page.row(page, R.layout.btn_ghost, getString(R.string.lib_write),
                R.layout.btn_ghost, getString(R.string.lib_import), 12);
        own[0].setOnClickListener(x -> toast(getString(R.string.stub_editor)));
        own[1].setOnClickListener(x -> toast(getString(R.string.stub_import)));

        if (d.classes.isEmpty()) {
            Page.rule(page);
            LinearLayout card = Page.card(page, 0);
            strong(card, getString(R.string.lib_join_title), 14.5f);
            Page.sub(card, getString(R.string.lib_join_sub), 4, 10);
            EditText code = (EditText) LayoutInflater.from(requireContext()).inflate(R.layout.item_code, card, false);
            card.addView(code);
            MaterialButton join = Page.button(card, R.layout.btn_peri, getString(R.string.join), 10);
            join.setOnClickListener(x -> {
                String c = code.getText().toString().trim();
                if (c.isEmpty()) {
                    toast(getString(R.string.code_needed));
                    return;
                }
                join.setEnabled(false);
                background(() -> api().joinClass(c), cl -> {
                    toast(getString(R.string.joined, cl.name));
                    reload();
                }, e -> {
                    join.setEnabled(true);
                    toast(BaseActivity.messageOf(e));
                });
            });
        }
    }

    private void reload() {
        goTab(TabsActivity.TAB_LIB);
    }
}
