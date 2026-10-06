package ua.museclass.musicxml;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Позначки такту: реприза, вольта, написи, динаміка, вилка, зміна розміру чи
 * тональності. Спільні для всіх станів партії.
 */
public final class Bar {
    String repeat;
    final List<Integer> ending = new ArrayList<>();
    Boolean endingStop;
    String text;
    String jump;
    String mark;
    String dynamics;
    String hairpin;
    String hairpinKind;
    Meter meter;
    Integer fifths;

    /** start / end / both або null. */
    public String repeat() {
        return repeat;
    }

    /** Номери вольти; порожньо — не вольта. */
    public List<Integer> ending() {
        return Collections.unmodifiableList(ending);
    }

    /** false — вольта відкрита справа (type="start" без stop у цьому такті). */
    public Boolean endingStop() {
        return endingStop;
    }

    /** Текст над станом: темп, позначки, ♩=120. */
    public String text() {
        return text;
    }

    /** D.C., D.S., Fine, Coda, Segno. */
    public String jump() {
        return jump;
    }

    /** Репетиційна буква. */
    public String mark() {
        return mark;
    }

    /** p, mf, ff… */
    public String dynamics() {
        return dynamics;
    }

    /** start / stop або null. */
    public String hairpin() {
        return hairpin;
    }

    /** cresc / dim. */
    public String hairpinKind() {
        return hairpinKind;
    }

    /** Новий розмір з цього такту або null. */
    public Meter meter() {
        return meter;
    }

    /** Нова тональність з цього такту або null. */
    public Integer fifths() {
        return fifths;
    }

    boolean isEmpty() {
        return repeat == null && ending.isEmpty() && endingStop == null && text == null && jump == null
                && mark == null && dynamics == null && hairpin == null && meter == null && fifths == null;
    }
}
