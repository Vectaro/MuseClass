package ua.museclass.app.ui;

import android.content.Context;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import ua.museclass.app.R;

/** Кнопки-перемикачі .pick: обрана — бурштинова рамка, тло pult3 і « ✓» кольору accent-ink. */
public final class Picks {
    private Picks() {
    }

    public static TextView make(Context c, String label) {
        TextView t = new TextView(c, null, 0, R.style.Mc_Pick);
        t.setTag(label);
        t.setClickable(true);
        t.setFocusable(true);
        set(t, false);
        return t;
    }

    public static void set(TextView t, boolean on) {
        t.setSelected(on);
        String label = (String) t.getTag();
        if (!on) {
            t.setText(label);
            return;
        }
        SpannableStringBuilder sb = new SpannableStringBuilder(label).append(" ✓");
        sb.setSpan(new ForegroundColorSpan(ContextCompat.getColor(t.getContext(), R.color.accent_ink)),
                label.length(), sb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        t.setText(sb);
    }
}
