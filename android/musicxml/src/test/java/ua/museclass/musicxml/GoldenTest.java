package ua.museclass.musicxml;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;

import org.junit.Test;

import java.io.File;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Звірка з прототипом. Для кожного golden/X.json розбираємо вхід X.musicxml
 * або X.mxl своїм парсером, переводимо модель у форму, яку дає fromMusicXML у
 * прототипі, і порівнюємо поле в поле. Еталони пише android/tools/golden.js.
 */
public class GoldenTest {

    @Test
    public void everyGoldenMatchesPrototype() throws Exception {
        URL dirUrl = getClass().getClassLoader().getResource("golden");
        File dir = new File(dirUrl.toURI());
        File[] jsons = dir.listFiles((d, n) -> n.endsWith(".json"));
        assertTrue("еталонів немає", jsons != null && jsons.length >= 10);
        Arrays.sort(jsons);
        List<String> failures = new ArrayList<>();
        for (File json : jsons) {
            String base = json.getName().replace(".json", "");
            File input = new File(dir, base + ".musicxml");
            if (!input.exists()) input = new File(dir, base + ".mxl");
            Score score = MusicXmlReader.read(Files.readAllBytes(input.toPath()));
            JsonElement expected;
            try (Reader r = new InputStreamReader(Files.newInputStream(json.toPath()), StandardCharsets.UTF_8)) {
                expected = JsonParser.parseReader(r);
            }
            List<String> diffs = new ArrayList<>();
            compare(base, expected, toPrototypeShape(score), diffs);
            if (!diffs.isEmpty()) failures.add(String.join("\n", diffs.subList(0, Math.min(10, diffs.size()))));
        }
        if (!failures.isEmpty()) fail(String.join("\n\n", failures));
    }

    /** Модель → JSON у формі прототипу: {title, composer, arranger, parts:[{name, inst, clef, meter, …}]}. */
    static JsonObject toPrototypeShape(Score s) {
        JsonObject o = new JsonObject();
        o.addProperty("title", s.title());
        o.addProperty("composer", s.composer());
        o.addProperty("arranger", s.arranger());
        JsonArray parts = new JsonArray();
        for (Part p : s.parts()) {
            JsonObject jp = new JsonObject();
            jp.addProperty("name", p.name());
            jp.addProperty("inst", p.instrument());
            jp.addProperty("clef", p.clef());
            // відома розбіжність: прототип пише партії ОСТАННІЙ розмір і тональність,
            // ми — початкові, а зміни лишаються в тактах
            Meter meter = p.meter();
            int fifths = p.fifths();
            for (Measure m : p.measures()) {
                if (m.bar() != null && m.bar().meter() != null) meter = m.bar().meter();
                if (m.bar() != null && m.bar().fifths() != null) fifths = m.bar().fifths();
            }
            jp.add("meter", ints(meter.beats, meter.beatType));
            jp.addProperty("fifths", fifths);
            jp.addProperty("ts", p.transpose());
            JsonObject bars = new JsonObject();
            JsonObject v2 = new JsonObject();
            JsonArray ms = new JsonArray();
            for (Measure m : p.measures()) {
                if (m.bar() != null) bars.add(String.valueOf(m.index()), bar(m.bar()));
                if (!m.voice2().isEmpty()) v2.add(String.valueOf(m.index()), notes(m.voice2()));
                if (!m.notes().isEmpty()) ms.add(notes(m.notes()));
            }
            // прототип пише null, коли позначок чи другого голосу немає
            jp.add("bars", bars.size() > 0 ? bars : JsonNull.INSTANCE);
            jp.add("v2", v2.size() > 0 ? v2 : JsonNull.INSTANCE);
            jp.add("ms", ms);
            parts.add(jp);
        }
        o.add("parts", parts);
        return o;
    }

    private static JsonObject bar(Bar b) {
        JsonObject o = new JsonObject();
        if (b.meter() != null) o.add("meter", ints(b.meter().beats, b.meter().beatType));
        if (b.fifths() != null) o.addProperty("fifths", b.fifths());
        if (b.repeat() != null) o.addProperty("rep", b.repeat());
        if (!b.ending().isEmpty()) {
            JsonArray e = new JsonArray();
            for (int n : b.ending()) e.add(n);
            o.add("ending", e);
        }
        if (b.endingStop() != null) o.addProperty("endingStop", b.endingStop());
        if (b.jump() != null) o.addProperty("jump", b.jump());
        if (b.text() != null) o.addProperty("text", b.text());
        if (b.mark() != null) o.addProperty("mark", b.mark());
        if (b.dynamics() != null) o.addProperty("dyn", b.dynamics());
        if (b.hairpin() != null) o.addProperty("hair", b.hairpin());
        if (b.hairpinKind() != null) o.addProperty("hairKind", b.hairpinKind());
        return o;
    }

    private static JsonArray notes(List<Note> list) {
        JsonArray a = new JsonArray();
        for (Note n : list) a.add(note(n));
        return a;
    }

    private static JsonObject note(Note n) {
        JsonObject o = new JsonObject();
        JsonArray ps = new JsonArray();
        for (Pitch p : n.pitches()) {
            JsonObject jp = new JsonObject();
            jp.add("m", p.midi() == null ? JsonNull.INSTANCE : new JsonPrimitive(p.midi()));
            if (p.unpitched) jp.addProperty("u", 1);
            jp.addProperty("s", p.step);
            jp.addProperty("a", p.alter);
            jp.addProperty("o", p.octave);
            ps.add(jp);
        }
        o.add("p", ps);
        o.addProperty("d", n.duration());
        if (n.tie() != null) o.addProperty("tie", n.tie());
        if (n.slur() != null) o.addProperty("slur", n.slur());
        if (!n.articulations().isEmpty()) {
            JsonArray art = new JsonArray();
            for (String s : n.articulations()) art.add(s);
            o.add("art", art);
        }
        if (!n.graces().isEmpty()) o.add("gr", notes(n.graces()));
        if (n.lyric() != null) o.addProperty("lyric", n.lyric());
        if (n.tuplet() != null) {
            JsonObject t = new JsonObject();
            t.addProperty("n", n.tuplet().actual);
            t.addProperty("of", n.tuplet().normal);
            t.addProperty("pos", n.tuplet().pos);
            o.add("tup", t);
        }
        return o;
    }

    private static JsonArray ints(int... v) {
        JsonArray a = new JsonArray();
        for (int i : v) a.add(i);
        return a;
    }

    /** Поле в поле, числа з допуском; розбіжності — шляхом до місця. */
    static void compare(String path, JsonElement exp, JsonElement act, List<String> diffs) {
        if (exp.isJsonObject() && act.isJsonObject()) {
            JsonObject e = exp.getAsJsonObject(), a = act.getAsJsonObject();
            Set<String> keys = new TreeSet<>(e.keySet());
            keys.addAll(a.keySet());
            for (String k : keys) {
                if (!e.has(k)) diffs.add(path + "." + k + ": зайве в Java = " + a.get(k));
                else if (!a.has(k)) diffs.add(path + "." + k + ": бракує в Java, прототип = " + e.get(k));
                else compare(path + "." + k, e.get(k), a.get(k), diffs);
            }
        } else if (exp.isJsonArray() && act.isJsonArray()) {
            JsonArray e = exp.getAsJsonArray(), a = act.getAsJsonArray();
            if (e.size() != a.size()) {
                diffs.add(path + ": довжина " + a.size() + " замість " + e.size());
                return;
            }
            for (int i = 0; i < e.size(); i++) compare(path + "[" + i + "]", e.get(i), a.get(i), diffs);
        } else if (exp.isJsonPrimitive() && act.isJsonPrimitive()
                && exp.getAsJsonPrimitive().isNumber() && act.getAsJsonPrimitive().isNumber()) {
            if (Math.abs(exp.getAsDouble() - act.getAsDouble()) > 1e-9) {
                diffs.add(path + ": " + act + " замість " + exp);
            }
        } else if (!exp.equals(act)) {
            diffs.add(path + ": " + act + " замість " + exp);
        }
    }

    @Test
    public void meterChangeKeepsInitialMeterOnPart() throws Exception {
        Score s = readGolden("demo-s13.musicxml");
        Part p = s.parts().get(0);
        // 3/4 і один дієз з 10-го такту
        assertEquals(new Meter(4, 4), p.meter());
        assertEquals(0, p.fifths());
        assertEquals(new Meter(3, 4), p.measures().get(9).bar().meter());
        assertEquals(Integer.valueOf(1), p.measures().get(9).bar().fifths());
        // кожен такт рівно своєї довжини, з урахуванням зміни розміру
        Meter cur = p.meter();
        for (Measure m : p.measures()) {
            if (m.bar() != null && m.bar().meter() != null) cur = m.bar().meter();
            assertEquals("такт " + (m.index() + 1), cur.quarters(), m.length(), 1e-6);
        }
    }

    static Score readGolden(String name) throws Exception {
        URL url = GoldenTest.class.getClassLoader().getResource("golden/" + name);
        return MusicXmlReader.read(Files.readAllBytes(new File(url.toURI()).toPath()));
    }
}
