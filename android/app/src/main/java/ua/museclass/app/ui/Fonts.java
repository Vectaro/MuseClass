package ua.museclass.app.ui;

import android.content.Context;
import android.graphics.Typeface;
import android.graphics.fonts.Font;
import android.graphics.fonts.FontFamily;
import android.os.Build;

import androidx.core.content.res.ResourcesCompat;

import java.io.IOException;

import ua.museclass.app.R;

/**
 * Шрифти прототипу. У Instrument Sans і Instrument Serif немає кирилиці: у
 * браузері прототипу українські літери малює запасний шрифт зі стеку CSS
 * (system-ui / Georgia). Тут так само: Sans падає на системний (Roboto) сам,
 * а Serif з API 29 отримує запасним системний serif — інакше заголовки
 * українською вийшли б без засічок. До API 29 — просто системний serif.
 */
public final class Fonts {
    private static Typeface serif;

    private Fonts() {
    }

    public static Typeface serif(Context c) {
        if (serif != null) return serif;
        Typeface t = null;
        if (Build.VERSION.SDK_INT >= 29) {
            try {
                Font f = new Font.Builder(c.getResources(), R.font.instrument_serif_regular).build();
                t = new Typeface.CustomFallbackBuilder(new FontFamily.Builder(f).build())
                        .setSystemFallback("serif")
                        .build();
            } catch (IOException | RuntimeException e) {
                t = null;
            }
        }
        if (t == null) {
            Typeface own = ResourcesCompat.getFont(c, R.font.instrument_serif_regular);
            // без власного запасного ланцюжка кирилиця пішла б у Roboto — краще весь serif системний
            t = Build.VERSION.SDK_INT >= 29 && own != null ? own : Typeface.SERIF;
        }
        serif = t;
        return t;
    }
}
