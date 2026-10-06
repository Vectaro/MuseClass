package ua.museclass.app.api;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.regex.Pattern;

/**
 * Файли партитур на диску з ревалідацією за ETag (docs/api.md, «Файл»).
 * Копія — пара {@code <id>.bin} + {@code <id>.etag}; без однієї з них копії
 * немає. Без Android-класів: на пристрої тека — filesDir/scores.
 */
public final class ScoreFiles {
    private static final Pattern SAFE_ID = Pattern.compile("[A-Za-z0-9-]{1,64}");

    private final File dir;
    private final ApiClient api;

    public ScoreFiles(File dir, ApiClient api) {
        this.dir = dir;
        this.api = api;
    }

    /**
     * Актуальні байти файлу. Є копія — питаємо сервер з If-None-Match і на 304
     * віддаємо її. Нові байти кладемо на диск, якщо їхній sha256 збігся з ETag.
     */
    public byte[] get(String id) throws ApiException {
        boolean cacheable = SAFE_ID.matcher(id).matches();
        File bin = new File(dir, id + ".bin");
        File tag = new File(dir, id + ".etag");
        String etag = null;
        byte[] cached = null;
        if (cacheable && bin.isFile() && tag.isFile()) {
            try {
                etag = new String(Files.read(tag), StandardCharsets.UTF_8);
                cached = Files.read(bin);
            } catch (IOException e) {
                etag = null;
                cached = null;
            }
        }
        ApiClient.FileResult r;
        try {
            r = api.scoreFile(id, etag);
        } catch (ApiException e) {
            // доступ забрали або партитуру видалили — копія більше не наша
            if (e.status == 403 || e.status == 404) delete(bin, tag);
            throw e;
        }
        if (r.notModified) {
            if (cached == null) throw new ApiException(304, ApiException.GENERIC, false, null);
            return cached;
        }
        if (cacheable && matches(r.etag, r.bytes)) {
            try {
                store(bin, tag, r.bytes, r.etag);
            } catch (IOException e) {
                delete(bin, tag); // не вдалося — просто без копії
            }
        } else if (cacheable) {
            delete(bin, tag);
        }
        return r.bytes;
    }

    /** Стерти всі копії — при виході з акаунта. */
    public void clear() {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    /** ETag сервера — sha256 вмісту в лапках; інше не кешуємо. */
    static boolean matches(String etag, byte[] bytes) {
        if (etag == null || etag.length() < 3 || !etag.startsWith("\"") || !etag.endsWith("\"")) return false;
        String want = etag.substring(1, etag.length() - 1);
        try {
            byte[] d = MessageDigest.getInstance("SHA-256").digest(bytes);
            StringBuilder hex = new StringBuilder(d.length * 2);
            for (byte x : d) hex.append(String.format("%02x", x & 0xff));
            return hex.toString().equalsIgnoreCase(want);
        } catch (NoSuchAlgorithmException e) {
            return false;
        }
    }

    /** Спершу тимчасові файли, потім заміна; etag — останнім, щоб пара не розійшлась. */
    private void store(File bin, File tag, byte[] bytes, String etag) throws IOException {
        if (!dir.isDirectory() && !dir.mkdirs()) throw new IOException("не створилась " + dir);
        File binTmp = new File(dir, bin.getName() + ".tmp");
        File tagTmp = new File(dir, tag.getName() + ".tmp");
        write(binTmp, bytes);
        write(tagTmp, etag.getBytes(StandardCharsets.UTF_8));
        delete(tag);
        if (!(replace(binTmp, bin) && replace(tagTmp, tag))) throw new IOException("не замінився файл");
    }

    private static boolean replace(File from, File to) {
        return (!to.exists() || to.delete()) && from.renameTo(to);
    }

    private static void write(File f, byte[] bytes) throws IOException {
        try (OutputStream out = new FileOutputStream(f)) {
            out.write(bytes);
        }
    }

    private static void delete(File... files) {
        for (File f : files) {
            //noinspection ResultOfMethodCallIgnored
            f.delete();
        }
    }

    /** Читання цілого файлу під Java 8 / API 24 (без java.nio.file). */
    private static final class Files {
        static byte[] read(File f) throws IOException {
            try (FileInputStream in = new FileInputStream(f)) {
                ByteArrayOutputStream out = new ByteArrayOutputStream((int) f.length());
                byte[] buf = new byte[8192];
                for (int n; (n = in.read(buf)) > 0; ) out.write(buf, 0, n);
                return out.toByteArray();
            }
        }
    }
}
