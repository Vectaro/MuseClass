package ua.museclass.musicxml;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MusicXmlInspectorTest {

    static final String DOCTYPE = "<!DOCTYPE score-partwise PUBLIC \"-//Recordare//DTD MusicXML 4.0 Partwise//EN\" "
            + "\"http://www.musicxml.org/dtds/partwise.dtd\">\n";

    static String score(String partList, String parts) {
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n" + DOCTYPE
                + "<score-partwise version=\"4.0\">\n"
                + "  <work><work-title>Щедрик</work-title></work>\n"
                + "  <identification>\n"
                + "    <creator type=\"composer\">М. Леонтович</creator>\n"
                + "    <creator type=\"arranger\">О. Кравець</creator>\n"
                + "  </identification>\n"
                + "  <part-list>" + partList + "</part-list>\n"
                + parts
                + "</score-partwise>\n";
    }

    static final String TWO_PARTS = score(
            "<score-part id=\"P1\"><part-name>Фортепіано</part-name>"
                    + "<score-instrument id=\"P1-I1\"><instrument-name>Piano</instrument-name></score-instrument>"
                    + "<midi-instrument id=\"P1-I1\"><midi-channel>1</midi-channel><midi-program>1</midi-program></midi-instrument>"
                    + "</score-part>"
                    + "<score-part id=\"P2\"><part-name>Труба\nin B♭</part-name>"
                    + "<midi-instrument id=\"P2-I1\"><midi-program>1</midi-program></midi-instrument></score-part>",
            "<part id=\"P1\"><measure number=\"1\"><note><rest/><duration>4</duration></note></measure>"
                    + "<measure number=\"2\"><note><rest/><duration>4</duration></note></measure>"
                    + "<measure number=\"3\"><sound><midi-instrument id=\"P1-I1\"><midi-program>41</midi-program>"
                    + "</midi-instrument></sound></measure></part>"
                    + "<part id=\"P2\"><measure number=\"1\"/><measure number=\"2\"/><measure number=\"3\"/>"
                    + "<measure number=\"4\"/></part>");

    @Test
    void readsMetadataAndParts() {
        ScoreInfo info = MusicXmlInspector.inspect(TWO_PARTS.getBytes(StandardCharsets.UTF_8));
        assertEquals("musicxml", info.format());
        assertEquals("Щедрик", info.title());
        assertEquals("М. Леонтович", info.composer());
        assertEquals("О. Кравець", info.arranger());
        assertEquals(3, info.measures(), "такти рахуються по першій партії");
        assertEquals(2, info.parts().size());

        ScoreInfo.PartInfo p1 = info.parts().get(0);
        assertEquals("P1", p1.id());
        assertEquals("Фортепіано", p1.name());
        assertEquals("Piano", p1.instrumentName());
        assertEquals(1, p1.midiProgram(), "midi-program усередині такту не перезаписує партію");
        assertEquals("piano", p1.instrument());

        ScoreInfo.PartInfo p2 = info.parts().get(1);
        assertEquals("Труба in B♭", p2.name(), "перенос рядка в part-name схлопується");
        assertEquals("trumpet", p2.instrument(), "назва важить більше за фортепіанну MIDI-програму");
    }

    @Test
    void readsCompressedMxlViaContainer() throws IOException {
        byte[] mxl = mxl("scores/shchedryk.musicxml", TWO_PARTS, true, true);
        ScoreInfo info = MusicXmlInspector.inspect(mxl);
        assertEquals("mxl", info.format());
        assertEquals("Щедрик", info.title());
        assertEquals(2, info.parts().size());
    }

    @Test
    void readsMxlWithoutContainer() throws IOException {
        byte[] mxl = mxl("score.xml", TWO_PARTS, false, true);
        assertEquals("Щедрик", MusicXmlInspector.inspect(mxl).title());
    }

    @Test
    void readsUtf16WithBom() {
        String s = TWO_PARTS.replace("encoding=\"UTF-8\"", "encoding=\"UTF-16\"");
        byte[] body = s.getBytes(StandardCharsets.UTF_16); // Java пише BOM
        assertEquals("Щедрик", MusicXmlInspector.inspect(body).title());
    }

    @Test
    void toleratesUtf16DeclarationOnUtf8Bytes() {
        // Реальний випадок з експорту Sibelius, який потім перекодували в UTF-8
        String s = TWO_PARTS.replace("encoding=\"UTF-8\"", "encoding='UTF-16' standalone='no'");
        assertEquals("Щедрик", MusicXmlInspector.inspect(s.getBytes(StandardCharsets.UTF_8)).title());
    }

    @Test
    void movementTitleIsFallback() {
        String s = TWO_PARTS.replace("<work><work-title>Щедрик</work-title></work>",
                "<movement-title>Ода до радості</movement-title>");
        assertEquals("Ода до радості", MusicXmlInspector.inspect(s.getBytes(StandardCharsets.UTF_8)).title());
    }

    @Test
    void untypedCreatorBecomesComposer() {
        String s = TWO_PARTS
                .replace("<creator type=\"composer\">М. Леонтович</creator>", "<creator>народна</creator>")
                .replace("<creator type=\"arranger\">О. Кравець</creator>", "");
        ScoreInfo info = MusicXmlInspector.inspect(s.getBytes(StandardCharsets.UTF_8));
        assertEquals("народна", info.composer());
        assertNull(info.arranger());
    }

    @Test
    void noTitleIsNull() {
        String s = TWO_PARTS.replace("<work><work-title>Щедрик</work-title></work>", "");
        assertNull(MusicXmlInspector.inspect(s.getBytes(StandardCharsets.UTF_8)).title());
    }

    @Test
    void rejectsTimewise() {
        String s = "<?xml version=\"1.0\"?><score-timewise><part-list/></score-timewise>";
        MusicXmlException e = assertThrows(MusicXmlException.class,
                () -> MusicXmlInspector.inspect(s.getBytes(StandardCharsets.UTF_8)));
        assertTrue(e.getMessage().contains("score-timewise"));
    }

    @Test
    void rejectsOtherXml() {
        assertThrows(MusicXmlException.class,
                () -> MusicXmlInspector.inspect("<html><body/></html>".getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void rejectsGarbageAndEmpty() {
        assertThrows(MusicXmlException.class,
                () -> MusicXmlInspector.inspect("це не ноти".getBytes(StandardCharsets.UTF_8)));
        assertThrows(MusicXmlException.class, () -> MusicXmlInspector.inspect(new byte[0]));
        assertThrows(MusicXmlException.class, () -> MusicXmlInspector.inspect(null));
    }

    @Test
    void rejectsScoreWithoutParts() {
        String s = score("", "");
        assertThrows(MusicXmlException.class, () -> MusicXmlInspector.inspect(s.getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void doesNotResolveExternalEntities() {
        String s = "<?xml version=\"1.0\"?>\n<!DOCTYPE score-partwise [<!ENTITY xxe SYSTEM \"file:///etc/hostname\">]>\n"
                + "<score-partwise><work><work-title>&xxe;</work-title></work>"
                + "<part-list><score-part id=\"P1\"><part-name>P</part-name></score-part></part-list>"
                + "<part id=\"P1\"><measure/></part></score-partwise>";
        try {
            ScoreInfo info = MusicXmlInspector.inspect(s.getBytes(StandardCharsets.UTF_8));
            // якщо парсер пропустив сутність, у назві не має бути вмісту файлу
            assertTrue(info.title() == null || info.title().isEmpty() || !info.title().contains("\n"));
        } catch (MusicXmlException expected) {
            // відмова — теж правильна поведінка
        }
    }

    @Test
    void rejectsZipBomb() throws IOException {
        // 30 МБ нулів стискаються до кількох десятків кілобайт
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            z.putNextEntry(new ZipEntry("score.musicxml"));
            byte[] zeros = new byte[1024 * 1024];
            for (int i = 0; i < 30; i++) z.write(zeros);
            z.closeEntry();
        }
        assertTrue(bos.size() < 200_000);
        MusicXmlException e = assertThrows(MusicXmlException.class, () -> MusicXmlInspector.inspect(bos.toByteArray()));
        assertTrue(e.getMessage().contains("завеликий"));
    }

    @Test
    void rejectsBrokenZip() {
        byte[] b = new byte[]{'P', 'K', 3, 4, 1, 2, 3, 4, 5, 6, 7};
        assertThrows(MusicXmlException.class, () -> MusicXmlInspector.inspect(b));
    }

    @Test
    void rejectsMxlWithoutScore() throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            z.putNextEntry(new ZipEntry("cover.png"));
            z.write(new byte[]{1, 2, 3});
            z.closeEntry();
        }
        MusicXmlException e = assertThrows(MusicXmlException.class, () -> MusicXmlInspector.inspect(bos.toByteArray()));
        assertFalse(e.getMessage().isBlank());
    }

    static byte[] mxl(String innerName, String xml, boolean withContainer, boolean deflate) throws IOException {
        ByteArrayOutputStream bos = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(bos)) {
            if (!deflate) z.setLevel(0);
            if (withContainer) {
                z.putNextEntry(new ZipEntry("META-INF/container.xml"));
                z.write(("<?xml version=\"1.0\" encoding=\"UTF-8\"?><container><rootfiles>"
                        + "<rootfile full-path=\"" + innerName + "\" media-type=\"application/vnd.recordare.musicxml+xml\"/>"
                        + "</rootfiles></container>").getBytes(StandardCharsets.UTF_8));
                z.closeEntry();
            }
            // підкидаємо ще один xml, щоб перевірити, що береться саме rootfile
            z.putNextEntry(new ZipEntry("aaa-other.xml"));
            z.write("<other/>".getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
            z.putNextEntry(new ZipEntry(innerName));
            z.write(xml.getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        return bos.toByteArray();
    }
}
