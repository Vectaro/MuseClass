package ua.museclass.app.ui;

import android.app.Activity;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import ua.museclass.app.R;

/**
 * Тост прототипу (.toast): пігулка --pult3 з рамкою, 13 px, над нижньою панеллю
 * (bottom 86 px), до 86 % ширини, 2,2 с, поява/зникнення .2 с.
 */
public final class Toasts {
    private static final int TAG = R.id.mc_toast;

    /** Тост, що має пережити перестворення екрана (зміна теми). */
    private static CharSequence pending;

    private Toasts() {
    }

    public static void later(CharSequence msg) {
        pending = msg;
    }

    /** Показати відкладений тост, якщо є (кличе екран після створення). */
    public static void flush(Activity a) {
        if (pending == null) return;
        CharSequence m = pending;
        pending = null;
        a.getWindow().getDecorView().post(() -> show(a, m));
    }

    public static void show(Activity a, CharSequence msg) {
        FrameLayout root = a.findViewById(android.R.id.content);
        if (root == null) return;
        TextView t = root.findViewWithTag(TAG);
        float d = a.getResources().getDisplayMetrics().density;
        if (t == null) {
            t = new TextView(a);
            t.setTag(TAG);
            t.setBackgroundResource(R.drawable.bg_toast);
            t.setTextColor(ContextCompat.getColor(a, R.color.text));
            t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            t.setGravity(Gravity.CENTER);
            t.setPadding(Math.round(15 * d), Math.round(9 * d), Math.round(15 * d), Math.round(9 * d));
            t.setAlpha(0);
            t.setElevation(40 * d);
            FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT,
                    Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);
            root.addView(t, lp);
        }
        int w = root.getWidth() > 0 ? root.getWidth() : a.getResources().getDisplayMetrics().widthPixels;
        t.setMaxWidth(Math.round(w * .86f));
        // над нижньою панеллю і системною смугою навігації
        WindowInsetsCompat ins = ViewCompat.getRootWindowInsets(root);
        int bottomInset = ins == null ? 0 : ins.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
        ((FrameLayout.LayoutParams) t.getLayoutParams()).bottomMargin = Math.round(86 * d) + bottomInset;
        t.setText(msg);
        t.setVisibility(View.VISIBLE);
        t.animate().cancel();
        final TextView tv = t;
        tv.animate().alpha(1).setDuration(200).withEndAction(() ->
                tv.animate().alpha(0).setStartDelay(2200).setDuration(200).start()).start();
        t.requestLayout();
    }
}
