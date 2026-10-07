package ua.museclass.app;

import android.app.Application;

import java.io.File;

import ua.museclass.app.api.ApiClient;
import ua.museclass.app.api.ScoreFiles;
import ua.museclass.app.ui.ThemeMode;

/** Одна сесія й один клієнт на весь застосунок. */
public final class MuseClassApp extends Application {
    private Session session;
    private ApiClient api;
    private ScoreFiles scoreFiles;

    @Override
    public void onCreate() {
        super.onCreate();
        ThemeMode.apply(this);
        session = new Session(this);
        api = new ApiClient(BuildConfig.API_BASE, session);
        scoreFiles = new ScoreFiles(new File(getFilesDir(), "scores"), api);
    }

    Session session() {
        return session;
    }

    ApiClient api() {
        return api;
    }

    ScoreFiles scoreFiles() {
        return scoreFiles;
    }
}
