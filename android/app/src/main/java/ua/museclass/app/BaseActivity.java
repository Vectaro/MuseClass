package ua.museclass.app;

import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ua.museclass.app.api.ApiClient;
import ua.museclass.app.api.ApiException;

/** Спільне для екранів: фонові запити, відступи від системних смуг, вихід на вхід. */
public abstract class BaseActivity extends AppCompatActivity {
    private static final ExecutorService IO = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());

    protected interface Work<T> {
        T run() throws Exception;
    }

    protected interface Done<T> {
        void accept(T value);
    }

    protected MuseClassApp app() {
        return (MuseClassApp) getApplication();
    }

    protected ApiClient api() {
        return app().api();
    }

    protected Session session() {
        return app().session();
    }

    /**
     * Робота у фоні, результат — на головному потоці, якщо екран ще живий.
     * Помилка «сесія закінчилась» сама веде на вхід.
     */
    protected <T> void background(Work<T> work, Done<T> ok, Done<Exception> fail) {
        IO.execute(() -> {
            T value = null;
            Exception error = null;
            try {
                value = work.run();
            } catch (Exception e) {
                error = e;
            }
            final T v = value;
            final Exception err = error;
            MAIN.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                if (err instanceof ApiException && ((ApiException) err).unauthorized) {
                    toLogin();
                } else if (err != null) {
                    fail.accept(err);
                } else {
                    ok.accept(v);
                }
            });
        });
    }

    /** Текст помилки для людини. */
    protected static String messageOf(Exception e) {
        if (e instanceof ApiException) return e.getMessage();
        if (e instanceof ua.museclass.musicxml.MusicXmlException) {
            return "Не вдалося прочитати ноти: " + e.getMessage();
        }
        return ApiException.GENERIC;
    }

    protected void toLogin() {
        session().clear();
        Intent i = new Intent(this, LoginActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    /** Edge-to-edge обов'язковий з API 35: відсуваємо вміст від смуг і клавіатури. */
    protected static void padForSystemBars(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
