package ua.museclass.musicxml;

/**
 * Файл не вдалося прийняти як партитуру. Повідомлення показується користувачу,
 * тому воно українською і без технічних подробиць парсера.
 */
public class MusicXmlException extends RuntimeException {
    public MusicXmlException(String message) {
        super(message);
    }

    public MusicXmlException(String message, Throwable cause) {
        super(message, cause);
    }
}
