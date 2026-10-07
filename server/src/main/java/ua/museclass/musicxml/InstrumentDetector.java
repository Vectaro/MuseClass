package ua.museclass.musicxml;

import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Визначає інструмент партії за назвою і MIDI-даними. Усі правила — з
 * довідника shared/instruments.json ({@link Instruments}), опис — у
 * docs/instruments.md.
 *
 * Назва важить більше за MIDI-програму: редактори часто лишають
 * фортепіанний звук за замовчуванням навіть для інших партій.
 */
public final class InstrumentDetector {

    /** Коди, які приймає API і БД. */
    public static final Set<String> CODES = Set.copyOf(Instruments.get().codes);

    /** Саксофон без уточнення виду; вид — за кодами saxophone_* з довідника. */
    private static final String[] SAX_WORDS = {"sax", "сакс"};
    static final String SAX_PREFIX = "saxophone_";

    private InstrumentDetector() {}

    public static String detect(String partName, String instrumentName, Integer midiProgram, Integer midiChannel) {
        Instruments dict = Instruments.get();
        String hay = ((instrumentName == null ? "" : instrumentName) + " "
                + (partName == null ? "" : partName)).toLowerCase(Locale.ROOT);

        // 1. за назвою, у порядку detect_order: перший збіг перемагає
        List<Instruments.Entry> order = dict.detectOrder;
        for (int i = 0; i < order.size(); i++) {
            for (String k : order.get(i).keywords()) {
                if (hay.contains(k)) return order.get(i).code();
            }
            // 2. «Sax» без уточнення — одразу після саксофонів з довідника, ще до
            //    вокалу: інакше «Baritone Saxophone» став би голосом. Назва важливіша
            //    за MIDI, але якщо програма каже, який саме саксофон, — віримо їй.
            if (i == dict.lastSaxIndex && containsAny(hay, SAX_WORDS)) {
                String byProgram = midiProgram == null ? null : dict.byProgram.get(midiProgram);
                return byProgram != null && byProgram.startsWith(SAX_PREFIX) ? byProgram : dict.saxWithoutKind;
            }
        }

        // 3. назва не допомогла: 10-й канал — ударні, далі програма General MIDI
        if (midiChannel != null && midiChannel == 10) return dict.midiChannel10;
        if (midiProgram != null) return dict.byProgram.get(midiProgram);
        return null;
    }

    private static boolean containsAny(String hay, String[] words) {
        for (String w : words) {
            if (hay.contains(w)) return true;
        }
        return false;
    }
}
