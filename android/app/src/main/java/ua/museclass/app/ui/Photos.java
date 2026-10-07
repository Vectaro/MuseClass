package ua.museclass.app.ui;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import androidx.annotation.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * Фото профілю живе лише на пристрої: сервер фото не зберігає. Як shrinkPhoto у
 * прототипі — зменшуємо до 256 px по більшій стороні, JPEG 85 %. Окремий файл
 * на кожного користувача.
 */
public final class Photos {
    private static final int MAX = 256;

    private Photos() {
    }

    private static File file(Context c, String userId) {
        return new File(c.getFilesDir(), "avatar-" + (userId == null ? "anon" : userId) + ".jpg");
    }

    @Nullable
    public static Bitmap load(Context c, String userId) {
        File f = file(c, userId);
        return f.isFile() ? BitmapFactory.decodeFile(f.getPath()) : null;
    }

    public static void save(Context c, String userId, @Nullable Bitmap b) {
        File f = file(c, userId);
        if (b == null) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
            return;
        }
        try (OutputStream out = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.JPEG, 85, out);
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    /** Прочитати вибране фото і зменшити; null — не читається. */
    @Nullable
    public static Bitmap read(Context c, Uri uri) {
        try {
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                BitmapFactory.decodeStream(in, null, o);
            }
            int sample = 1;
            while (Math.max(o.outWidth, o.outHeight) / (sample * 2) >= MAX) sample *= 2;
            BitmapFactory.Options o2 = new BitmapFactory.Options();
            o2.inSampleSize = sample;
            Bitmap b;
            try (InputStream in = c.getContentResolver().openInputStream(uri)) {
                b = BitmapFactory.decodeStream(in, null, o2);
            }
            if (b == null) return null;
            float k = Math.min(1f, MAX / (float) Math.max(b.getWidth(), b.getHeight()));
            if (k < 1f) {
                Bitmap s = Bitmap.createScaledBitmap(b, Math.max(1, Math.round(b.getWidth() * k)),
                        Math.max(1, Math.round(b.getHeight() * k)), true);
                if (s != b) b.recycle();
                b = s;
            }
            return b;
        } catch (IOException | SecurityException e) {
            return null;
        }
    }
}
