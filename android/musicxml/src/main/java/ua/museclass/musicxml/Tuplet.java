package ua.museclass.musicxml;

/** Тріоль і подібні: actual нот на місці normal. pos — start / stop / mid. */
public final class Tuplet {
    public final int actual;
    public final int normal;
    public final String pos;

    public Tuplet(int actual, int normal, String pos) {
        this.actual = actual;
        this.normal = normal;
        this.pos = pos;
    }
}
