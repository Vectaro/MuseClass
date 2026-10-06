package ua.museclass.musicxml;

/**
 * Висота ноти: ступінь + альтерація + октава, а не голий MIDI-номер — інакше
 * не відрізнити сі-бемоль від ля-дієза. Ударні — {@link #unpitched}: висоти
 * немає, є лише місце на стані (display-step / display-octave).
 */
public final class Pitch {
    static final String STEPS = "CDEFGAB";
    private static final int[] STEP_PC = {0, 2, 4, 5, 7, 9, 11};

    /** 0..6 = C..B */
    public final int step;
    /** бемолі мінус, дієзи плюс */
    public final int alter;
    public final int octave;
    public final boolean unpitched;

    public Pitch(int step, int alter, int octave, boolean unpitched) {
        this.step = step;
        this.alter = alter;
        this.octave = octave;
        this.unpitched = unpitched;
    }

    /** MIDI-номер; для ударних — null. */
    public Integer midi() {
        return unpitched ? null : (octave + 1) * 12 + STEP_PC[step] + alter;
    }

    /** Діатонічна позиція: octave*7 + step. Що більше, то вище на стані. */
    public int diatonic() {
        return octave * 7 + step;
    }

    /** Ключ сортування нот акорду знизу вгору (як у прототипі). */
    double sortKey() {
        Integer m = midi();
        return m == null ? diatonic() * 2 : m;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        if (unpitched) sb.append('x');
        sb.append(STEPS.charAt(step));
        for (int i = 0; i < Math.abs(alter); i++) sb.append(alter > 0 ? '#' : 'b');
        return sb.append(octave).toString();
    }
}
