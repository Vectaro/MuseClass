package ua.museclass.app.api;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.IOException;
import java.util.List;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import okio.Buffer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Клієнт проти підставного сервера: запити, розбір відповідей і обидва формати помилок. */
public class ApiClientTest {
    private MockWebServer server;
    private FakeTokens tokens;
    private ApiClient api;

    private static final class FakeTokens implements ApiClient.TokenStore {
        String token;
        boolean cleared;

        @Override
        public String token() {
            return token;
        }

        @Override
        public void clear() {
            token = null;
            cleared = true;
        }
    }

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        tokens = new FakeTokens();
        api = new ApiClient(server.url("/api").toString(), tokens);
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private static MockResponse json(int code, String body) {
        return new MockResponse().setResponseCode(code).setHeader("Content-Type", "application/json").setBody(body);
    }

    private static MockResponse problem(int code, String detail) {
        return new MockResponse().setResponseCode(code)
                .setHeader("Content-Type", "application/problem+json")
                .setBody("{\"title\":\"x\",\"status\":" + code + ",\"detail\":\"" + detail + "\",\"instance\":\"/api/x\"}");
    }

    @Test
    public void loginSendsJsonWithoutToken() throws Exception {
        tokens.token = "old";
        server.enqueue(json(200, "{\"token\":\"jwt\",\"expiresAt\":\"2026-11-05T18:03:00.55Z\","
                + "\"userId\":\"u1\",\"displayName\":\"Тарас\"}"));
        Dto.Auth a = api.login("student@dev.museclass", "student-dev-1");
        assertEquals("jwt", a.token);
        assertEquals("Тарас", a.displayName);
        RecordedRequest r = server.takeRequest();
        assertEquals("POST", r.getMethod());
        assertEquals("/api/auth/login", r.getPath());
        assertNull(r.getHeader("Authorization"));
        assertTrue(r.getHeader("Content-Type").startsWith("application/json"));
        assertEquals("{\"email\":\"student@dev.museclass\",\"password\":\"student-dev-1\"}", r.getBody().readUtf8());
    }

    @Test
    public void libraryCarriesBearerAndParsesNulls() throws Exception {
        tokens.token = "jwt";
        server.enqueue(json(200, "[{\"classId\":\"c1\",\"className\":\"Dev: оркестр\",\"assignedAt\":\"2026-10-06T18:03:00Z\","
                + "\"score\":{\"id\":\"s1\",\"title\":\"Щедрик\",\"composer\":null,\"arranger\":null,\"kind\":\"folk\","
                + "\"visibility\":\"class\",\"measures\":8,\"updatedAt\":\"2026-10-06T18:03:00Z\",\"ownerId\":\"o\","
                + "\"ownerName\":\"Оксана\",\"instruments\":[\"piano\"],\"fits\":true}}]"));
        List<Dto.LibraryEntry> lib = api.library();
        assertEquals(1, lib.size());
        assertEquals("Щедрик", lib.get(0).score.title);
        assertNull(lib.get(0).score.composer);
        assertEquals(8, lib.get(0).score.measures);
        RecordedRequest r = server.takeRequest();
        assertEquals("/api/me/library", r.getPath());
        assertEquals("Bearer jwt", r.getHeader("Authorization"));
    }

    @Test
    public void problemDetailIsShownAsIs() throws Exception {
        server.enqueue(problem(409, "Акаунт з такою поштою вже є."));
        try {
            api.register("a@b.c", "12345678", "А");
            fail();
        } catch (ApiException e) {
            assertEquals(409, e.status);
            assertEquals("Акаунт з такою поштою вже є.", e.getMessage());
            assertFalse(e.unauthorized);
        }
    }

    @Test
    public void wrongPasswordIsNotSessionExpiry() throws Exception {
        server.enqueue(problem(401, "Невірна пошта або пароль."));
        try {
            api.login("a@b.c", "bad");
            fail();
        } catch (ApiException e) {
            assertEquals("Невірна пошта або пароль.", e.getMessage());
            assertFalse(e.unauthorized);
            assertFalse(tokens.cleared);
        }
    }

    @Test
    public void bare401DropsToken() throws Exception {
        tokens.token = "expired";
        server.enqueue(new MockResponse().setResponseCode(401).setHeader("WWW-Authenticate", "Bearer"));
        try {
            api.library();
            fail();
        } catch (ApiException e) {
            assertTrue(e.unauthorized);
            assertEquals(ApiException.SESSION, e.getMessage());
            assertTrue(tokens.cleared);
            assertNull(tokens.token);
        }
    }

    @Test
    public void springDefaultErrorIsGeneric() throws Exception {
        tokens.token = "jwt";
        server.enqueue(json(400, "{\"timestamp\":\"2026-10-06T18:03:00Z\",\"status\":400,"
                + "\"error\":\"Bad Request\",\"path\":\"/api/scores/123\"}"));
        try {
            api.score("123");
            fail();
        } catch (ApiException e) {
            assertEquals(400, e.status);
            assertEquals(ApiException.GENERIC, e.getMessage());
        }
    }

    @Test
    public void serverErrorWithoutDetail() throws Exception {
        tokens.token = "jwt";
        server.enqueue(new MockResponse().setResponseCode(502).setBody("<html>Bad Gateway</html>"));
        try {
            api.library();
            fail();
        } catch (ApiException e) {
            assertEquals(ApiException.SERVER, e.getMessage());
        }
    }

    @Test
    public void noServerIsNetworkError() throws Exception {
        server.shutdown();
        try {
            api.login("a@b.c", "12345678");
            fail();
        } catch (ApiException e) {
            assertEquals(0, e.status);
            assertEquals(ApiException.NETWORK, e.getMessage());
        }
    }

    @Test
    public void scoreViewAndFile() throws Exception {
        tokens.token = "jwt";
        server.enqueue(json(200, "{\"score\":{\"id\":\"s1\",\"title\":\"Марш\",\"format\":\"mxl\",\"measures\":8},"
                + "\"parts\":[{\"position\":0,\"partId\":\"P1\",\"name\":\"Труба\",\"instrument\":\"trumpet\"},"
                + "{\"position\":1,\"partId\":\"P2\",\"name\":\"Тромбон\",\"instrument\":null}],\"canEdit\":false}"));
        byte[] zip = {'P', 'K', 3, 4, 0, 1};
        server.enqueue(new MockResponse().setHeader("Content-Type", "application/vnd.recordare.musicxml")
                .setHeader("ETag", "\"abc\"").setBody(new Buffer().write(zip)));
        Dto.ScoreView v = api.score("s1");
        assertEquals("mxl", v.score.format);
        assertEquals(2, v.parts.size());
        assertNull(v.parts.get(1).instrument);
        ApiClient.FileResult f = api.scoreFile("s1", null);
        assertArrayEquals(zip, f.bytes);
        assertEquals("\"abc\"", f.etag);
        assertFalse(f.notModified);
        server.takeRequest();
        RecordedRequest req = server.takeRequest();
        assertEquals("/api/scores/s1/file", req.getPath());
        assertNull(req.getHeader("If-None-Match"));
    }

    @Test
    public void fileNotModified() throws Exception {
        tokens.token = "jwt";
        server.enqueue(new MockResponse().setResponseCode(304));
        ApiClient.FileResult f = api.scoreFile("s1", "\"abc\"");
        assertTrue(f.notModified);
        assertNull(f.bytes);
        RecordedRequest req = server.takeRequest();
        assertEquals("\"abc\"", req.getHeader("If-None-Match"));
        assertEquals("Bearer jwt", req.getHeader("Authorization"));
    }

    @Test
    public void joinClass() throws Exception {
        tokens.token = "jwt";
        server.enqueue(json(200, "{\"id\":\"c1\",\"code\":null,\"name\":\"Dev: оркестр\","
                + "\"teacherName\":\"Оксана Кравець\",\"role\":\"student\",\"students\":1}"));
        server.enqueue(new MockResponse().setResponseCode(404).setHeader("Content-Type", "application/problem+json")
                .setBody("{\"status\":404,\"detail\":\"Клас не знайдено.\"}"));
        Dto.ClassInfo c = api.joinClass("dev 6k");
        assertEquals("Dev: оркестр", c.name);
        assertEquals("student", c.role);
        assertNull(c.code);
        RecordedRequest req = server.takeRequest();
        assertEquals("POST", req.getMethod());
        assertEquals("/api/classes/join", req.getPath());
        assertEquals("Bearer jwt", req.getHeader("Authorization"));
        assertEquals("{\"code\":\"dev 6k\"}", req.getBody().readUtf8());
        try {
            api.joinClass("XXX-99");
            fail();
        } catch (ApiException e) {
            assertEquals(404, e.status);
            assertEquals("Клас не знайдено.", e.getMessage());
            assertFalse(e.unauthorized);
        }
    }

    @Test
    public void catalogSendsQueryKindAndPaging() throws Exception {
        tokens.token = "jwt";
        server.enqueue(json(200, "[{\"id\":\"s1\",\"title\":\"Щедрик\",\"fits\":true,"
                + "\"instruments\":[\"piano\",\"voice\"],\"visibility\":\"public\"}]"));
        server.enqueue(json(200, "[]"));
        List<Dto.Summary> l = api.catalog(" щед ", "folk", 30, 0);
        assertEquals(1, l.size());
        assertTrue(l.get(0).fits);
        RecordedRequest r = server.takeRequest();
        assertEquals("/api/catalog", r.getRequestUrl().encodedPath());
        assertEquals("щед", r.getRequestUrl().queryParameter("q"));
        assertEquals("folk", r.getRequestUrl().queryParameter("kind"));
        assertEquals("30", r.getRequestUrl().queryParameter("limit"));
        assertEquals("0", r.getRequestUrl().queryParameter("offset"));
        api.catalog("", null, 30, 0);
        RecordedRequest r2 = server.takeRequest();
        assertNull(r2.getRequestUrl().queryParameter("q"));
        assertNull(r2.getRequestUrl().queryParameter("kind"));
    }

    @Test
    public void savedPutAndDelete() throws Exception {
        tokens.token = "jwt";
        server.enqueue(new MockResponse().setResponseCode(204));
        server.enqueue(new MockResponse().setResponseCode(204));
        api.setSaved("s1", true);
        api.setSaved("s1", false);
        RecordedRequest put = server.takeRequest();
        assertEquals("PUT", put.getMethod());
        assertEquals("/api/me/saved/s1", put.getPath());
        assertEquals("Bearer jwt", put.getHeader("Authorization"));
        RecordedRequest del = server.takeRequest();
        assertEquals("DELETE", del.getMethod());
        assertEquals("/api/me/saved/s1", del.getPath());
    }

    @Test
    public void profileNameAndInstruments() throws Exception {
        tokens.token = "jwt";
        server.enqueue(json(200, "{\"displayName\":\"Анна\",\"instruments\":[]}"));
        server.enqueue(json(200, "{\"displayName\":\"Анна\",\"instruments\":[\"piano\",\"trumpet\"]}"));
        assertEquals("Анна", api.updateName("Анна").displayName);
        assertEquals(2, api.setInstruments(java.util.Arrays.asList("trumpet", "piano")).instruments.size());
        RecordedRequest a = server.takeRequest();
        assertEquals("PATCH", a.getMethod());
        assertEquals("/api/me", a.getPath());
        assertEquals("{\"displayName\":\"Анна\"}", a.getBody().readUtf8());
        RecordedRequest b = server.takeRequest();
        assertEquals("PUT", b.getMethod());
        assertEquals("/api/me/instruments", b.getPath());
        assertEquals("{\"instruments\":[\"trumpet\",\"piano\"]}", b.getBody().readUtf8());
    }

    @Test
    public void classesListAndCreate() throws Exception {
        tokens.token = "jwt";
        server.enqueue(json(200, "[{\"id\":\"c1\",\"name\":\"Dev: оркестр\",\"role\":\"student\",\"students\":3}]"));
        server.enqueue(json(201, "{\"id\":\"c2\",\"code\":\"GIT-3K\",\"name\":\"Гітара\",\"role\":\"teacher\"}"));
        assertEquals("student", api.classes().get(0).role);
        Dto.ClassInfo c = api.createClass("Гітара");
        assertEquals("GIT-3K", c.code);
        server.takeRequest();
        RecordedRequest r = server.takeRequest();
        assertEquals("POST", r.getMethod());
        assertEquals("/api/classes", r.getPath());
        assertEquals("{\"name\":\"Гітара\"}", r.getBody().readUtf8());
    }

    @Test
    public void detailOfIgnoresJunk() {
        assertNull(ApiClient.detailOf("не json"));
        assertNull(ApiClient.detailOf("[1,2]"));
        assertNull(ApiClient.detailOf("{\"detail\":\"  \"}"));
        assertNull(ApiClient.detailOf("{\"detail\":{\"a\":1}}"));
        assertEquals("Клас не знайдено.", ApiClient.detailOf("{\"detail\":\"Клас не знайдено.\"}"));
    }
}
