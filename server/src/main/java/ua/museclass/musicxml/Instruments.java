package ua.museclass.musicxml;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import tools.jackson.databind.json.JsonMapper;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Довідник інструментів з shared/instruments.json — спільного для сервера,
 * прототипу й Android. Maven копіює файл у classpath як
 * {@value #RESOURCE} (див. pom.xml), тож джерело одне — файл у shared/.
 *
 * Читається один раз при першому зверненні й перевіряється на цілісність:
 * зламаний довідник має валити старт і тести, а не тихо псувати впізнавання.
 */
public final class Instruments {

    static final String RESOURCE = "/museclass/instruments.json";

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Entry(String code, List<Integer> gm, List<String> keywords) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Dict(List<Entry> instruments,
                @JsonProperty("detect_order") List<String> detectOrder,
                Map<String, String> fallback,
                Map<String, String> renamed) {}

    /** Коди в порядку довідника (= порядок у UI). */
    public final List<String> codes;
    /** Правила за назвою, вже в порядку detect_order. */
    final List<Entry> detectOrder;
    /** Позиція останнього saxophone_* у detectOrder: після неї — «sax» без уточнення. */
    final int lastSaxIndex;
    /** Програма General MIDI (з 1) → код. */
    final Map<Integer, String> byProgram;
    /** «Sax» без уточнення. */
    final String saxWithoutKind;
    /** MIDI-канал 10. */
    final String midiChannel10;
    /** Старий код → новий. */
    public final Map<String, String> renamed;

    private static final Instruments INSTANCE = load();

    public static Instruments get() {
        return INSTANCE;
    }

    private Instruments(Dict d) {
        Map<String, Entry> byCode = new HashMap<>();
        Set<String> ordered = new LinkedHashSet<>();
        for (Entry e : d.instruments()) {
            if (e.code() == null || !ordered.add(e.code())) {
                throw new IllegalStateException("instruments.json: порожній або повторений code " + e.code());
            }
            byCode.put(e.code(), e);
        }
        codes = List.copyOf(ordered);

        if (d.detectOrder() == null || !new HashSet<>(d.detectOrder()).equals(ordered)
                || d.detectOrder().size() != ordered.size()) {
            throw new IllegalStateException("instruments.json: detect_order має містити кожен code рівно раз");
        }
        detectOrder = d.detectOrder().stream().map(byCode::get).toList();
        int last = -1;
        for (int i = 0; i < detectOrder.size(); i++) {
            if (detectOrder.get(i).code().startsWith(InstrumentDetector.SAX_PREFIX)) last = i;
        }
        lastSaxIndex = last;

        Map<Integer, String> programs = new HashMap<>();
        for (Entry e : d.instruments()) {
            for (Integer p : e.gm() == null ? List.<Integer>of() : e.gm()) {
                String prev = programs.put(p, e.code());
                if (prev != null) {
                    throw new IllegalStateException("instruments.json: програма " + p + " і в " + prev + ", і в " + e.code());
                }
            }
        }
        byProgram = Map.copyOf(programs);

        Map<String, String> fb = d.fallback() == null ? Map.of() : d.fallback();
        saxWithoutKind = known(fb.get("sax_without_kind"), "fallback.sax_without_kind");
        midiChannel10 = known(fb.get("midi_channel_10"), "fallback.midi_channel_10");
        renamed = d.renamed() == null ? Map.of() : Map.copyOf(d.renamed());
        for (String to : renamed.values()) known(to, "renamed");
    }

    private String known(String code, String where) {
        if (code == null || !codes.contains(code)) {
            throw new IllegalStateException("instruments.json: " + where + " — невідомий code " + code);
        }
        return code;
    }

    private static Instruments load() {
        try (InputStream in = Instruments.class.getResourceAsStream(RESOURCE)) {
            if (in == null) {
                throw new IllegalStateException("Немає " + RESOURCE + " у classpath: Maven копіює його з shared/instruments.json");
            }
            return new Instruments(JsonMapper.builder().build().readValue(in, Dict.class));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
