package ua.museclass.musicxml;

import java.util.Collections;
import java.util.List;

/** Партитура: назва, автори і партії. */
public final class Score {
    private final String title;
    private final String composer;
    private final String arranger;
    private final List<Part> parts;

    Score(String title, String composer, String arranger, List<Part> parts) {
        this.title = title;
        this.composer = composer;
        this.arranger = arranger;
        this.parts = parts;
    }

    public String title() {
        return title;
    }

    public String composer() {
        return composer;
    }

    /** Порожній рядок, якщо аранжувальника немає. */
    public String arranger() {
        return arranger;
    }

    public List<Part> parts() {
        return Collections.unmodifiableList(parts);
    }

    /** Кількість тактів — найдовша партія. */
    public int measureCount() {
        int max = 0;
        for (Part p : parts) max = Math.max(max, p.measures().size());
        return max;
    }
}
