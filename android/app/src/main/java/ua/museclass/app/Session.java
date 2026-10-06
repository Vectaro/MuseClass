package ua.museclass.app;

import android.content.Context;
import android.content.SharedPreferences;

import java.time.Instant;
import java.time.format.DateTimeParseException;

import ua.museclass.app.api.ApiClient;
import ua.museclass.app.api.Dto;

/**
 * Вхід користувача: JWT і хто це. Лежить у SharedPreferences «session»,
 * який виключено з резервних копій (res/xml/*_rules.xml).
 */
public final class Session implements ApiClient.TokenStore {
    private static final String FILE = "session";
    private final SharedPreferences prefs;

    Session(Context context) {
        prefs = context.getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    void save(Dto.Auth auth) {
        prefs.edit()
                .putString("token", auth.token)
                .putString("expiresAt", auth.expiresAt)
                .putString("userId", auth.userId)
                .putString("displayName", auth.displayName)
                .apply();
    }

    /** Є токен і він ще не прострочений (з хвилиною запасу). */
    boolean isActive() {
        if (prefs.getString("token", null) == null) return false;
        String exp = prefs.getString("expiresAt", null);
        if (exp == null) return true;
        try {
            return Instant.parse(exp).isAfter(Instant.now().plusSeconds(60));
        } catch (DateTimeParseException e) {
            return true;
        }
    }

    String displayName() {
        return prefs.getString("displayName", "");
    }

    @Override
    public String token() {
        return prefs.getString("token", null);
    }

    @Override
    public void clear() {
        prefs.edit().clear().apply();
    }
}
