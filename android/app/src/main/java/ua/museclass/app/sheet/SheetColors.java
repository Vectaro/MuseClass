package ua.museclass.app.sheet;

import android.content.Context;
import android.content.SharedPreferences;

import androidx.core.content.ContextCompat;

import ua.museclass.app.R;

/**
 * Кольори нотного аркуша — параметри малювання, не константи. За
 * замовчуванням білий «папір» в обох темах; інверсія (світлі ноти на темному)
 * — налаштування {@link #KEY_INVERTED}, перемикач з'явиться в розділі «вигляд».
 */
public final class SheetColors {
    public static final String PREFS = "settings";
    public static final String KEY_INVERTED = "sheet_inverted";

    public final int paper;
    public final int ink;
    public final int muted;

    public SheetColors(int paper, int ink, int muted) {
        this.paper = paper;
        this.ink = ink;
        this.muted = muted;
    }

    public static SheetColors of(Context c) {
        SharedPreferences p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (p.getBoolean(KEY_INVERTED, false)) {
            return new SheetColors(ContextCompat.getColor(c, R.color.sheet_inverted_paper),
                    ContextCompat.getColor(c, R.color.sheet_inverted_ink),
                    ContextCompat.getColor(c, R.color.sheet_inverted_muted));
        }
        return new SheetColors(ContextCompat.getColor(c, R.color.sheet_paper),
                ContextCompat.getColor(c, R.color.sheet_ink),
                ContextCompat.getColor(c, R.color.sheet_muted));
    }
}
