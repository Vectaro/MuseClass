package ua.museclass.app.ui;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.List;

import ua.museclass.app.Instruments;
import ua.museclass.app.R;
import ua.museclass.app.ViewerActivity;
import ua.museclass.app.api.Dto;

/**
 * Картка (cardHTML) і рядок списку (itemHTML) партитури. Формати: сервер
 * зберігає лише MusicXML, з якого ми малюємо ноти, тож завжди «Інтерактив ·
 * MusicXML»; MIDI і PDF у прототипі — заглушки, тут їх немає.
 */
public final class ScoreViews {
    private ScoreViews() {
    }

    /** Картка для стрічки; i — позиція (обкладинка — miniSheet(i + 2), як у прототипі). */
    public static View card(Activity a, ViewGroup parent, Dto.Summary s, int i, List<String> mine) {
        View v = LayoutInflater.from(a).inflate(R.layout.item_card, parent, false);
        ((MiniSheet) v.findViewById(R.id.cover)).set(i + 2, 9, true);
        TextView vis = v.findViewById(R.id.vis);
        if ("class".equals(s.visibility)) {
            badge(vis, R.string.badge_class, R.drawable.bg_badge_class, android.R.color.white);
        } else if ("private".equals(s.visibility)) {
            badge(vis, R.string.badge_me, R.drawable.bg_badge_dark, R.color.badge_me);
        }
        formats(a, v.findViewById(R.id.fmt));
        ((TextView) v.findViewById(R.id.title)).setText(s.title);
        ((TextView) v.findViewById(R.id.author)).setText(author(s));
        String fit = fitPart(a, s, mine);
        TextView f = v.findViewById(R.id.fit);
        if (fit != null) {
            f.setText(a.getString(R.string.fit_part, fit));
            f.setVisibility(View.VISIBLE);
        }
        v.setOnClickListener(x -> open(a, s));
        return v;
    }

    /** Рядок списку; i — позиція (мініатюра — miniSheet(i + 5)). */
    public static View row(Activity a, ViewGroup parent, Dto.Summary s, int i) {
        View v = LayoutInflater.from(a).inflate(R.layout.item_row, parent, false);
        ((MiniSheet) v.findViewById(R.id.mini)).set(i + 5, 7, false);
        ((TextView) v.findViewById(R.id.title)).setText(s.title);
        String au = author(s);
        if (s.arranger != null && !s.arranger.isEmpty() && !s.arranger.equals(au)) au += " · " + s.arranger;
        ((TextView) v.findViewById(R.id.author)).setText(au);
        formats(a, v.findViewById(R.id.fmt));
        v.setOnClickListener(x -> open(a, s));
        return v;
    }

    /** Автор картки: композитор, інакше аранжувальник, інакше власник. */
    static String author(Dto.Summary s) {
        if (s.composer != null && !s.composer.isEmpty()) return s.composer;
        if (s.arranger != null && !s.arranger.isEmpty()) return s.arranger;
        return s.ownerName == null ? "" : s.ownerName;
    }

    /** Назва першої партії під мої інструменти — «є партія: …»; null — немає. */
    public static String fitPart(Context c, Dto.Summary s, List<String> mine) {
        if (!s.fits || s.instruments == null || mine == null) return null;
        for (String m : mine) if (s.instruments.contains(m)) return Instruments.name(c, m);
        return null;
    }

    private static void formats(Context c, LinearLayout box) {
        box.removeAllViews();
        addBadge(c, box, R.string.badge_interactive, R.color.amber);
        addBadge(c, box, R.string.badge_xml, R.color.badge_xml);
    }

    private static void addBadge(Context c, LinearLayout box, int text, int color) {
        TextView t = new TextView(c, null, 0, R.style.Mc_Badge);
        t.setText(text);
        t.setTextColor(ContextCompat.getColor(c, color));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        if (box.getChildCount() > 0) lp.setMarginStart(Math.round(4 * c.getResources().getDisplayMetrics().density));
        box.addView(t, lp);
    }

    private static void badge(TextView t, int text, int bg, int color) {
        t.setText(text);
        t.setBackgroundResource(bg);
        t.setTextColor(ContextCompat.getColor(t.getContext(), color));
        t.setVisibility(View.VISIBLE);
    }

    /** Відкрити партитуру в переглядачі. */
    public static void open(Activity a, Dto.Summary s) {
        a.startActivity(new Intent(a, ViewerActivity.class)
                .putExtra(ViewerActivity.EXTRA_ID, s.id)
                .putExtra(ViewerActivity.EXTRA_TITLE, s.title));
    }
}
