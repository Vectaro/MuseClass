package ua.museclass.musicxml;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class InstrumentDetectorTest {

    static String d(String part, String instr, Integer prog) {
        return InstrumentDetector.detect(part, instr, prog, null);
    }

    @Test
    void prototypeInstrumentNames() {
        assertEquals("piano", d("Фортепіано", null, null));
        assertEquals("guitar", d("Гітара", null, null));
        assertEquals("voice", d("Вокал", null, null));
        assertEquals("violin", d("Скрипка", null, null));
        assertEquals("trumpet", d("Труба in B♭", null, null));
        assertEquals("flute", d("Флейта", null, null));
        assertEquals("bass_guitar", d("Бас-гітара", null, null));
        assertEquals("drums", d("Ударні", null, null));
        assertEquals("saxophone", d("Саксофон in E♭", null, null));
        assertEquals("bandura", d("Бандура", null, null));
    }

    @Test
    void englishExportNames() {
        assertEquals("piano", d("Piano", "Grand Piano", 1));
        assertEquals("bass_guitar", d("Electric Bass", null, 34));
        assertEquals("guitar", d("Classical Guitar", null, 25));
        assertEquals("saxophone", d("Alto Saxophone", null, 66));
        assertEquals("saxophone", d("Tenor Sax.", null, null), "tenor sax — не голос");
        assertEquals("voice", d("Soprano", null, null));
        assertEquals("violin", d("Vln. I", null, null));
        assertEquals("trumpet", d("Trumpet in Bb", null, null));
        assertEquals("drums", d("Drumset", null, null));
    }

    @Test
    void nameBeatsDefaultPianoProgram() {
        assertEquals("violin", d("Скрипка", null, 1));
    }

    @Test
    void midiFallback() {
        assertEquals("violin", d("Part 1", null, 41));
        assertEquals("flute", d("", null, 74));
        assertEquals("drums", InstrumentDetector.detect("Part 3", null, 1, 10), "10-й канал — ударні");
        assertNull(d("Viola", null, 42));
        assertNull(d("Part 1", null, null));
        assertNull(d(null, null, null));
    }

    @Test
    void ambiguousBassIsNotGuitarByName() {
        assertNull(d("Bassoon", null, 71));
        assertNull(d("Bass Clarinet", null, 72));
        assertNull(d("Contrabass", null, 44));
    }
}
