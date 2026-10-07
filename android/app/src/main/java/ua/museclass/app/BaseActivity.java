package ua.museclass.app;

import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import java.util.Arrays;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import ua.museclass.app.api.ApiClient;
import ua.museclass.app.api.ApiException;

/** Спільне для екранів: фонові запити, відступи від системних смуг, вихід на вхід. */
public abstract class BaseActivity extends AppCompatActivity {
    private static final ExecutorService IO = Executors.newFixedThreadPool(4);
    private static final Handler MAIN = new Handler(Looper.getMainLooper());
    private static final String TAG = "MuseClass";

    public interface Work<T> {
        T run() throws Exception;
    }

    public interface Done<T> {
        void accept(T value);
    }

    public MuseClassApp app() {
        return (MuseClassApp) getApplication();
    }

    public ApiClient api() {
        return app().api();
    }

    public Session session() {
        return app().session();
    }

    /**
     * Робота у фоні, результат — на головному потоці, якщо екран ще живий.
     * Помилка «сесія закінчилась» сама веде на вхід.
     */
    public <T> void background(Work<T> work, Done<T> ok, Done<Exception> fail) {
        IO.execute(() -> {
            T value = null;
            Exception error = null;
            try {
                value = work.run();
            } catch (Exception e) {
                Log.w(TAG, getClass().getSimpleName() + ": фоновий запит упав", e);
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
    public static String messageOf(Exception e) {
        if (e instanceof ApiException) return e.getMessage();
        if (e instanceof ua.museclass.musicxml.MusicXmlException) {
            return "Не вдалося прочитати ноти: " + e.getMessage();
        }
        return ApiException.GENERIC;
    }

    private static final String LOCAL_NETWORK = "android.permission.ACCESS_LOCAL_NETWORK";

    /**
     * З API 37 з'єднання з приватними адресами (10.0.2.2, 192.168.*) без
     * дозволу «Пристрої поблизу» мовчки відкидаються. Просимо його, лише якщо
     * маніфест його оголошує — зараз тільки debug (dev-сервер на ПК).
     */
    protected void askLocalNetwork() {
        if (Build.VERSION.SDK_INT < 37) return;
        if (checkSelfPermission(LOCAL_NETWORK) == PackageManager.PERMISSION_GRANTED) return;
        try {
            PackageInfo pi = getPackageManager().getPackageInfo(getPackageName(), PackageManager.GET_PERMISSIONS);
            if (pi.requestedPermissions == null
                    || !Arrays.asList(pi.requestedPermissions).contains(LOCAL_NETWORK)) return;
        } catch (PackageManager.NameNotFoundException e) {
            return;
        }
        requestPermissions(new String[]{LOCAL_NETWORK}, 1);
    }

    public void toLogin() {
        session().clear();
        app().scoreFiles().clear(); // копії файлів — попереднього користувача
        Intent i = new Intent(this, OnboardingActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }

    /** Edge-to-edge обов'язковий з API 35: відсуваємо вміст від смуг і клавіатури. */
    public static void padForSystemBars(View root) {
        ViewCompat.setOnApplyWindowInsetsListener(root, (v, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.ime());
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return WindowInsetsCompat.CONSUMED;
        });
    }
}
