package ua.museclass;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.json.JsonParser;
import org.springframework.boot.json.JsonParserFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Наскрізний сценарій через справжній HTTP і справжній Postgres у контейнері:
 * викладач, учень і чужий користувач проходять шлях від реєстрації до видачі нот.
 * Потрібен запущений Docker.
 */
@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "museclass.jwt.secret=test-secret-that-is-long-enough-for-hs256")
class ApiFlowTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer postgres = new PostgreSQLContainer("postgres:16-alpine");

    @Value("${local.server.port}")
    int port;

    final HttpClient http = HttpClient.newHttpClient();
    final JsonParser json = JsonParserFactory.getJsonParser();
    String base;

    record Res(int status, String body, java.net.http.HttpHeaders headers) {
        Map<String, Object> map() {
            return JsonParserFactory.getJsonParser().parseMap(body);
        }

        List<Object> list() {
            return JsonParserFactory.getJsonParser().parseList(body);
        }
    }

    @BeforeEach
    void setUp() {
        base = "http://localhost:" + port;
    }

    @Test
    void fullTeacherStudentFlow() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // --- відкрите і закрите
        assertEquals(200, get("/actuator/health", null).status());
        assertEquals(401, get("/api/me", null).status());
        assertEquals(401, get("/api/me", "not-a-token").status());

        // --- реєстрація і вхід
        String teacher = token(send("POST", "/api/auth/register", null,
                obj("email", "Teacher-" + suffix + "@example.com", "password", "correct-horse", "displayName", "Оксана Кравець")), 201);
        String student = token(send("POST", "/api/auth/register", null,
                obj("email", "student-" + suffix + "@example.com", "password", "correct-horse", "displayName", "Влад")), 201);
        String outsider = token(send("POST", "/api/auth/register", null,
                obj("email", "other-" + suffix + "@example.com", "password", "correct-horse", "displayName", "Чужий")), 201);

        Res dup = send("POST", "/api/auth/register", null,
                obj("email", "teacher-" + suffix + "@EXAMPLE.com", "password", "correct-horse", "displayName", "Дубль"));
        assertEquals(409, dup.status(), dup.body());
        Res badEmail = send("POST", "/api/auth/register", null,
                obj("email", "не пошта", "password", "correct-horse", "displayName", "X"));
        assertEquals(400, badEmail.status());
        assertEquals("Пошта виглядає неправильно.", badEmail.map().get("detail"));
        assertEquals(400, send("POST", "/api/auth/register", null,
                obj("email", "a" + suffix + "@example.com", "password", "short", "displayName", "X")).status());

        assertEquals(401, send("POST", "/api/auth/login", null,
                obj("email", "student-" + suffix + "@example.com", "password", "wrong-password")).status());
        assertEquals(401, send("POST", "/api/auth/login", null,
                obj("email", "nobody-" + suffix + "@example.com", "password", "whatever-123")).status());
        student = token(send("POST", "/api/auth/login", null,
                obj("email", "STUDENT-" + suffix + "@example.com", "password", "correct-horse")), 200);

        // --- профіль та інструменти
        Res me = get("/api/me", student);
        assertEquals(200, me.status());
        assertEquals("Влад", me.map().get("displayName"));
        Res instr = send("PUT", "/api/me/instruments", student, "{\"instruments\":[\"trumpet\",\"TRUMPET\"]}");
        assertEquals(200, instr.status(), instr.body());
        assertEquals(List.of("trumpet"), instr.map().get("instruments"));
        assertEquals(400, send("PUT", "/api/me/instruments", student, "{\"instruments\":[\"theremin\"]}").status());

        // --- клас
        Res created = send("POST", "/api/classes", teacher, obj("name", "Фортепіано, 3 клас", "codePrefix", "pno"));
        assertEquals(201, created.status(), created.body());
        String classId = (String) created.map().get("id");
        String code = (String) created.map().get("code");
        assertTrue(code.matches("PNO-[2-9][A-Z]"), code);
        assertEquals("teacher", created.map().get("role"));
        assertEquals(400, send("POST", "/api/classes", teacher, obj("name", "X", "codePrefix", "P1")).status());

        // учень вводить код маленькими літерами, без дефіса, з кириличними О і Р
        String typed = code.toLowerCase().replace("-", " ").replace('o', 'о').replace('p', 'р');
        Res joined = send("POST", "/api/classes/join", student, obj("code", typed));
        assertEquals(200, joined.status(), joined.body());
        assertEquals("student", joined.map().get("role"));
        assertNull(joined.map().get("code"), "учню код не віддаємо");
        assertEquals(409, send("POST", "/api/classes/join", teacher, obj("code", code)).status());
        assertEquals(400, send("POST", "/api/classes/join", student, obj("code", "щось")).status());
        assertEquals(404, send("POST", "/api/classes/join", student, obj("code", "ZZZ-9Z")).status());
        assertEquals(404, get("/api/classes/" + classId, outsider).status());

        Res members = get("/api/classes/" + classId + "/members", teacher);
        assertEquals(1, members.list().size());

        // --- завантаження: приватна партитура викладача
        Res up = upload("POST", "/api/scores", teacher, mxl(SHCHEDRYK), "shchedryk.mxl", Map.of());
        assertEquals(201, up.status(), up.body());
        Map<String, Object> view = up.map();
        @SuppressWarnings("unchecked")
        Map<String, Object> score = (Map<String, Object>) view.get("score");
        String scoreId = (String) score.get("id");
        assertEquals("Щедрик", score.get("title"));
        assertEquals("М. Леонтович", score.get("composer"));
        assertEquals("private", score.get("visibility"));
        assertEquals("mxl", score.get("format"));
        assertEquals(Boolean.TRUE, view.get("canEdit"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> parts = (List<Map<String, Object>>) view.get("parts");
        assertEquals("piano", parts.get(0).get("instrument"));
        assertEquals("trumpet", parts.get(1).get("instrument"));

        assertEquals(404, get("/api/scores/" + scoreId, student).status(), "приватну учень не бачить");

        // --- видача класу
        assertEquals(403, send("PUT", "/api/classes/" + classId + "/scores/" + scoreId, student, null).status());
        assertEquals(204, send("PUT", "/api/classes/" + classId + "/scores/" + scoreId, teacher, null).status());
        Res seen = get("/api/scores/" + scoreId, student);
        assertEquals(200, seen.status());
        @SuppressWarnings("unchecked")
        Map<String, Object> seenScore = (Map<String, Object>) seen.map().get("score");
        assertEquals("class", seenScore.get("visibility"), "видана приватна стала класною");
        assertEquals(Boolean.FALSE, seen.map().get("canEdit"));
        assertEquals(404, get("/api/scores/" + scoreId, outsider).status());
        assertEquals(1, get("/api/me/library", student).list().size());
        assertEquals(1, get("/api/classes/" + classId + "/scores", student).list().size());

        // --- файл і ETag
        Res file = get("/api/scores/" + scoreId + "/file", student);
        assertEquals(200, file.status());
        String etag = file.headers().firstValue("ETag").orElseThrow();
        assertTrue(file.headers().firstValue("Content-Type").orElseThrow().startsWith("application/vnd.recordare.musicxml"));
        HttpResponse<String> notModified = http.send(HttpRequest.newBuilder(URI.create(base + "/api/scores/" + scoreId + "/file"))
                .header("Authorization", "Bearer " + student).header("If-None-Match", etag).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertEquals(304, notModified.statusCode());

        // --- права: учень бачить, але не змінює; чужий навіть не бачить
        assertEquals(403, send("PATCH", "/api/scores/" + scoreId, student, obj("title", "Злам")).status());
        assertEquals(404, send("PATCH", "/api/scores/" + scoreId, outsider, obj("title", "Злам")).status());
        assertEquals(404, send("DELETE", "/api/scores/" + scoreId, outsider, null).status());

        // --- публічний каталог і авторські права
        Res cover = upload("POST", "/api/scores", teacher, SHCHEDRYK.getBytes(StandardCharsets.UTF_8), "cover.musicxml",
                Map.of("visibility", "public", "rights", "arrangement", "kind", "cover"));
        assertEquals(400, cover.status());
        assertTrue(((String) cover.map().get("detail")).contains("public domain"));
        Res folk = upload("POST", "/api/scores", teacher, SHCHEDRYK.getBytes(StandardCharsets.UTF_8), "folk.musicxml",
                Map.of("visibility", "public", "rights", "folk", "kind", "folk", "title", "Щедрик (дует " + suffix + ")"));
        assertEquals(201, folk.status(), folk.body());
        Res pianoOnly = upload("POST", "/api/scores", outsider, PIANO_ONLY.getBytes(StandardCharsets.UTF_8), "a.musicxml",
                Map.of("visibility", "public", "rights", "public_domain", "title", "А-етюд " + suffix));
        assertEquals(201, pianoOnly.status(), pianoOnly.body());

        List<Object> cat = get("/api/catalog?q=" + suffix, student).list();
        assertEquals(2, cat.size());
        @SuppressWarnings("unchecked")
        Map<String, Object> first = (Map<String, Object>) cat.get(0);
        assertEquals(Boolean.TRUE, first.get("fits"), "трубачу партія труби вище, хоч за алфавітом «А-етюд» перший");
        assertEquals(400, get("/api/catalog?kind=jazz", student).status());

        // --- збережене
        String folkId = (String) ((Map<?, ?>) folk.map().get("score")).get("id");
        assertEquals(204, send("PUT", "/api/me/saved/" + folkId, student, null).status());
        assertEquals(1, get("/api/me/saved", student).list().size());
        assertEquals(404, send("PUT", "/api/me/saved/" + scoreId, outsider, null).status());

        // --- биті файли
        Res garbage = upload("POST", "/api/scores", teacher, "це не ноти".getBytes(StandardCharsets.UTF_8), "x.musicxml", Map.of());
        assertEquals(422, garbage.status());
        assertFalse(((String) garbage.map().get("detail")).isBlank());

        // --- нова версія з редактора
        Res replaced = upload("PUT", "/api/scores/" + scoreId + "/file", teacher,
                PIANO_ONLY.getBytes(StandardCharsets.UTF_8), "v2.musicxml", Map.of());
        assertEquals(200, replaced.status(), replaced.body());
        assertEquals(1, ((List<?>) replaced.map().get("parts")).size());
        Res file2 = get("/api/scores/" + scoreId + "/file", student);
        assertFalse(etag.equals(file2.headers().firstValue("ETag").orElseThrow()), "новий файл — новий ETag");

        // --- перевипуск коду: старий більше не працює
        Res newCode = send("POST", "/api/classes/" + classId + "/code", teacher, null);
        assertEquals(200, newCode.status(), newCode.body());
        assertFalse(code.equals(newCode.map().get("code")));
        assertEquals(404, send("POST", "/api/classes/join", outsider, obj("code", code)).status());

        // --- учень виходить сам
        String studentId = (String) get("/api/me", student).map().get("id");
        assertEquals(204, send("DELETE", "/api/classes/" + classId + "/members/" + studentId, student, null).status());
        assertEquals(0, get("/api/me/library", student).list().size());
        assertEquals(404, get("/api/scores/" + scoreId, student).status());
    }

    /** Будь-яка помилка — problem+json зі status і detail українською, і ті, що не з нашого коду. */
    @Test
    void everyErrorIsProblemJson() throws Exception {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        String user = token(send("POST", "/api/auth/register", null,
                obj("email", "errors-" + suffix + "@example.com", "password", "correct-horse", "displayName", "Помилка")), 201);

        // токен: немає, зіпсований, прострочений — тіло є, WWW-Authenticate лишився
        Res none = get("/api/me", null);
        assertProblem(none, 401, "Потрібно увійти.");
        assertTrue(none.headers().firstValue("WWW-Authenticate").orElse("").startsWith("Bearer"));
        Res broken = get("/api/me", "not-a-token");
        assertProblem(broken, 401, "Недійсний токен. Увійди знову.");
        assertTrue(broken.headers().firstValue("WWW-Authenticate").orElse("").contains("invalid_token"));
        assertProblem(get("/api/me", expiredToken()), 401, "Сесія закінчилась. Увійди знову.");

        // помилки Spring MVC, до коду контролерів не доходять
        assertProblem(send("POST", "/api/classes", user, "{broken"), 400,
                "Тіло запиту не читається: потрібен коректний JSON.");
        assertProblem(get("/api/classes/123", user), 400, "Некоректний ідентифікатор: 123");
        assertProblem(get("/api/catalog?limit=abc", user), 400, "Параметр «limit» має неправильний формат: abc");
        assertProblem(get("/api/nope", user), 404, "Такого шляху в API немає.");
        Res method = send("PUT", "/api/classes", user, "{}");
        assertProblem(method, 405, "Метод PUT тут не підтримується.");
        assertTrue(method.headers().firstValue("Allow").isPresent());
        assertProblem(send("POST", "/api/scores", user, "{}"), 415, "Непідтримуваний тип тіла запиту (Content-Type).");

        // і наші власні — як і були
        assertProblem(send("POST", "/api/auth/login", null, obj("email", "nobody-" + suffix + "@example.com",
                "password", "whatever-123")), 401, "Невірна пошта або пароль.");
    }

    void assertProblem(Res r, int status, String detail) {
        assertEquals(status, r.status(), r.body());
        assertTrue(r.headers().firstValue("Content-Type").orElse("").startsWith("application/problem+json"),
                r.headers().toString());
        Map<String, Object> m = r.map();
        assertEquals(status, ((Number) m.get("status")).intValue());
        assertEquals(detail, m.get("detail"));
    }

    /** JWT, підписаний тим самим тестовим секретом, але прострочений годину тому. */
    static String expiredToken() throws Exception {
        java.util.Base64.Encoder b64 = java.util.Base64.getUrlEncoder().withoutPadding();
        long now = System.currentTimeMillis() / 1000;
        String header = b64.encodeToString("{\"alg\":\"HS256\"}".getBytes(StandardCharsets.UTF_8));
        String claims = b64.encodeToString(("{\"iss\":\"museclass\",\"sub\":\"" + UUID.randomUUID()
                + "\",\"iat\":" + (now - 7200) + ",\"exp\":" + (now - 3600) + "}").getBytes(StandardCharsets.UTF_8));
        javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
        mac.init(new javax.crypto.spec.SecretKeySpec(
                "test-secret-that-is-long-enough-for-hs256".getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        String sig = b64.encodeToString(mac.doFinal((header + "." + claims).getBytes(StandardCharsets.UTF_8)));
        return header + "." + claims + "." + sig;
    }

    // ---------------------------------------------------------------- HTTP

    Res get(String path, String token) throws IOException, InterruptedException {
        return send("GET", path, token, null);
    }

    Res send(String method, String path, String token, String jsonBody) throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(base + path));
        if (token != null) b.header("Authorization", "Bearer " + token);
        if (jsonBody != null) {
            b.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(jsonBody));
        } else {
            b.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> r = http.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Res(r.statusCode(), r.body(), r.headers());
    }

    Res upload(String method, String path, String token, byte[] file, String fileName, Map<String, String> fields)
            throws IOException, InterruptedException {
        String boundary = "----museclass" + UUID.randomUUID();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        for (Map.Entry<String, String> f : fields.entrySet()) {
            body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + f.getKey() + "\"\r\n"
                    + "Content-Type: text/plain; charset=UTF-8\r\n\r\n" + f.getValue() + "\r\n").getBytes(StandardCharsets.UTF_8));
        }
        body.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"" + fileName + "\"\r\n"
                + "Content-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(file);
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        HttpRequest req = HttpRequest.newBuilder(URI.create(base + path))
                .header("Authorization", "Bearer " + token)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .method(method, HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        HttpResponse<String> r = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        return new Res(r.statusCode(), r.body(), r.headers());
    }

    String token(Res r, int expectedStatus) {
        assertEquals(expectedStatus, r.status(), r.body());
        Object t = r.map().get("token");
        assertNotNull(t);
        return (String) t;
    }

    /** Плоский JSON-об'єкт з рядкових пар ключ-значення. */
    static String obj(String... kv) {
        StringBuilder sb = new StringBuilder("{");
        for (int i = 0; i < kv.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append('"').append(esc(kv[i])).append("\":\"").append(esc(kv[i + 1])).append('"');
        }
        return sb.append('}').toString();
    }

    static String esc(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    static byte[] mxl(String xml) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            z.putNextEntry(new ZipEntry("META-INF/container.xml"));
            z.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?><container><rootfiles>"
                    + "<rootfile full-path=\"score.musicxml\"/></rootfiles></container>").getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
            z.putNextEntry(new ZipEntry("score.musicxml"));
            z.write(xml.getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        return bos.toByteArray();
    }

    static final String SHCHEDRYK = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE score-partwise PUBLIC "-//Recordare//DTD MusicXML 4.0 Partwise//EN" "http://www.musicxml.org/dtds/partwise.dtd">
            <score-partwise version="4.0">
              <work><work-title>Щедрик</work-title></work>
              <identification><creator type="composer">М. Леонтович</creator></identification>
              <part-list>
                <score-part id="P1"><part-name>Фортепіано</part-name></score-part>
                <score-part id="P2"><part-name>Труба in B♭</part-name></score-part>
              </part-list>
              <part id="P1"><measure number="1"><note><rest/><duration>4</duration></note></measure></part>
              <part id="P2"><measure number="1"><note><rest/><duration>4</duration></note></measure></part>
            </score-partwise>
            """;

    static final String PIANO_ONLY = """
            <?xml version="1.0" encoding="UTF-8"?>
            <score-partwise version="4.0">
              <work><work-title>Етюд</work-title></work>
              <part-list><score-part id="P1"><part-name>Piano</part-name></score-part></part-list>
              <part id="P1"><measure number="1"/><measure number="2"/></part>
            </score-partwise>
            """;
}
