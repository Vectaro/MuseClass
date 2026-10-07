package ua.museclass.musicxml;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.io.InputStream;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Правила й приклади — з docs/instruments.md і shared/instruments.json. */
class InstrumentDetectorTest {

    static String d(String part, String instr, Integer prog) {
        return InstrumentDetector.detect(part, instr, prog, null);
    }

    @Test
    void dictionaryHas24Codes() {
        List<String> codes = Instruments.get().codes;
        assertEquals(24, codes.size());
        assertEquals("piano", codes.get(0), "порядок довідника = порядок у UI");
        assertTrue(InstrumentDetector.CODES.contains("saxophone_alto"));
        assertTrue(!InstrumentDetector.CODES.contains("saxophone"), "старий код перейменовано");
        assertEquals("saxophone_alto", Instruments.get().renamed.get("saxophone"));
    }

    /** Українська назва кожного інструмента з довідника (поле name) впізнається як він сам. */
    @Test
    void everyUkrainianNameFromDictionary() throws Exception {
        JsonNode root;
        try (InputStream in = Instruments.class.getResourceAsStream(Instruments.RESOURCE)) {
            root = JsonMapper.builder().build().readTree(in);
        }
        int n = 0;
        for (JsonNode i : root.get("instruments")) {
            String name = i.get("name").asString();
            // «Баян / акордеон» — обидва слова мають працювати і поодинці
            for (String variant : name.split(" / ")) {
                assertEquals(i.get("code").asString(), d(variant, null, null), variant);
            }
            n++;
        }
        assertEquals(24, n);
    }

    @Test
    void prototypeBandPartNames() {
        assertEquals("trumpet", d("Труба 1 in B♭", null, null));
        assertEquals("saxophone_alto", d("Альт-саксофон in E♭", null, null));
        assertEquals("trombone", d("Тромбон", null, null));
        assertEquals("guitar", d("Гітара", null, null));
        assertEquals("bass_guitar", d("Бас-гітара", null, null));
        assertEquals("drums", d("Ударні", null, null));
        assertEquals("voice", d("Вокал", null, null));
    }

    @Test
    void englishExportNames() {
        assertEquals("piano", d("Piano", "Grand Piano", 1));
        assertEquals("bass_guitar", d("Electric Bass", null, 34));
        assertEquals("guitar", d("Classical Guitar", null, 25));
        assertEquals("saxophone_alto", d("Alto Saxophone", null, 66));
        assertEquals("saxophone_tenor", d("Tenor Sax.", null, null), "tenor sax — не голос");
        assertEquals("voice", d("Soprano", null, null));
        assertEquals("violin", d("Vln. I", null, null));
        assertEquals("viola", d("Viola", null, 42));
        assertEquals("cello", d("Violoncello", null, 43));
        assertEquals("trumpet", d("Trumpet in Bb", null, null));
        assertEquals("trombone", d("Trombone", null, 58));
        assertEquals("french_horn", d("Horn in F", null, 61));
        assertEquals("clarinet", d("Clarinet in Bb", null, 72));
        assertEquals("drums", d("Drumset", null, null));
        assertEquals("drums", d("Drum Kit", null, null));
    }

    /** Порядок detect_order з docs/instruments.md, «Впізнавання партії», п. 1. */
    @Test
    void detectOrderResolvesOverlaps() {
        assertEquals("bass_guitar", d("Bass Guitar", null, null), "бас-гітара раніше за гітару");
        assertEquals("bassoon", d("Contrabassoon", null, null), "фагот (contrabassoon) раніше за контрабас");
        assertEquals("double_bass", d("Contrabass", null, 44));
        assertEquals("double_bass", d("Контрабас", null, null));
        assertEquals("saxophone_tenor", d("Тенор-саксофон", null, null), "саксофони раніше за вокал (тенор)");
        assertEquals("saxophone_alto", d("Альт-саксофон", null, null), "саксофони раніше за альт");
        assertEquals("oboe", d("English Horn", null, null), "гобой (english horn) раніше за валторну (horn)");
        assertEquals("trombone", d("Tenor Trombone", null, null), "тромбон раніше за вокал (tenor)");
    }

    /** Відома двозначність з docs/instruments.md. */
    @Test
    void altoIsViolaButAltoVoiceIsVoice() {
        assertEquals("viola", d("Альт", null, null));
        assertEquals("voice", d("Alto Voice", null, null));
        assertEquals("voice", d("Contralto", null, null));
    }

    /** «Sax»/«саксофон» без уточнення → fallback.sax_without_kind. */
    @Test
    void saxWithoutKind() {
        assertEquals("saxophone_alto", d("Саксофон in E♭", null, null));
        assertEquals("saxophone_alto", d("Sax.", null, null));
        assertEquals("saxophone_alto", d("Saxophone", null, 1), "назва важливіша за фортепіанну програму");
        assertEquals("saxophone_tenor", d("Saxophone", null, 67), "але якщо програма уточнює саксофон — віримо їй");
        // знайдено на файлах OSMD: «baritone» з вокалу не має перебивати саксофон
        assertEquals("saxophone_alto", d("Baritone Saxophone", null, null));
        assertEquals("saxophone_tenor", d("Baritone Saxophone", "Baritone Saxophone", 68));
        assertEquals("saxophone_alto", d("Soprano Sax", null, null));
    }

    @Test
    void nameBeatsDefaultPianoProgram() {
        assertEquals("violin", d("Скрипка", null, 1));
        assertEquals("trombone", d("Тромбон", null, 1));
    }

    @Test
    void midiFallback() {
        assertEquals("violin", d("Part 1", null, 41));
        assertEquals("flute", d("", null, 74));
        assertEquals("recorder", d("", null, 75));
        assertEquals("tuba", d("Part 2", null, 59));
        assertEquals("drums", InstrumentDetector.detect("Part 3", null, 1, 10), "10-й канал — ударні");
        assertNull(d("Part 1", null, 100), "програма поза довідником");
        assertNull(d("Part 1", null, null));
        assertNull(d(null, null, null));
    }

    @Test
    void unknownInstrumentsStayNull() {
        assertNull(d("Harp", null, null));
        assertNull(d("Celesta", null, null));
        assertNull(d("Bass", null, null), "просто «bass» — двозначне, без MIDI не вгадуємо");
    }
}
