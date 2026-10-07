package ua.museclass.app.ui;

import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatTextView;

/** Текст шрифтом Instrument Serif із запасним serif для кирилиці (.h-title, .h-sec, .mark…). */
public class SerifText extends AppCompatTextView {
    public SerifText(Context c) {
        super(c);
        init();
    }

    public SerifText(Context c, AttributeSet a) {
        super(c, a);
        init();
    }

    public SerifText(Context c, AttributeSet a, int s) {
        super(c, a, s);
        init();
    }

    private void init() {
        if (!isInEditMode()) setTypeface(Fonts.serif(getContext()));
    }
}
