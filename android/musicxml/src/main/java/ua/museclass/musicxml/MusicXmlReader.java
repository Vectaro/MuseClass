package ua.museclass.musicxml;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.regex.Pattern;

import static ua.museclass.musicxml.Element.kid;
import static ua.museclass.musicxml.Element.kids;
import static ua.museclass.musicxml.Element.num;
import static ua.museclass.musicxml.Element.txt;

/**
 * Розбір MusicXML (score-partwise) у модель. Порт fromMusicXML з прототипу
 * (prototype/index.html): ті самі правила, а еталонні тести звіряють результат
 * з тим, що розбирає прототип.
 *
 * Свідомі відмінності від прототипу:
 * - розмір, тональність, ключ і стрій партії — початкові, а не останні в файлі;
 *   зміни посеред партії лежать у {@link Bar};
 * - такт без нот лишається порожнім тактом, а не випадає — номери тактів
 *   збігаються в усіх партіях;
 * - альтерація ціла (чвертьтони округлюються).
 */
public final class MusicXmlReader {
    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final String STEP_LETTERS = "CDEFGAB";
    private static final Pattern XML_DECL = Pattern.compile("^\\s*<\\?xml[^>]*\\?>");
    private static final Pattern JUMP = Pattern.compile("^(D\\.?C\\.?|D\\.?S\\.?|Fine|Coda|Segno)",
            Pattern.CASE_INSENSITIVE);
    private static final double[] BASES = {4, 2, 1, .5, .25, .125};
    private static final Map<String, String> ARTICULATIONS = new HashMap<>();

    static {
        ARTICULATIONS.put("accent", "accent");
        ARTICULATIONS.put("staccato", "staccato");
        ARTICULATIONS.put("tenuto", "tenuto");
        ARTICULATIONS.put("strong-accent", "marcato");
    }

    private MusicXmlReader() {
    }

    /** Файл як є: .musicxml / .xml або стиснений .mxl (розпізнається за вмістом). */
    public static Score read(InputStream in) throws IOException, MusicXmlException {
        ByteArrayOutputStream buf = new ByteArrayOutputStream();
        byte[] chunk = new byte[16384];
        for (int n; (n = in.read(chunk)) > 0; ) buf.write(chunk, 0, n);
        return read(buf.toByteArray());
    }

    public static Score read(byte[] bytes) throws MusicXmlException {
        return parse(decode(Mxl.isZip(bytes) ? Mxl.extractScore(bytes) : bytes));
    }

    /** Текст MusicXML. */
    public static Score parse(String xml) throws MusicXmlException {
        String src = xml.startsWith("﻿") ? xml.substring(1) : xml;
        // заголовок прибираємо: рядок уже декодований, а encoding у ньому буває брехливий
        src = XML_DECL.matcher(src).replaceFirst("");
        return fromRoot(Element.parse(src));
    }

    /** Байти → текст: BOM UTF-16 поважаємо, решта — UTF-8, як у прототипі. */
    static String decode(byte[] b) {
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFE && (b[1] & 0xFF) == 0xFF) {
            return new String(b, 2, b.length - 2, Charset.forName("UTF-16BE"));
        }
        if (b.length >= 2 && (b[0] & 0xFF) == 0xFF && (b[1] & 0xFF) == 0xFE) {
            return new String(b, 2, b.length - 2, Charset.forName("UTF-16LE"));
        }
        return new String(b, UTF_8);
    }

    private static Score fromRoot(Element root) throws MusicXmlException {
        if (root == null) throw new MusicXmlException("Це не схоже на XML.");
        if (root.name.equals("score-timewise")) {
            throw new MusicXmlException(
                    "Це score-timewise. Потрібен score-partwise — MuseScore експортує саме його.");
        }
        if (!root.name.equals("score-partwise")) {
            throw new MusicXmlException("Кореневий елемент «" + root.name + "», а має бути score-partwise.");
        }
        List<Element> creators = kids(kid(root, "identification"), "creator");
        String title = firstNonEmpty(txt(kid(root, "work"), "work-title"), txt(root, "movement-title"), "Без назви");
        String composer = firstNonEmpty(creatorOfType(creators, "composer"),
                creators.isEmpty() ? "" : creators.get(0).text(), "невідомий автор");
        String arranger = creatorOfType(creators, "arranger");

        Map<String, String[]> meta = new HashMap<>();
        for (Element sp : kids(kid(root, "part-list"), "score-part")) {
            String id = sp.attr("id");
            meta.put(id, new String[]{
                    firstNonEmpty(txt(sp, "part-name"), id),
                    txt(kid(sp, "score-instrument"), "instrument-name")});
        }

        List<Part> parts = new ArrayList<>();
        for (Element partEl : kids(root, "part")) readPart(partEl, meta, parts);
        if (parts.isEmpty()) throw new MusicXmlException("У файлі немає жодної партії з нотами.");
        return new Score(title, composer, arranger, parts);
    }

    private static String creatorOfType(List<Element> creators, String type) {
        for (Element c : creators) {
            String t = c.attr("type");
            if (t != null && t.toLowerCase(Locale.ROOT).equals(type)) return c.text();
        }
        return "";
    }

    /** Подія з одного &lt;note&gt; (з дочірніми нотами акорду). */
    private static final class Ev {
        String staff;
        String voice;
        double t;
        double dur;
        boolean rest;
        final List<Pitch> notes = new ArrayList<>();
        List<Note> graces;
        String lyric;
        String tie;
        String slur;
        List<String> art;
        String tupPos;
        int[] tup;
    }

    private static void readPart(Element partEl, Map<String, String[]> meta, List<Part> parts) {
        String id = partEl.attr("id");
        String[] info = meta.get(id);
        if (info == null) info = new String[]{id != null ? id : "Партія", ""};

        double divisions = 1;
        int[] meter = {4, 4};
        int fifths = 0;
        Meter firstMeter = null;
        Integer firstFifths = null;
        Integer firstTranspose = null;
        Map<String, String> clefs = new HashMap<>();
        Map<Integer, Bar> bars = new HashMap<>();
        // staff → (номер такту → ноти)
        Map<String, Map<Integer, List<Note>>> staffMeasures = new TreeMap<>();
        Map<String, Map<Integer, List<Note>>> staffV2 = new HashMap<>();

        List<Element> measureEls = kids(partEl, "measure");
        for (int mi = 0; mi < measureEls.size(); mi++) {
            Element mEl = measureEls.get(mi);
            Bar bar = new Bar();
            for (Element at : kids(mEl, "attributes")) {
                divisions = num(at, "divisions", divisions);
                Element t = kid(at, "time");
                if (t != null) {
                    int[] nm = {(int) num(t, "beats", meter[0]), (int) num(t, "beat-type", meter[1])};
                    if (mi > 0 && (nm[0] != meter[0] || nm[1] != meter[1])) bar.meter = new Meter(nm[0], nm[1]);
                    meter = nm;
                    if (firstMeter == null) firstMeter = new Meter(nm[0], nm[1]);
                }
                Element k = kid(at, "key");
                if (k != null) {
                    int nf = (int) num(k, "fifths", fifths);
                    if (mi > 0 && nf != fifths) bar.fifths = nf;
                    fifths = nf;
                    if (firstFifths == null) firstFifths = nf;
                }
                for (Element c : kids(at, "clef")) {
                    String number = c.attr("number") != null ? c.attr("number") : "1";
                    if (clefs.containsKey(number)) continue;
                    String sign = txt(c, "sign").toUpperCase(Locale.ROOT);
                    clefs.put(number, sign.equals("F") ? "bass"
                            : sign.equals("C") ? "alto"
                            : sign.equals("PERCUSSION") ? "perc" : "treble");
                }
                Element tr = kid(at, "transpose");
                if (tr != null && firstTranspose == null) {
                    firstTranspose = (int) -(num(tr, "chromatic", 0) + 12 * num(tr, "octave-change", 0));
                }
            }
            readBarlines(mEl, bar);
            readDirections(mEl, bar);
            if (!bar.isEmpty()) bars.put(mi, bar);

            List<Ev> evs = readEvents(mEl);
            Set<String> staves = new LinkedHashSet<>();
            for (Ev e : evs) staves.add(e.staff);
            if (staves.isEmpty()) staves.add("1");
            for (String sv : staves) {
                List<Ev> all = new ArrayList<>();
                for (Ev e : evs) if (e.staff.equals(sv)) all.add(e);
                buildStaffMeasure(all, divisions, mi, sv, staffMeasures, staffV2);
            }
        }

        boolean multi = staffMeasures.size() > 1;
        Meter partMeter = firstMeter != null ? firstMeter : new Meter(4, 4);
        for (String sv : staffMeasures.keySet()) {
            String clef = clefs.containsKey(sv) ? clefs.get(sv) : "treble";
            Map<Integer, List<Note>> byIndex = staffMeasures.get(sv);
            Map<Integer, List<Note>> v2 = staffV2.get(sv);
            List<Measure> measures = new ArrayList<>();
            for (int mi = 0; mi < measureEls.size(); mi++) {
                List<Note> notes = byIndex.get(mi);
                measures.add(new Measure(mi, notes != null ? notes : new ArrayList<Note>(),
                        v2 != null ? v2.get(mi) : null, bars.get(mi)));
            }
            String name = info[0] + (multi ? (clef.equals("bass") ? " (ліва рука)" : " (права рука)") : "");
            parts.add(new Part(name, firstNonEmpty(info[1], info[0]), clef, partMeter,
                    firstFifths != null ? firstFifths : 0,
                    firstTranspose != null ? firstTranspose : 0, measures));
        }
    }

    private static void readBarlines(Element mEl, Bar bar) {
        for (Element bl : kids(mEl, "barline")) {
            Element r = kid(bl, "repeat");
            if (r != null) {
                String dir = "forward".equals(r.attr("direction")) ? "start" : "end";
                bar.repeat = bar.repeat != null && !bar.repeat.equals(dir) ? "both" : dir;
            }
            Element en = kid(bl, "ending");
            if (en != null && en.attr("number") != null && !en.attr("number").isEmpty()) {
                bar.ending.clear();
                for (String s : en.attr("number").split("[,\\s]+")) {
                    if (s.isEmpty()) continue;
                    try {
                        bar.ending.add(Integer.parseInt(s));
                    } catch (NumberFormatException ignored) {
                        // «1-2» і подібне прототип теж не розуміє
                    }
                }
                if ("start".equals(en.attr("type"))) bar.endingStop = false;
            }
        }
    }

    private static void readDirections(Element mEl, Bar bar) {
        for (Element dr : kids(mEl, "direction")) {
            for (Element dt : kids(dr, "direction-type")) {
                for (Element w : kids(dt, "words")) {
                    String t = w.text();
                    if (t.isEmpty()) continue;
                    if (JUMP.matcher(t).find()) bar.jump = t;
                    else bar.text = t;
                }
                Element rh = kid(dt, "rehearsal");
                if (rh != null && !rh.text().isEmpty()) bar.mark = rh.text();
                Element dy = kid(dt, "dynamics");
                if (dy != null && !dy.kids.isEmpty()) bar.dynamics = dy.kids.get(0).name;
                Element wd = kid(dt, "wedge");
                if (wd != null) {
                    String ty = wd.attr("type") == null ? "" : wd.attr("type").toLowerCase(Locale.ROOT);
                    if (ty.equals("stop")) {
                        bar.hairpin = "stop";
                    } else {
                        bar.hairpin = "start";
                        bar.hairpinKind = ty.equals("diminuendo") ? "dim" : "cresc";
                    }
                }
                Element mm = kid(dt, "metronome");
                if (mm != null) bar.text = (bar.text != null ? bar.text + " " : "") + "♩=" + txt(mm, "per-minute");
            }
        }
    }

    private static List<Ev> readEvents(Element mEl) {
        List<Ev> ev = new ArrayList<>();
        double t = 0;
        Map<String, Ev> lastByStaff = new HashMap<>();
        Map<String, List<Note>> pendingGrace = new HashMap<>();
        for (Element el : mEl.kids) {
            if (el.name.equals("backup")) {
                t = Math.max(0, t - num(el, "duration", 0));
                continue;
            }
            if (el.name.equals("forward")) {
                t += num(el, "duration", 0);
                continue;
            }
            if (!el.name.equals("note")) continue;
            String staff = firstNonEmpty(txt(el, "staff"), "1");
            if (kid(el, "grace") != null) {                // форшлаг чекає на свою ноту
                Pitch g = pitchOf(el);
                if (g != null) {
                    Note gn = new Note(.25);
                    gn.pitches.add(g);
                    List<Note> list = pendingGrace.get(staff);
                    if (list == null) pendingGrace.put(staff, list = new ArrayList<>());
                    list.add(gn);
                }
                continue;
            }
            if (kid(el, "chord") != null) {
                Ev prev = lastByStaff.get(staff);
                if (prev != null && !prev.rest) {
                    Pitch n = pitchOf(el);
                    if (n != null) prev.notes.add(n);
                }
                continue;
            }
            Ev e = new Ev();
            e.staff = staff;
            e.voice = firstNonEmpty(txt(el, "voice"), "1");
            e.t = t;
            e.dur = num(el, "duration", 0);
            e.rest = kid(el, "rest") != null;
            List<Note> gr = pendingGrace.get(staff);
            if (gr != null && !gr.isEmpty()) {
                e.graces = gr;
                pendingGrace.put(staff, new ArrayList<Note>());
            }
            Element ly = kid(el, "lyric");
            if (ly != null && !txt(ly, "text").isEmpty()) e.lyric = txt(ly, "text");
            if (!e.rest) {
                Pitch n = pitchOf(el);
                if (n != null) e.notes.add(n);
                else e.rest = true;
            }
            Element nt = kid(el, "notations");
            if (nt != null) {
                e.tie = startStop(kids(nt, "tied"));
                e.slur = startStop(kids(nt, "slur"));
                Element ar = kid(nt, "articulations");
                if (ar != null) {
                    List<String> got = new ArrayList<>();
                    for (Element k : ar.kids) if (ARTICULATIONS.containsKey(k.name)) got.add(ARTICULATIONS.get(k.name));
                    if (!got.isEmpty()) e.art = got;
                }
                if (kid(nt, "fermata") != null) {
                    if (e.art == null) e.art = new ArrayList<>();
                    e.art.add("fermata");
                }
                Element tp = kid(nt, "tuplet");
                if (tp != null) e.tupPos = tp.attr("type");
            }
            Element tm = kid(el, "time-modification");
            if (tm != null) e.tup = new int[]{(int) num(tm, "actual-notes", 3), (int) num(tm, "normal-notes", 2)};
            ev.add(e);
            lastByStaff.put(staff, e);
            t += e.dur;
        }
        return ev;
    }

    /** Типи з &lt;tied&gt; / &lt;slur&gt;: один → він, кілька → both, жодного → null. */
    private static String startStop(List<Element> els) {
        List<String> types = new ArrayList<>();
        for (Element x : els) {
            String ty = x.attr("type");
            if (ty != null && !ty.isEmpty()) types.add(ty);
        }
        if (types.isEmpty()) return null;
        return types.size() > 1 ? "both" : types.get(0);
    }

    private static void buildStaffMeasure(List<Ev> all, double divisions, int mi, String sv,
                                          Map<String, Map<Integer, List<Note>>> staffMeasures,
                                          Map<String, Map<Integer, List<Note>>> staffV2) {
        List<String> voices = new ArrayList<>();
        for (Ev e : all) if (!voices.contains(e.voice)) voices.add(e.voice);
        Collections.sort(voices);
        String v1 = voices.isEmpty() ? null : voices.get(0);

        if (voices.size() > 1) {
            List<Ev> second = new ArrayList<>();
            for (Ev e : all) if (e.voice.equals(voices.get(1)) && !e.rest) second.add(e);
            Collections.sort(second, (a, b) -> Double.compare(a.t, b.t));
            if (!second.isEmpty()) {
                List<Note> out = new ArrayList<>();
                for (Ev e : second) {
                    Note n = new Note(snapDur(e.dur / divisions));
                    n.pitches.addAll(sortedPitches(e.notes));
                    out.add(n);
                }
                mapFor(staffV2, sv).put(mi, out);
            }
        }

        List<Ev> list = new ArrayList<>();
        for (Ev e : all) if (e.voice.equals(v1)) list.add(e);
        Collections.sort(list, (a, b) -> a.t != b.t ? Double.compare(a.t, b.t) : b.notes.size() - a.notes.size());
        List<Note> out = new ArrayList<>();
        double cur = 0;
        for (Ev e : list) {
            if (e.t < cur) continue;
            if (e.t > cur) out.add(new Note(snapDur((e.t - cur) / divisions)));
            Note nn = new Note(snapDur(e.dur / divisions));
            if (!e.rest) nn.pitches.addAll(sortedPitches(e.notes));
            nn.tie = e.tie;
            nn.slur = e.slur;
            if (e.art != null) nn.articulations.addAll(e.art);
            if (e.graces != null) nn.graces.addAll(e.graces);
            nn.lyric = e.lyric;
            if (e.tup != null) nn.tuplet = new Tuplet(e.tup[0], e.tup[1], e.tupPos != null ? e.tupPos : "mid");
            out.add(nn);
            cur = e.t + e.dur;
        }
        mapFor(staffMeasures, sv).put(mi, out);
    }

    private static Map<Integer, List<Note>> mapFor(Map<String, Map<Integer, List<Note>>> m, String key) {
        Map<Integer, List<Note>> v = m.get(key);
        if (v == null) m.put(key, v = new LinkedHashMap<>());
        return v;
    }

    private static List<Pitch> sortedPitches(List<Pitch> notes) {
        List<Pitch> s = new ArrayList<>(notes);
        Collections.sort(s, (a, b) -> Double.compare(a.sortKey(), b.sortKey()));
        return s;
    }

    private static Pitch pitchOf(Element el) {
        Element p = kid(el, "pitch");
        if (p != null) {
            int st = STEP_LETTERS.indexOf(txt(p, "step").toUpperCase(Locale.ROOT));
            if (st < 0 || txt(p, "step").length() != 1) return null;
            return new Pitch(st, (int) Math.round(num(p, "alter", 0)), (int) num(p, "octave", 4), false);
        }
        // ударні: висоти немає, є лише місце на стані
        Element u = kid(el, "unpitched");
        if (u != null) {
            String ds = firstNonEmpty(txt(u, "display-step"), "B").toUpperCase(Locale.ROOT);
            int st = ds.length() == 1 ? STEP_LETTERS.indexOf(ds) : -1;
            return new Pitch(st >= 0 ? st : 6, 0, (int) num(u, "display-octave", 4), true);
        }
        return null;
    }

    /** Тривалість у чвертках, притягнута до найближчої нотної (з крапкою, тріольної). */
    static double snapDur(double beats) {
        double best = 0, err = 1e9;
        for (double b : BASES) {
            for (double dot : new double[]{1, 1.5}) {
                for (double tri : new double[]{1, 2.0 / 3}) {
                    double v = b * dot * tri, e = Math.abs(v - beats);
                    if (e < err) {
                        err = e;
                        best = v;
                    }
                }
            }
        }
        if (err <= .003) return best;
        double r = Math.round(beats * 48) / 48.0;
        return r != 0 ? r : .25;
    }

    private static String firstNonEmpty(String... values) {
        for (String v : values) if (v != null && !v.isEmpty()) return v;
        return values.length > 0 && values[values.length - 1] != null ? values[values.length - 1] : "";
    }
}
