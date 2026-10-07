package ua.museclass.engraving;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

import ua.museclass.musicxml.MusicXmlReader;
import ua.museclass.musicxml.Part;
import ua.museclass.musicxml.Score;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Верстка проти прототипу: ті самі демо, ті самі розбивки на системи —
 * фігура в фігуру, з допуском 0,05. Еталони пише android/tools/golden-render.js.
 */
public class GoldenRenderTest {
    private static final File GOLDEN = new File("src/test/resources/golden");
    private static final File INPUT = new File("../musicxml/src/test/resources/golden");

    @Test
    public void matchesPrototype() throws Exception {
        File[] files = GOLDEN.listFiles((d, n) -> n.endsWith(".json"));
        assertTrue("немає еталонів у " + GOLDEN.getAbsolutePath(), files != null && files.length > 0);
        List<String> failures = new ArrayList<>();
        int systems = 0;
        for (File f : files) {
            String id = f.getName().replace(".json", "");
            JsonObject g = JsonParser.parseString(read(f)).getAsJsonObject();
            Score score = MusicXmlReader.read(Files.readAllBytes(new File(INPUT, id + ".musicxml").toPath()));
            JsonArray parts = g.getAsJsonArray("parts");
            assertEquals(id + ": кількість партій", parts.size(), score.parts().size());
            for (int pi = 0; pi < parts.size(); pi++) {
                JsonObject gp = parts.get(pi).getAsJsonObject();
                Part p = score.parts().get(pi);
                List<Staff.Item> items = Staff.items(p.measures());
                assertEquals(id + "/" + pi + ": тактів", gp.get("measures").getAsInt(), items.size());
                for (JsonElement se : gp.getAsJsonArray("systems")) {
                    JsonObject gs = se.getAsJsonObject();
                    Staff.Options o = new Staff.Options();
                    o.clef = p.clef();
                    o.fifths = p.fifths();
                    o.meter = p.meter();
                    o.top = g.get("top").getAsDouble();
                    o.timesig = gs.get("timesig").getAsBoolean();
                    o.end = gs.get("end").getAsBoolean();
                    int from = gs.get("from").getAsInt(), to = gs.get("to").getAsInt();
                    Staff.Result r = Staff.render(items.subList(from, to), o);
                    String where = id + " партія " + pi + " («" + p.name() + "») такти " + (from + 1) + "–" + to;
                    String diff = compare(gs.getAsJsonArray("shapes"), r.shapes);
                    if (diff != null) failures.add(where + ": " + diff);
                    systems++;
                }
            }
        }
        if (!failures.isEmpty()) fail(failures.size() + " систем(и) з " + systems + " розходяться:\n"
                + String.join("\n", failures));
    }

    /** null — збіглося; інакше перше розходження. */
    static String compare(JsonArray want, List<Shape> got) {
        int n = Math.min(want.size(), got.size());
        for (int i = 0; i < n; i++) {
            String w = want.get(i).getAsString(), h = got.get(i).canon();
            if (!same(w, h)) return "фігура #" + i + ": чекали «" + w + "», маємо «" + h + "»";
        }
        if (want.size() != got.size()) {
            return "фігур " + got.size() + " замість " + want.size()
                    + (got.size() > n ? ", зайва: «" + got.get(n).canon() + "»" : ", бракує: «" + want.get(n).getAsString() + "»");
        }
        return null;
    }

    private static boolean same(String a, String b) {
        String[] x = a.split(" "), y = b.split(" ");
        if (x.length != y.length) return false;
        for (int i = 0; i < x.length; i++) {
            if (x[i].equals(y[i])) continue;
            try {
                if (Math.abs(Double.parseDouble(x[i]) - Double.parseDouble(y[i])) > 0.05) return false;
            } catch (NumberFormatException e) {
                return false;
            }
        }
        return true;
    }

    private static String read(File f) throws IOException {
        return new String(Files.readAllBytes(f.toPath()), StandardCharsets.UTF_8);
    }
}
