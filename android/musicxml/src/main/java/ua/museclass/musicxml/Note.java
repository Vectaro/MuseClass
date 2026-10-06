package ua.museclass.musicxml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Подія в такті: нота, акорд або пауза (порожній {@link #pitches()}).
 * Тривалість — у чвертках, уже з урахуванням тріолі (тріольна вісімка = 1/3).
 */
public final class Note {
    final List<Pitch> pitches = new ArrayList<>();
    final double duration;
    String tie;
    String slur;
    final List<String> articulations = new ArrayList<>();
    final List<Note> graces = new ArrayList<>();
    String lyric;
    Tuplet tuplet;

    Note(double duration) {
        this.duration = duration;
    }

    /** Висоти знизу вгору; порожньо — пауза. */
    public List<Pitch> pitches() {
        return Collections.unmodifiableList(pitches);
    }

    public boolean isRest() {
        return pitches.isEmpty();
    }

    public double duration() {
        return duration;
    }

    /** start / stop / both або null. */
    public String tie() {
        return tie;
    }

    /** start / stop / both або null. */
    public String slur() {
        return slur;
    }

    /** accent, staccato, tenuto, marcato, fermata. */
    public List<String> articulations() {
        return Collections.unmodifiableList(articulations);
    }

    /** Форшлаги перед нотою, кожен — одна висота на шістнадцяту. */
    public List<Note> graces() {
        return Collections.unmodifiableList(graces);
    }

    public String lyric() {
        return lyric;
    }

    public Tuplet tuplet() {
        return tuplet;
    }
}
