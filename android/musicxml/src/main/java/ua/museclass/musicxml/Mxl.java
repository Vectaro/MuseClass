package ua.museclass.musicxml;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Стиснений MusicXML (.mxl) — звичайний zip з META-INF/container.xml і самою
 * партитурою всередині. MuseScore за замовчуванням зберігає саме так.
 */
final class Mxl {
    /** Захист від zip-бомби: сервер приймає файли до 5 МБ, розпаковано — з запасом. */
    static final long MAX_UNPACKED = 64L * 1024 * 1024;
    static final int MAX_ENTRIES = 1000;

    private Mxl() {
    }

    static boolean isZip(byte[] b) {
        return b.length >= 2 && b[0] == 'P' && b[1] == 'K';
    }

    /** Байти партитури з архіву: за container.xml, інакше перший .musicxml / .xml. */
    static byte[] extractScore(byte[] zip) throws MusicXmlException {
        Map<String, byte[]> entries = unzip(zip);
        String root = null;
        byte[] container = null;
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            if (e.getKey().toLowerCase().endsWith("meta-inf/container.xml")) {
                container = e.getValue();
                break;
            }
        }
        if (container != null) {
            try {
                Element c = Element.parse(MusicXmlReader.decode(container).replaceFirst("^\\s*<\\?xml[^>]*\\?>", ""));
                Element rf = Element.kid(Element.kid(c, "rootfiles"), "rootfile");
                if (rf != null) root = rf.attr("full-path");
            } catch (MusicXmlException ignored) {
                // битий container.xml — шукаємо партитуру за розширенням
            }
        }
        if (root != null && entries.containsKey(root)) return entries.get(root);
        for (Map.Entry<String, byte[]> e : entries.entrySet()) {
            String n = e.getKey().toLowerCase();
            if ((n.endsWith(".musicxml") || n.endsWith(".xml")) && !n.startsWith("meta-inf/")) return e.getValue();
        }
        throw new MusicXmlException("В архіві немає файлу партитури.");
    }

    private static Map<String, byte[]> unzip(byte[] zip) throws MusicXmlException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        long total = 0;
        byte[] chunk = new byte[16384];
        try (ZipInputStream in = new ZipInputStream(new ByteArrayInputStream(zip))) {
            for (ZipEntry e; (e = in.getNextEntry()) != null; ) {
                if (out.size() >= MAX_ENTRIES) throw new MusicXmlException("В архіві забагато файлів.");
                if (e.isDirectory()) continue;
                ByteArrayOutputStream buf = new ByteArrayOutputStream();
                for (int n; (n = in.read(chunk)) > 0; ) {
                    total += n;
                    if (total > MAX_UNPACKED) throw new MusicXmlException("Архів завеликий після розпакування.");
                    buf.write(chunk, 0, n);
                }
                out.put(e.getName(), buf.toByteArray());
            }
        } catch (IOException e) {
            throw new MusicXmlException("Архів пошкоджений.", e);
        }
        if (out.isEmpty()) throw new MusicXmlException("Архів пошкоджений.");
        return out;
    }
}
