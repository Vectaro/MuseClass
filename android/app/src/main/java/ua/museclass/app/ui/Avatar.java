package ua.museclass.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Shader;
import android.util.AttributeSet;
import android.view.View;

import androidx.core.content.ContextCompat;

import java.util.Locale;

import ua.museclass.app.R;

/** Аватар (avatarHTML): фото в колі або бурштинове коло з першою літерою імені (Serif, 0.46 розміру). */
public class Avatar extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint text = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private String letter = "?";
    private Bitmap photo;

    public Avatar(Context c, AttributeSet a) {
        super(c, a);
        text.setTypeface(Fonts.serif(c));
        text.setTextAlign(Paint.Align.CENTER);
    }

    public void set(String name, Bitmap photo) {
        String n = name == null ? "" : name.trim();
        letter = n.isEmpty() ? "?" : n.substring(0, n.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT);
        this.photo = photo;
        invalidate();
    }

    @Override
    protected void onDraw(Canvas c) {
        float s = Math.min(getWidth(), getHeight()), rad = s / 2f;
        if (photo != null) {
            BitmapShader sh = new BitmapShader(photo, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
            float k = s / Math.min(photo.getWidth(), photo.getHeight());
            m.setScale(k, k);
            m.postTranslate((s - photo.getWidth() * k) / 2f, (s - photo.getHeight() * k) / 2f);
            sh.setLocalMatrix(m);
            paint.setShader(sh);
            c.drawCircle(rad, rad, rad, paint);
            paint.setShader(null);
            return;
        }
        paint.setColor(ContextCompat.getColor(getContext(), R.color.amber));
        c.drawCircle(rad, rad, rad, paint);
        text.setColor(ContextCompat.getColor(getContext(), R.color.on_accent));
        text.setTextSize(Math.round(s * 0.46f));
        Paint.FontMetrics fm = text.getFontMetrics();
        c.drawText(letter, rad, rad - (fm.ascent + fm.descent) / 2f, text);
    }
}
