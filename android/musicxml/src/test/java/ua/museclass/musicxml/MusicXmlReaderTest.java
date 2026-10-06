package ua.museclass.musicxml;

import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class MusicXmlReaderTest {

    @Test
    public void sampleFromMuseScore() throws Exception {
        Score s = GoldenTest.readGolden("fixture-sample-musicxml.musicxml");
        assertEquals("Тест & кавер", s.title());
        assertEquals("В. Довгий", s.composer());
        assertEquals("аранжування для класу", s.arranger());
        // фортепіано з двома станами — дві самостійні партії, плюс труба
        assertEquals(3, s.parts().size());
        assertEquals("Piano (права рука)", s.parts().get(0).name());
        assertEquals("treble", s.parts().get(0).clef());
        assertEquals("Piano (ліва рука)", s.parts().get(1).name());
        assertEquals("bass", s.parts().get(1).clef());
        assertEquals(-2, s.parts().get(0).fifths());

        Part trumpet = s.parts().get(2);
        assertEquals("Труба in B♭", trumpet.instrument());
        assertEquals(2, trumpet.transpose());

        // перший акорд правої руки: сі-бемоль, а не ля-дієз
        Pitch bFlat = s.parts().get(0).measures().get(0).notes().get(0).pitches().get(0);
        assertEquals(6, bFlat.step);
        assertEquals(-1, bFlat.alter);
        assertEquals(4, bFlat.octave);
        assertEquals(Integer.valueOf(70), bFlat.midi());
        assertEquals(2, s.measureCount());
    }

    @Test
    public void mxlGivesSameScoreAsPlainFile() throws Exception {
        Score plain = GoldenTest.readGolden("fixture-sample-musicxml.musicxml");
        Score packed = GoldenTest.readGolden("fixture-sample-mxl.mxl");
        assertEquals(GoldenTest.toPrototypeShape(plain), GoldenTest.toPrototypeShape(packed));
    }

    @Test
    public void percussionIsUnpitched() throws Exception {
        Score s = MusicXmlReader.parse(xml(
                "<attributes><divisions>1</divisions><clef><sign>percussion</sign></clef></attributes>"
                        + "<note><unpitched><display-step>C</display-step><display-octave>5</display-octave></unpitched>"
                        + "<duration>4</duration></note>"));
        Part p = s.parts().get(0);
        assertEquals("perc", p.clef());
        Pitch x = p.measures().get(0).notes().get(0).pitches().get(0);
        assertTrue(x.unpitched);
        assertNull(x.midi());
        assertEquals(0, x.step);
        assertEquals(5, x.octave);
        assertEquals(4.0, p.measures().get(0).notes().get(0).duration(), 0);
    }

    @Test
    public void emptyMeasureKeepsNumbering() throws Exception {
        Score s = MusicXmlReader.parse(xml("<note><rest/><duration>4</duration></note>",
                "", "<note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration></note>"));
        Part p = s.parts().get(0);
        assertEquals(3, p.measures().size());
        assertTrue(p.measures().get(1).notes().isEmpty());
        assertFalse(p.measures().get(2).notes().get(0).isRest());
    }

    @Test
    public void lyingEncodingInHeaderIsIgnored() throws Exception {
        String src = xml("<note><pitch><step>C</step><octave>4</octave></pitch><duration>4</duration>"
                + "<lyric><text>Щед</text></lyric></note>").replace("UTF-8", "UTF-16");
        Score s = MusicXmlReader.read(src.getBytes(StandardCharsets.UTF_8));
        assertEquals("Щед", s.parts().get(0).measures().get(0).notes().get(0).lyric());
    }

    @Test
    public void errorsAreReadable() {
        assertError("<score-timewise/>", "score-timewise");
        assertError("<html/>", "Кореневий елемент «html»");
        assertError("<score-partwise/>", "немає жодної партії");
        assertError("просто текст", "не схоже на XML");
    }

    @Test
    public void zipWithoutScore() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        try (ZipOutputStream z = new ZipOutputStream(buf)) {
            z.putNextEntry(new ZipEntry("readme.txt"));
            z.write("нічого".getBytes(StandardCharsets.UTF_8));
            z.closeEntry();
        }
        try {
            MusicXmlReader.read(buf.toByteArray());
            fail();
        } catch (MusicXmlException e) {
            assertEquals("В архіві немає файлу партитури.", e.getMessage());
        }
    }

    @Test
    public void zipBombIsStopped() throws Exception {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] zeros = new byte[1024 * 1024];
        try (ZipOutputStream z = new ZipOutputStream(buf)) {
            z.putNextEntry(new ZipEntry("score.musicxml"));
            for (int i = 0; i < 70; i++) z.write(zeros);
            z.closeEntry();
        }
        assertTrue("стиснений має бути малим", buf.size() < 1024 * 1024);
        try {
            MusicXmlReader.read(buf.toByteArray());
            fail();
        } catch (MusicXmlException e) {
            assertTrue(e.getMessage(), e.getMessage().contains("завеликий"));
        }
    }

    @Test
    public void snapDurMatchesPrototype() {
        assertEquals(1.0 / 3, MusicXmlReader.snapDur(1.0 / 3), 1e-12);
        assertEquals(1.5, MusicXmlReader.snapDur(1.499), 1e-12);
        assertEquals(0.25, MusicXmlReader.snapDur(0), 1e-12);
    }

    private static void assertError(String src, String part) {
        try {
            MusicXmlReader.parse(src);
            fail("мало впасти: " + src);
        } catch (MusicXmlException e) {
            assertTrue(e.getMessage(), e.getMessage().contains(part));
        }
    }

    /** Партитура з однією партією; кожен аргумент — вміст такту. */
    private static String xml(String... measures) {
        StringBuilder sb = new StringBuilder("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<score-partwise version=\"4.0\">"
                + "<part-list><score-part id=\"P1\"><part-name>Тест</part-name></score-part></part-list><part id=\"P1\">");
        for (int i = 0; i < measures.length; i++) {
            sb.append("<measure number=\"").append(i + 1).append("\">").append(measures[i]).append("</measure>");
        }
        return sb.append("</part></score-partwise>").toString();
    }
}
