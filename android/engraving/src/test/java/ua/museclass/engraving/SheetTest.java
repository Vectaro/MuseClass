package ua.museclass.engraving;

import org.junit.Test;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import ua.museclass.musicxml.MusicXmlReader;
import ua.museclass.musicxml.Part;
import ua.museclass.musicxml.Score;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** Перенос за шириною: усе влазить, нічого не губиться, рядки заповнені. */
public class SheetTest {
    private static final File INPUT = new File("../musicxml/src/test/resources/golden");

    private static Score demo(String id) throws Exception {
        return MusicXmlReader.read(Files.readAllBytes(new File(INPUT, "demo-" + id + ".musicxml").toPath()));
    }

    private static long count(List<Shape> shapes, Class<?> type) {
        return shapes.stream().filter(type::isInstance).count();
    }

    @Test
    public void everyDemoPartFitsAndCoversAllMeasures() throws Exception {
        File[] files = INPUT.listFiles((d, n) -> n.startsWith("demo-") && n.endsWith(".musicxml"));
        assertTrue(files != null && files.length > 0);
        for (double width : new double[]{360, 460, 900}) {
            for (File f : files) {
                Score s = MusicXmlReader.read(Files.readAllBytes(f.toPath()));
                for (Part p : s.parts()) {
                    List<Sheet.System> sys = Sheet.layout(p, width, new Sheet.Options());
                    String where = f.getName() + " / " + p.name() + " @" + width;
                    int next = 0;
                    for (int i = 0; i < sys.size(); i++) {
                        Sheet.System y = sys.get(i);
                        assertEquals(where + ": такти без пропусків", next, y.from);
                        assertTrue(where + ": порожня система", y.to > y.from);
                        next = y.to;
                        boolean last = i == sys.size() - 1;
                        if (y.to - y.from > 1 || !last) {
                            // кілька тактів не вилазять; повний рядок — рівно на ширину
                            assertTrue(where + ": система " + i + " ширша за екран: " + y.width,
                                    y.width <= width + 1e-6 || y.to - y.from == 1);
                        }
                        if (!last) assertEquals(where + ": рядок " + i + " не розтягнуто", width, y.width, 1e-6);
                        for (Shape sh : y.shapes) {
                            double[] b = {Double.MAX_VALUE, Double.MAX_VALUE, -Double.MAX_VALUE, -Double.MAX_VALUE};
                            sh.bounds(b);
                            assertTrue(where + ": фігура над системою " + sh.canon(), b[1] >= -1e-6);
                            assertTrue(where + ": фігура під системою " + sh.canon(), b[3] <= y.height + 1e-6);
                        }
                    }
                    assertEquals(where + ": усі такти", p.measures().size(), next);
                }
            }
        }
    }

    @Test
    public void onlyFirstSystemHasTimeSignature() throws Exception {
        Part p = demo("s1").parts().get(0);
        List<Sheet.System> sys = Sheet.layout(p, 400, new Sheet.Options());
        assertTrue("треба кілька систем", sys.size() > 1);
        assertEquals(2, count(sys.get(0).shapes, Shape.Text.class) - sys.get(0).to);
        assertEquals(0, count(sys.get(1).shapes, Shape.Text.class) - (sys.get(1).to - sys.get(1).from));
    }

    private static final String KEY_CHANGE = "<?xml version=\"1.0\"?><score-partwise version=\"4.0\">"
            + "<part-list><score-part id=\"P1\"><part-name>Тест</part-name></score-part></part-list>"
            + "<part id=\"P1\">"
            + "<measure number=\"1\"><attributes><divisions>1</divisions><key><fifths>0</fifths></key>"
            + "<time><beats>4</beats><beat-type>4</beat-type></time><clef><sign>G</sign><line>2</line></clef></attributes>"
            + "<note><pitch><step>F</step><octave>4</octave></pitch><duration>4</duration><type>whole</type></note></measure>"
            + "<measure number=\"2\"><attributes><key><fifths>1</fifths></key></attributes>"
            + "<note><pitch><step>F</step><alter>1</alter><octave>4</octave></pitch><duration>2</duration><type>half</type></note>"
            + "<note><pitch><step>F</step><octave>4</octave></pitch><duration>2</duration><type>half</type></note></measure>"
            + "<measure number=\"3\"><note><pitch><step>F</step><alter>1</alter><octave>4</octave></pitch>"
            + "<duration>4</duration><type>whole</type></note></measure>"
            + "</part></score-partwise>";

    @Test
    public void accidentalsFollowKeyInForce() throws Exception {
        Part p = MusicXmlReader.read(KEY_CHANGE.getBytes(StandardCharsets.UTF_8)).parts().get(0);
        // вузько: кожен такт — окрема система
        List<Sheet.System> sys = Sheet.layout(p, 150, new Sheet.Options());
        assertEquals(3, sys.size());
        // такт 2: зміна на соль мажор (один ♯ у самому такті); F♯ — без знака, F — з бекаром
        List<Shape> m2 = sys.get(1).shapes;
        long sharps = m2.stream().filter(s -> s instanceof Shape.Accidental && ((Shape.Accidental) s).alter == 1).count();
        long naturals = m2.stream().filter(s -> s instanceof Shape.Accidental && ((Shape.Accidental) s).alter == 0).count();
        assertEquals("ключовий ♯ на початку системи, без знака біля F♯", 1, sharps);
        assertEquals("бекар біля F", 1, naturals);
        // такт 3: нова система починається з ♯ у ключі, F♯ знова без знака
        List<Shape> m3 = sys.get(2).shapes;
        assertEquals(1, m3.stream().filter(s -> s instanceof Shape.Accidental).count());

        // широко: усе в одній системі, зміна ключа посеред — знаки від нового ключа (у прототипі — від старого)
        List<Sheet.System> one = Sheet.layout(p, 900, new Sheet.Options());
        assertEquals(1, one.size());
        List<Shape> all = one.get(0).shapes;
        assertEquals("лише ключовий ♯ у такті 2", 1,
                all.stream().filter(s -> s instanceof Shape.Accidental && ((Shape.Accidental) s).alter == 1).count());
        assertEquals("бекар біля F у такті 2", 1,
                all.stream().filter(s -> s instanceof Shape.Accidental && ((Shape.Accidental) s).alter == 0).count());
    }
}
