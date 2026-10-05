package ua.museclass.musicxml;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Визначає інструмент партії за назвою і MIDI-даними.
 * Коди збігаються з переліком у прототипі й перевіркою в БД.
 *
 * Назва важить більше за MIDI-програму: редактори часто лишають
 * фортепіанний звук за замовчуванням навіть для інших партій.
 */
public final class InstrumentDetector {

    public static final Set<String> CODES = Set.of(
            "piano", "guitar", "voice", "violin", "trumpet", "flute",
            "bass_guitar", "drums", "saxophone", "bandura");

    private record Rule(String code, List<String> keywords) {}

    // Порядок важливий: «бас-гітара» раніше за «гітару», саксофони раніше за
    // голоси (alto/tenor sax), бандура раніше за все інше.
    private static final List<Rule> RULES = List.of(
            new Rule("bandura", List.of("bandura", "бандур")),
            new Rule("bass_guitar", List.of("bass guitar", "electric bass", "fretless bass",
                    "acoustic bass", "бас-гітар", "бас гітар", "басгітар")),
            new Rule("saxophone", List.of("sax", "саксофон")),
            new Rule("guitar", List.of("guitar", "гітар", "gtr")),
            new Rule("piano", List.of("piano", "pno", "фортеп", "піаніно", "рояль", "клавір", "keyboard")),
            new Rule("violin", List.of("violin", "vln", "скрипк")),
            new Rule("trumpet", List.of("trumpet", "tpt", "trp", "труба", "cornet", "корнет")),
            new Rule("flute", List.of("flute", "флейт")),
            new Rule("drums", List.of("drum", "percussion", "ударн", "барабан")),
            new Rule("voice", List.of("voice", "vocal", "soprano", "mezzo", "tenor", "baritone",
                    "choir", "вокал", "голос", "сопрано", "тенор", "баритон", "хор", "спів")));

    private InstrumentDetector() {}

    public static String detect(String partName, String instrumentName, Integer midiProgram, Integer midiChannel) {
        String hay = ((instrumentName == null ? "" : instrumentName) + " "
                + (partName == null ? "" : partName)).toLowerCase(Locale.ROOT);
        for (Rule r : RULES) {
            for (String k : r.keywords()) {
                if (hay.contains(k)) return r.code();
            }
        }
        if (midiChannel != null && midiChannel == 10) return "drums";
        if (midiProgram != null) return byProgram(midiProgram);
        return null;
    }

    /** General MIDI, нумерація з 1, як у MusicXML. */
    static String byProgram(int p) {
        if (p >= 1 && p <= 8) return "piano";
        if (p >= 25 && p <= 32) return "guitar";
        if (p >= 33 && p <= 40) return "bass_guitar";
        if (p == 41) return "violin";
        if (p >= 53 && p <= 55) return "voice";
        if (p == 57) return "trumpet";
        if (p >= 65 && p <= 68) return "saxophone";
        if (p == 74) return "flute";
        return null;
    }
}
