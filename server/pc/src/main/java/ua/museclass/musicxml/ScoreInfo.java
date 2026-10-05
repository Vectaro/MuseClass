package ua.museclass.musicxml;

import java.util.List;

/** Метадані партитури, витягнуті з MusicXML при завантаженні. */
public record ScoreInfo(
        String format,          // "musicxml" або "mxl"
        String title,           // може бути null, якщо у файлі немає назви
        String composer,
        String arranger,
        List<PartInfo> parts,
        int measures) {

    public record PartInfo(
            String id,
            String name,
            String instrumentName,
            Integer midiProgram,    // 1..128, як у MusicXML
            Integer midiChannel,    // 1..16
            String instrument) {    // код інструмента MuseClass або null
    }
}
