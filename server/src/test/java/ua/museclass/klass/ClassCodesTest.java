package ua.museclass.klass;

import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Optional;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ClassCodesTest {

    @Test
    void generatedCodesMatchFormat() {
        Random rnd = new Random(42);
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < 5000; i++) {
            String c = ClassCodes.generate(null, rnd);
            assertTrue(c.matches("[A-Z]{3}-[0-9][A-Z]"), c);
            assertFalse(c.substring(4).matches(".*[IO01].*"), "випадкова частина без I, O, 0, 1: " + c);
            seen.add(c);
        }
        assertTrue(seen.size() > 4900, "колізії мають бути рідкісні");
    }

    @Test
    void prefixIsKept() {
        String c = ClassCodes.generate("PNO", new Random(1));
        assertTrue(c.startsWith("PNO-"), c);
    }

    @Test
    void prefixNormalization() {
        assertEquals(Optional.of("PNO"), ClassCodes.normalizePrefix(" pno "));
        assertEquals(Optional.of("PHO"), ClassCodes.normalizePrefix("РНО"), "кирилиця-двійник");
        assertEquals(Optional.empty(), ClassCodes.normalizePrefix(""));
        assertEquals(Optional.empty(), ClassCodes.normalizePrefix(null));
        assertThrows(IllegalArgumentException.class, () -> ClassCodes.normalizePrefix("PN"));
        assertThrows(IllegalArgumentException.class, () -> ClassCodes.normalizePrefix("ФОР"));
        assertThrows(IllegalArgumentException.class, () -> ClassCodes.normalizePrefix("P1O"));
    }

    @Test
    void inputIsForgiving() {
        assertEquals(Optional.of("PNO-3A"), ClassCodes.normalizeInput("PNO-3A"));
        assertEquals(Optional.of("PNO-3A"), ClassCodes.normalizeInput(" pno3a "));
        assertEquals(Optional.of("PNO-3A"), ClassCodes.normalizeInput("pno – 3a"));
        assertEquals(Optional.of("PNO-3A"), ClassCodes.normalizeInput("РNО-3А"), "кирилиця з укр. розкладки");
        assertEquals(Optional.of("SOL-2B"), ClassCodes.normalizeInput("sol 2b"));
        assertEquals(Optional.empty(), ClassCodes.normalizeInput("PNO-33"));
        assertEquals(Optional.empty(), ClassCodes.normalizeInput("MUS-ABCD"));
        assertEquals(Optional.empty(), ClassCodes.normalizeInput(""));
        assertEquals(Optional.empty(), ClassCodes.normalizeInput(null));
    }
}
