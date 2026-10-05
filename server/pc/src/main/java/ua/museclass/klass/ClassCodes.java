package ua.museclass.klass;

import java.util.Locale;
import java.util.Optional;
import java.util.random.RandomGenerator;

/**
 * Коди класів формату XXX-0X, як у прототипі (PNO-3A, SOL-2B).
 *
 * Префікс викладач може задати сам (три латинські літери), інакше випадковий.
 * У випадковій частині немає I, O, 0 і 1 — їх плутають, коли диктують код голосом.
 */
public final class ClassCodes {

    static final String LETTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ";
    static final String DIGITS = "23456789";

    private ClassCodes() {}

    public static String generate(String prefix, RandomGenerator rnd) {
        String p = prefix != null ? prefix : randomLetters(3, rnd);
        return p + "-" + DIGITS.charAt(rnd.nextInt(DIGITS.length()))
                + LETTERS.charAt(rnd.nextInt(LETTERS.length()));
    }

    public static String randomLetters(int n, RandomGenerator rnd) {
        StringBuilder sb = new StringBuilder(n);
        for (int i = 0; i < n; i++) sb.append(LETTERS.charAt(rnd.nextInt(LETTERS.length())));
        return sb.toString();
    }

    /** Префікс від викладача: «pno», «ПНО» → PNO. Порожній — нема префікса. */
    public static Optional<String> normalizePrefix(String raw) {
        if (raw == null || raw.isBlank()) return Optional.empty();
        String p = latinize(raw.trim().toUpperCase(Locale.ROOT));
        if (!p.matches("[A-Z]{3}")) {
            throw new IllegalArgumentException("Префікс коду — рівно три латинські літери, наприклад PNO.");
        }
        return Optional.of(p);
    }

    /**
     * Те, що ввів учень, → канонічний код або empty, якщо формат не той.
     * Прощає нижній регістр, пробіли, відсутній дефіс і кирилицю з
     * української розкладки, що виглядає як латиниця (РNО-3А).
     */
    public static Optional<String> normalizeInput(String raw) {
        if (raw == null) return Optional.empty();
        String s = latinize(raw.toUpperCase(Locale.ROOT)).replaceAll("[\\s\\-–—_]", "");
        if (!s.matches("[A-Z]{3}[0-9][A-Z]")) return Optional.empty();
        return Optional.of(s.substring(0, 3) + "-" + s.substring(3));
    }

    private static String latinize(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            sb.append(switch (c) {
                case 'А' -> 'A';
                case 'В' -> 'B';
                case 'С' -> 'C';
                case 'Е' -> 'E';
                case 'Н' -> 'H';
                case 'І' -> 'I';
                case 'К' -> 'K';
                case 'М' -> 'M';
                case 'О' -> 'O';
                case 'Р' -> 'P';
                case 'Т' -> 'T';
                case 'Х' -> 'X';
                default -> c;
            });
        }
        return sb.toString();
    }
}
