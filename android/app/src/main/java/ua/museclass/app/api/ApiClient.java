package ua.museclass.app.api;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonParser;
import com.google.gson.reflect.TypeToken;

import java.io.IOException;
import java.lang.reflect.Type;
import java.util.List;
import java.util.concurrent.TimeUnit;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;

/**
 * REST-клієнт MuseClass. Виклики синхронні — кликати не з головного потоку.
 * Без Android-класів, тож тестується на JVM проти MockWebServer.
 */
public final class ApiClient {

    /** Де лежить токен. На пристрої — SharedPreferences, у тестах — поле. */
    public interface TokenStore {
        String token();

        void clear();
    }

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");

    private final String base;
    private final TokenStore tokens;
    private final OkHttpClient http;
    private final Gson gson = new Gson();

    public ApiClient(String base, TokenStore tokens) {
        this(base, tokens, new OkHttpClient.Builder()
                .connectTimeout(10, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build());
    }

    public ApiClient(String base, TokenStore tokens, OkHttpClient http) {
        this.base = base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
        this.tokens = tokens;
        this.http = http;
    }

    // ---- акаунт ----

    public Dto.Auth login(String email, String password) throws ApiException {
        JsonObject b = new JsonObject();
        b.addProperty("email", email);
        b.addProperty("password", password);
        return call(post("/auth/login", b, false), Dto.Auth.class, false);
    }

    public Dto.Auth register(String email, String password, String displayName) throws ApiException {
        JsonObject b = new JsonObject();
        b.addProperty("email", email);
        b.addProperty("password", password);
        b.addProperty("displayName", displayName);
        return call(post("/auth/register", b, false), Dto.Auth.class, false);
    }

    public Dto.Profile me() throws ApiException {
        return call(get("/me"), Dto.Profile.class, true);
    }

    // ---- бібліотека і партитури ----

    public List<Dto.LibraryEntry> library() throws ApiException {
        Type t = new TypeToken<List<Dto.LibraryEntry>>() { }.getType();
        return call(get("/me/library"), t, true);
    }

    public Dto.ScoreView score(String id) throws ApiException {
        return call(get("/scores/" + id), Dto.ScoreView.class, true);
    }

    /** Оригінальні байти файлу: .musicxml або .mxl. */
    public byte[] scoreFile(String id) throws ApiException {
        try (Response r = http.newCall(get("/scores/" + id + "/file")).execute()) {
            if (!r.isSuccessful()) throw error(r, true);
            return body(r).bytes();
        } catch (IOException e) {
            throw ApiException.network(e);
        }
    }

    // ---- нутрощі ----

    private Request get(String path) {
        return authed(new Request.Builder().url(base + path).get()).build();
    }

    private Request post(String path, JsonElement json, boolean auth) {
        Request.Builder b = new Request.Builder().url(base + path).post(RequestBody.create(json.toString(), JSON));
        return (auth ? authed(b) : b).build();
    }

    private Request.Builder authed(Request.Builder b) {
        String t = tokens.token();
        return t == null ? b : b.header("Authorization", "Bearer " + t);
    }

    private <T> T call(Request req, Type type, boolean auth) throws ApiException {
        try (Response r = http.newCall(req).execute()) {
            if (!r.isSuccessful()) throw error(r, auth);
            String s = body(r).string();
            try {
                return gson.fromJson(s, type);
            } catch (JsonParseException e) {
                throw new ApiException(r.code(), ApiException.GENERIC, false, e);
            }
        } catch (IOException e) {
            throw ApiException.network(e);
        }
    }

    private static ResponseBody body(Response r) throws IOException {
        ResponseBody b = r.body();
        if (b == null) throw new IOException("порожня відповідь");
        return b;
    }

    /**
     * Два формати помилок (docs/api.md): problem+json з detail — показуємо
     * detail; усе інше (401 без тіла, Spring за замовчуванням) — загальний текст.
     */
    private ApiException error(Response r, boolean auth) {
        int code = r.code();
        String detail = null;
        try {
            ResponseBody b = r.body();
            String ct = r.header("Content-Type", "");
            if (b != null && ct != null && ct.contains("json")) detail = detailOf(b.string());
        } catch (IOException ignored) {
            // тіло не прочиталось — лишається загальний текст
        }
        // 401 на будь-якому запиті з токеном: токен більше не діє
        boolean unauthorized = code == 401 && auth;
        if (unauthorized) tokens.clear();
        String message = detail != null ? detail
                : unauthorized ? ApiException.SESSION
                : code >= 500 ? ApiException.SERVER
                : ApiException.GENERIC;
        return new ApiException(code, message, unauthorized, null);
    }

    static String detailOf(String body) {
        try {
            JsonElement e = JsonParser.parseString(body);
            if (!e.isJsonObject()) return null;
            JsonElement d = e.getAsJsonObject().get("detail");
            if (d == null || !d.isJsonPrimitive()) return null;
            String s = d.getAsString().trim();
            return s.isEmpty() ? null : s;
        } catch (JsonParseException | IllegalStateException e) {
            return null;
        }
    }
}
