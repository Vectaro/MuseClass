package ua.museclass.musicxml;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipException;
import java.util.zip.ZipInputStream;

/**
 * Витягує з MusicXML те, що потрібно серверу: назву, авторів, партії та кількість
 * тактів. Самі ноти сервер не розбирає — це робота клієнта.
 *
 * Приймає і звичайний .musicxml, і стиснений .mxl (zip з META-INF/container.xml).
 * Захищений від XXE (зовнішні сутності й DTD вимкнені) і від zip-бомб (ліміти
 * на розпакований розмір і кількість записів).
 */
public final class MusicXmlInspector {

    /** Ліміт на розпакований XML: реальні партитури — десятки-сотні кілобайт. */
    static final int MAX_XML_BYTES = 20 * 1024 * 1024;
    static final int MAX_ZIP_ENTRIES = 200;

    private static final XMLInputFactory XML = createFactory();

    private MusicXmlInspector() {}

    private static XMLInputFactory createFactory() {
        XMLInputFactory f = XMLInputFactory.newFactory();
        f.setProperty(XMLInputFactory.SUPPORT_DTD, false);
        f.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
        f.setProperty(XMLInputFactory.IS_COALESCING, true);
        return f;
    }

    public static ScoreInfo inspect(byte[] data) {
        if (data == null || data.length == 0) {
            throw new MusicXmlException("Файл порожній.");
        }
        if (isZip(data)) {
            // Беремо rootfile з container.xml; якщо його немає чи він не той —
            // перший XML в архіві, який справді є score-partwise.
            MusicXmlException first = null;
            for (byte[] xml : candidatesFromMxl(data)) {
                try {
                    return parse(xml, "mxl");
                } catch (MusicXmlException e) {
                    if (first == null) first = e;
                }
            }
            throw first != null ? first : new MusicXmlException("В архіві немає файлу партитури.");
        }
        return parse(data, "musicxml");
    }

    static boolean isZip(byte[] d) {
        return d.length >= 4 && d[0] == 'P' && d[1] == 'K' && d[2] == 3 && d[3] == 4;
    }

    // ---------------------------------------------------------------- .mxl

    private static List<byte[]> candidatesFromMxl(byte[] data) {
        Map<String, byte[]> entries = new HashMap<>();
        List<String> order = new ArrayList<>();
        try (ZipInputStream zin = new ZipInputStream(new ByteArrayInputStream(data))) {
            ZipEntry e;
            int count = 0;
            long total = 0;
            while ((e = zin.getNextEntry()) != null) {
                if (++count > MAX_ZIP_ENTRIES) {
                    throw new MusicXmlException("В архіві забагато файлів для партитури.");
                }
                if (e.isDirectory()) continue;
                String name = e.getName();
                // Цікавлять тільки XML-файли; картинки й інше пропускаємо не читаючи.
                if (!name.equalsIgnoreCase("META-INF/container.xml") && !isScoreName(name)) continue;
                byte[] body = readLimited(zin, MAX_XML_BYTES);
                total += body.length;
                if (total > MAX_XML_BYTES) {
                    throw new MusicXmlException("Архів після розпакування завеликий.");
                }
                entries.put(name, body);
                order.add(name);
            }
        } catch (ZipException ex) {
            throw new MusicXmlException("Архів .mxl пошкоджений.", ex);
        } catch (IOException ex) {
            throw new MusicXmlException("Не вдалося прочитати архів .mxl.", ex);
        }

        String rootPath = null;
        byte[] container = findIgnoreCase(entries, "META-INF/container.xml");
        if (container != null) rootPath = rootfilePath(container);

        List<byte[]> out = new ArrayList<>();
        if (rootPath != null && entries.containsKey(rootPath)) {
            out.add(entries.get(rootPath));
        }
        // .musicxml раніше за .xml: так найімовірніше влучити в партитуру
        for (String ext : List.of(".musicxml", ".xml")) {
            for (String name : order) {
                if (name.equals(rootPath) || name.regionMatches(true, 0, "META-INF/", 0, 9)) continue;
                if (name.toLowerCase().endsWith(ext)) out.add(entries.get(name));
            }
        }
        if (out.isEmpty()) throw new MusicXmlException("В архіві немає файлу партитури.");
        return out;
    }

    private static boolean isScoreName(String name) {
        String n = name.toLowerCase();
        return n.endsWith(".musicxml") || n.endsWith(".xml");
    }

    private static byte[] findIgnoreCase(Map<String, byte[]> m, String key) {
        for (Map.Entry<String, byte[]> e : m.entrySet()) {
            if (e.getKey().equalsIgnoreCase(key)) return e.getValue();
        }
        return null;
    }

    private static byte[] readLimited(InputStream in, int limit) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        byte[] buf = new byte[8192];
        int n;
        long total = 0;
        while ((n = in.read(buf)) > 0) {
            total += n;
            if (total > limit) {
                throw new MusicXmlException("Архів після розпакування завеликий.");
            }
            out.write(buf, 0, n);
        }
        return out.toByteArray();
    }

    private static String rootfilePath(byte[] container) {
        XMLStreamReader r = null;
        try {
            r = XML.createXMLStreamReader(new ByteArrayInputStream(container));
            while (r.hasNext()) {
                if (r.next() == XMLStreamConstants.START_ELEMENT && "rootfile".equals(r.getLocalName())) {
                    String p = r.getAttributeValue(null, "full-path");
                    if (p != null && !p.isBlank()) return p.trim();
                }
            }
        } catch (XMLStreamException ignored) {
            // container.xml битий — спробуємо знайти партитуру за розширенням
        } finally {
            close(r);
        }
        return null;
    }

    // ---------------------------------------------------------------- XML

    private static final class PartBuilder {
        final String id;
        String name;
        String instrumentName;
        Integer midiProgram;
        Integer midiChannel;

        PartBuilder(String id) {
            this.id = id;
        }
    }

    private static ScoreInfo parse(byte[] xml, String format) {
        try {
            return parseOnce(xml, format, false);
        } catch (MusicXmlException e) {
            // Трапляються файли з encoding='UTF-16' у заголовку, які насправді UTF-8
            // (хтось перекодував і не виправив декларацію). Даємо другий шанс.
            if (e.getCause() instanceof XMLStreamException && declaresUtf16ButIsAscii(xml)) {
                return parseOnce(xml, format, true);
            }
            throw e;
        }
    }

    private static boolean declaresUtf16ButIsAscii(byte[] xml) {
        if (xml.length < 6 || xml[0] != '<' || xml[1] != '?') return false;
        int n = Math.min(xml.length, 120);
        String head = new String(xml, 0, n, StandardCharsets.ISO_8859_1).toUpperCase();
        int end = head.indexOf("?>");
        return end > 0 && head.substring(0, end).contains("UTF-16");
    }

    private static ScoreInfo parseOnce(byte[] xml, String format, boolean forceUtf8) {
        XMLStreamReader r = null;
        String workTitle = null, movementTitle = null, composer = null, arranger = null, firstCreator = null;
        List<PartBuilder> parts = new ArrayList<>();
        Map<String, PartBuilder> byId = new HashMap<>();
        String firstPartId = null;
        int measures = 0;
        boolean sawPart = false;

        Deque<String> path = new ArrayDeque<>();
        StringBuilder text = new StringBuilder();
        String creatorType = null;
        PartBuilder currentScorePart = null;
        String currentPartId = null;

        try {
            r = forceUtf8
                    ? XML.createXMLStreamReader(new InputStreamReader(new ByteArrayInputStream(xml), StandardCharsets.UTF_8))
                    : XML.createXMLStreamReader(new ByteArrayInputStream(xml));
            boolean rootSeen = false;
            while (r.hasNext()) {
                int ev = r.next();
                switch (ev) {
                    case XMLStreamConstants.START_ELEMENT -> {
                        String name = r.getLocalName();
                        if (!rootSeen) {
                            rootSeen = true;
                            if ("score-timewise".equals(name)) {
                                throw new MusicXmlException(
                                        "Це score-timewise. Потрібен score-partwise — MuseScore експортує саме його.");
                            }
                            if (!"score-partwise".equals(name)) {
                                throw new MusicXmlException(
                                        "Кореневий елемент «" + name + "», а має бути score-partwise.");
                            }
                        }
                        text.setLength(0);
                        String parent = path.peek();
                        switch (name) {
                            case "creator" -> creatorType = r.getAttributeValue(null, "type");
                            case "score-part" -> {
                                if ("part-list".equals(parent)) {
                                    String id = attrOr(r, "id", "P" + (parts.size() + 1));
                                    currentScorePart = new PartBuilder(id);
                                    parts.add(currentScorePart);
                                    byId.putIfAbsent(id, currentScorePart);
                                }
                            }
                            case "part" -> {
                                if ("score-partwise".equals(parent)) {
                                    sawPart = true;
                                    currentPartId = r.getAttributeValue(null, "id");
                                    if (firstPartId == null) firstPartId = currentPartId == null ? "" : currentPartId;
                                }
                            }
                            case "measure" -> {
                                if ("part".equals(parent) && currentPartId != null
                                        && currentPartId.equals(firstPartId)) {
                                    measures++;
                                }
                            }
                            default -> { }
                        }
                        path.push(name);
                    }
                    case XMLStreamConstants.CHARACTERS, XMLStreamConstants.CDATA, XMLStreamConstants.SPACE ->
                            text.append(r.getText());
                    case XMLStreamConstants.END_ELEMENT -> {
                        String name = path.pop();
                        String parent = path.peek();
                        String value = clean(text.toString());
                        switch (name) {
                            case "work-title" -> {
                                if ("work".equals(parent) && workTitle == null) workTitle = value;
                            }
                            case "movement-title" -> {
                                if ("score-partwise".equals(parent) && movementTitle == null) movementTitle = value;
                            }
                            case "creator" -> {
                                if ("identification".equals(parent) && value != null) {
                                    if (firstCreator == null) firstCreator = value;
                                    if ("composer".equalsIgnoreCase(creatorType) && composer == null) composer = value;
                                    if ("arranger".equalsIgnoreCase(creatorType) && arranger == null) arranger = value;
                                }
                                creatorType = null;
                            }
                            case "part-name" -> {
                                if ("score-part".equals(parent) && currentScorePart != null
                                        && currentScorePart.name == null) {
                                    currentScorePart.name = value;
                                }
                            }
                            case "instrument-name" -> {
                                if ("score-instrument".equals(parent) && currentScorePart != null
                                        && currentScorePart.instrumentName == null) {
                                    currentScorePart.instrumentName = value;
                                }
                            }
                            case "midi-program" -> {
                                if ("midi-instrument".equals(parent) && currentScorePart != null
                                        && currentScorePart.midiProgram == null) {
                                    currentScorePart.midiProgram = parseIntOrNull(value);
                                }
                            }
                            case "midi-channel" -> {
                                if ("midi-instrument".equals(parent) && currentScorePart != null
                                        && currentScorePart.midiChannel == null) {
                                    currentScorePart.midiChannel = parseIntOrNull(value);
                                }
                            }
                            case "score-part" -> currentScorePart = null;
                            case "part" -> {
                                if ("score-partwise".equals(parent)) currentPartId = null;
                            }
                            default -> { }
                        }
                        text.setLength(0);
                    }
                    default -> { }
                }
            }
            if (!rootSeen) throw new MusicXmlException("Це не схоже на MusicXML.");
        } catch (XMLStreamException ex) {
            throw new MusicXmlException("Файл не читається як XML. Перевір, що це MusicXML.", ex);
        } finally {
            close(r);
        }

        if (parts.isEmpty() || !sawPart) {
            throw new MusicXmlException("У файлі немає жодної партії.");
        }

        List<ScoreInfo.PartInfo> out = new ArrayList<>(parts.size());
        for (PartBuilder p : parts) {
            String name = p.name != null ? p.name : (p.instrumentName != null ? p.instrumentName : p.id);
            out.add(new ScoreInfo.PartInfo(p.id, truncate(name, 120), truncate(p.instrumentName, 120),
                    p.midiProgram, p.midiChannel,
                    InstrumentDetector.detect(p.name, p.instrumentName, p.midiProgram, p.midiChannel)));
        }
        String title = workTitle != null ? workTitle : movementTitle;
        if (composer == null && arranger == null) composer = firstCreator;
        return new ScoreInfo(format, truncate(title, 200), truncate(composer, 200), truncate(arranger, 200),
                List.copyOf(out), measures);
    }

    private static String attrOr(XMLStreamReader r, String attr, String def) {
        String v = r.getAttributeValue(null, attr);
        return v == null || v.isBlank() ? def : v;
    }

    private static Integer parseIntOrNull(String s) {
        if (s == null) return null;
        try {
            return (int) Math.round(Double.parseDouble(s));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Схлопує пробіли й переноси (у part-name їх часто вставляють для двох рядків). */
    private static String clean(String s) {
        String t = s.replaceAll("\\s+", " ").trim();
        return t.isEmpty() ? null : t;
    }

    private static String truncate(String s, int max) {
        if (s == null) return null;
        return s.length() <= max ? s : s.substring(0, max);
    }

    private static void close(XMLStreamReader r) {
        if (r == null) return;
        try {
            r.close();
        } catch (XMLStreamException ignored) {
        }
    }
}
