package ua.museclass.app.ui;

import android.content.Context;

import androidx.appcompat.app.AppCompatDelegate;

/** «Як у системі / Світла / Темна» — як THEMES у прототипі. Живе на пристрої. */
public final class ThemeMode {
    public static final String SYSTEM = "system";
    public static final String LIGHT = "light";
    public static final String DARK = "dark";
    private static final String PREFS = "settings";
    private static final String KEY = "theme";

    private ThemeMode() {
    }

    public static String get(Context c) {
        return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, SYSTEM);
    }

    public static void set(Context c, String mode) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, mode).apply();
        apply(mode);
    }

    public static void apply(Context c) {
        apply(get(c));
    }

    private static void apply(String mode) {
        int m = LIGHT.equals(mode) ? AppCompatDelegate.MODE_NIGHT_NO
                : DARK.equals(mode) ? AppCompatDelegate.MODE_NIGHT_YES
                : AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM;
        if (AppCompatDelegate.getDefaultNightMode() != m) AppCompatDelegate.setDefaultNightMode(m);
    }
}
