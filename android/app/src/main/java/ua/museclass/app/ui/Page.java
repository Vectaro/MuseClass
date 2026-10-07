package ua.museclass.app.ui;

import android.content.Context;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;

import com.google.android.material.button.MaterialButton;

import ua.museclass.app.R;

/** Цеглинки сторінок прототипу: .h-sec, .sub, .staffrule, .rail, .list, .empty, .card2, .btn. */
public final class Page {
    private Page() {
    }

    static int dp(Context c, float v) {
        return Math.round(v * c.getResources().getDisplayMetrics().density);
    }

    private static LinearLayout.LayoutParams lp(Context c, float top, float bottom) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        p.topMargin = dp(c, top);
        p.bottomMargin = dp(c, bottom);
        return p;
    }

    /** Заголовок секції (.h-sec): Serif 20, відступи 26 / 10, або свої. */
    public static TextView sec(ViewGroup parent, CharSequence text, float top, float bottom) {
        Context c = parent.getContext();
        SerifText t = new SerifText(c);
        t.setTextAppearance(R.style.Mc_Sec);
        t.setTypeface(Fonts.serif(c)); // setTextAppearance скидає шрифт
        t.setText(text);
        ViewCompat.setAccessibilityHeading(t, true);
        parent.addView(t, lp(c, top, bottom));
        return t;
    }

    public static TextView sec(ViewGroup parent, CharSequence text) {
        return sec(parent, text, 26, 10);
    }

    /** Підпис (.sub). */
    public static TextView sub(ViewGroup parent, CharSequence text, float top, float bottom) {
        Context c = parent.getContext();
        TextView t = new TextView(c);
        t.setTextAppearance(R.style.Mc_Sub);
        t.setText(text);
        parent.addView(t, lp(c, top, bottom));
        return t;
    }

    /** Штрихова лінійка між секціями, відступи 20. */
    public static void rule(ViewGroup parent) {
        Context c = parent.getContext();
        parent.addView(new StaffRule(c, null), lp(c, 20, 20));
    }

    /** Стрічка карток (.rail): на всю ширину екрана поверх відступів сторінки 18. */
    public static LinearLayout rail(ViewGroup parent) {
        Context c = parent.getContext();
        HorizontalScrollView h = new HorizontalScrollView(c);
        h.setHorizontalScrollBarEnabled(false);
        h.setClipToPadding(false);
        h.setPadding(dp(c, 18), dp(c, 2), dp(c, 18), dp(c, 6));
        LinearLayout.LayoutParams p = lp(c, 0, 0);
        p.leftMargin = -dp(c, 18);
        p.rightMargin = -dp(c, 18);
        LinearLayout row = new LinearLayout(c);
        row.setOrientation(LinearLayout.HORIZONTAL);
        h.addView(row);
        parent.addView(h, p);
        return row;
    }

    /** Додати картку в стрічку з проміжком 12. */
    public static void addToRail(LinearLayout rail, View card) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(dp(rail.getContext(), 152),
                ViewGroup.LayoutParams.WRAP_CONTENT);
        if (rail.getChildCount() > 0) p.setMarginStart(dp(rail.getContext(), 12));
        rail.addView(card, p);
    }

    /** Список рядків (.list), відступ зверху 6. */
    public static LinearLayout list(ViewGroup parent) {
        Context c = parent.getContext();
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        parent.addView(l, lp(c, 6, 0));
        return l;
    }

    /** Порожній стан (.empty): по центру, приглушений, відступи 34 / 20. */
    public static TextView empty(ViewGroup parent, CharSequence text) {
        Context c = parent.getContext();
        TextView t = new TextView(c);
        t.setText(text);
        t.setGravity(Gravity.CENTER);
        t.setTextColor(ContextCompat.getColor(c, R.color.muted));
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13.5f);
        t.setLineSpacing(0, 1.15f);
        t.setPadding(dp(c, 20), dp(c, 34), dp(c, 20), dp(c, 34));
        parent.addView(t, lp(c, 0, 0));
        return t;
    }

    /** Картка .card2: pult2, рамка, радіус 13, відступ 14. */
    public static LinearLayout card(ViewGroup parent, float top) {
        Context c = parent.getContext();
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setBackgroundResource(R.drawable.bg_card);
        int p = dp(c, 14);
        l.setPadding(p, p, p, p);
        parent.addView(l, lp(c, top, 0));
        return l;
    }

    /** Рядок «назва — значення» (.kv): 14 / 13 приглушене праворуч, відступ 9, волосяна лінія між рядками. */
    public static TextView kv(ViewGroup parent, CharSequence label, CharSequence value) {
        Context c = parent.getContext();
        // попередній рядок отримує лінію знизу: у прототипі її немає лише в останнього
        if (parent.getChildCount() > 0) parent.getChildAt(parent.getChildCount() - 1).setBackgroundResource(R.drawable.bg_kv);
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(c, 9), 0, dp(c, 9));
        TextView l = new TextView(c);
        l.setText(label);
        l.setTextColor(ContextCompat.getColor(c, R.color.text));
        l.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        r.addView(l, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextColor(ContextCompat.getColor(c, R.color.muted));
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        vp.setMarginStart(dp(c, 10));
        if (value != null) r.addView(v, vp);
        parent.addView(r);
        return l;
    }

    /** Кнопка на всю ширину: kind — R.layout.btn_amber / btn_ghost / btn_peri. */
    public static MaterialButton button(ViewGroup parent, int kind, CharSequence text, float top) {
        MaterialButton b = (MaterialButton) LayoutInflater.from(parent.getContext()).inflate(kind, parent, false);
        b.setText(text);
        LinearLayout.LayoutParams p = lp(parent.getContext(), top, 0);
        parent.addView(b, p);
        return b;
    }

    /** Ряд (.row) з двох кнопок порівну, проміжок 10. */
    public static MaterialButton[] row(ViewGroup parent, int kind1, CharSequence t1, int kind2, CharSequence t2,
                                       float top) {
        Context c = parent.getContext();
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        MaterialButton a = (MaterialButton) LayoutInflater.from(c).inflate(kind1, r, false);
        MaterialButton b = (MaterialButton) LayoutInflater.from(c).inflate(kind2, r, false);
        a.setText(t1);
        b.setText(t2);
        LinearLayout.LayoutParams pa = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        LinearLayout.LayoutParams pb = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
        pb.setMarginStart(dp(c, 10));
        r.addView(a, pa);
        r.addView(b, pb);
        parent.addView(r, lp(c, top, 0));
        return new MaterialButton[]{a, b};
    }
}
