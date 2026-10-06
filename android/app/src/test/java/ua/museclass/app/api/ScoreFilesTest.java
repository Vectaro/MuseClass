package ua.museclass.app.api;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okio.Buffer;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/** Кеш файлів: перший раз — 200 і запис на диск, далі — If-None-Match і 304. */
public class ScoreFilesTest {
    private static final String ID = "0b5e6f1c-1111-4c2a-9d3e-000000000001";
    private static final byte[] XML = "<score-partwise/>".getBytes(StandardCharsets.UTF_8);

    @Rule
    public TemporaryFolder tmp = new TemporaryFolder();

    private MockWebServer server;
    private File dir;
    private ScoreFiles files;

    @Before
    public void setUp() throws IOException {
        server = new MockWebServer();
        server.start();
        ApiClient api = new ApiClient(server.url("/api").toString(), new ApiClient.TokenStore() {
            @Override
            public String token() {
                return "jwt";
            }

            @Override
            public void clear() {
            }
        });
        dir = new File(tmp.getRoot(), "scores");
        files = new ScoreFiles(dir, api);
    }

    @After
    public void tearDown() throws IOException {
        server.shutdown();
    }

    private static String sha256(byte[] b) throws Exception {
        StringBuilder s = new StringBuilder();
        for (byte x : MessageDigest.getInstance("SHA-256").digest(b)) s.append(String.format("%02x", x & 0xff));
        return s.toString();
    }

    private static MockResponse file(byte[] body, String etag) {
        return new MockResponse().setHeader("ETag", etag).setBody(new Buffer().write(body));
    }

    @Test
    public void storesThenRevalidates() throws Exception {
        String etag = "\"" + sha256(XML) + "\"";
        server.enqueue(file(XML, etag));
        server.enqueue(new MockResponse().setResponseCode(304));

        assertArrayEquals(XML, files.get(ID));
        assertNull(server.takeRequest().getHeader("If-None-Match"));
        assertTrue(new File(dir, ID + ".bin").isFile());

        assertArrayEquals(XML, files.get(ID));
        assertEquals(etag, server.takeRequest().getHeader("If-None-Match"));
    }

    @Test
    public void changedFileReplacesCopy() throws Exception {
        byte[] v2 = "<score-partwise version=\"4.0\"/>".getBytes(StandardCharsets.UTF_8);
        server.enqueue(file(XML, "\"" + sha256(XML) + "\""));
        server.enqueue(file(v2, "\"" + sha256(v2) + "\""));
        server.enqueue(new MockResponse().setResponseCode(304));

        files.get(ID);
        assertArrayEquals(v2, files.get(ID));
        assertArrayEquals(v2, files.get(ID));
        server.takeRequest();
        server.takeRequest();
        assertEquals("\"" + sha256(v2) + "\"", server.takeRequest().getHeader("If-None-Match"));
    }

    @Test
    public void etagNotMatchingContentIsNotCached() throws Exception {
        server.enqueue(file(XML, "\"deadbeef\""));
        server.enqueue(file(XML, "\"deadbeef\""));
        assertArrayEquals(XML, files.get(ID));
        assertFalse(new File(dir, ID + ".bin").exists());
        files.get(ID);
        server.takeRequest();
        assertNull(server.takeRequest().getHeader("If-None-Match"));
    }

    @Test
    public void forbiddenDropsCopy() throws Exception {
        server.enqueue(file(XML, "\"" + sha256(XML) + "\""));
        server.enqueue(new MockResponse().setResponseCode(403));
        files.get(ID);
        try {
            files.get(ID);
            fail();
        } catch (ApiException e) {
            assertEquals(403, e.status);
        }
        assertFalse(new File(dir, ID + ".bin").exists());
        assertFalse(new File(dir, ID + ".etag").exists());
    }

    @Test
    public void clearRemovesEverything() throws Exception {
        server.enqueue(file(XML, "\"" + sha256(XML) + "\""));
        files.get(ID);
        files.clear();
        String[] left = dir.list();
        assertEquals(0, left == null ? 0 : left.length);
    }

    @Test
    public void oddIdIsNeverWrittenToDisk() throws Exception {
        server.enqueue(file(XML, "\"" + sha256(XML) + "\""));
        assertArrayEquals(XML, files.get("..%2Fsession"));
        assertFalse(dir.exists());
    }
}
