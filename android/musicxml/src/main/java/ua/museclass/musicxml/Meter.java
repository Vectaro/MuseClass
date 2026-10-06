package ua.museclass.musicxml;

/** Розмір такту: 3/4 → beats=3, beatType=4. */
public final class Meter {
    public final int beats;
    public final int beatType;

    public Meter(int beats, int beatType) {
        this.beats = beats;
        this.beatType = beatType;
    }

    /** Довжина такту в чвертках. */
    public double quarters() {
        return beats * 4.0 / beatType;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Meter && ((Meter) o).beats == beats && ((Meter) o).beatType == beatType;
    }

    @Override
    public int hashCode() {
        return beats * 31 + beatType;
    }

    @Override
    public String toString() {
        return beats + "/" + beatType;
    }
}
