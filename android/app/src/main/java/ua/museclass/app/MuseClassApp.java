package ua.museclass.app;

import android.app.Application;

import ua.museclass.app.api.ApiClient;

/** Одна сесія й один клієнт на весь застосунок. */
public final class MuseClassApp extends Application {
    private Session session;
    private ApiClient api;

    @Override
    public void onCreate() {
        super.onCreate();
        session = new Session(this);
        api = new ApiClient(BuildConfig.API_BASE, session);
    }

    Session session() {
        return session;
    }

    ApiClient api() {
        return api;
    }
}
