package ua.museclass.musicxml;

import java.util.Collections;
import java.util.List;

/** Такт однієї партії: перший голос, другий голос (штилі вниз) і позначки. */
public final class Measure {
    private final int index;
    private final List<Note> notes;
    private final List<Note> voice2;
    private final Bar bar;

    Measure(int index, List<Note> notes, List<Note> voice2, Bar bar) {
        this.index = index;
        this.notes = notes;
        this.voice2 = voice2;
        this.bar = bar;
    }

    /** Номер такту з нуля, однаковий у всіх партіях. */
    public int index() {
        return index;
    }

    public List<Note> notes() {
        return Collections.unmodifiableList(notes);
    }

    /** Другий голос без пауз; порожньо — голос один. */
    public List<Note> voice2() {
        return voice2 == null ? Collections.<Note>emptyList() : Collections.unmodifiableList(voice2);
    }

    /** Позначки такту або null. */
    public Bar bar() {
        return bar;
    }

    /** Сума тривалостей першого голосу в чвертках. */
    public double length() {
        double sum = 0;
        for (Note n : notes) sum += n.duration;
        return sum;
    }
}
