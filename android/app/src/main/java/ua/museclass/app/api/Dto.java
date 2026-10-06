package ua.museclass.app.api;

import java.util.List;

/**
 * Відповіді сервера як є (docs/api.md). Поля названі так само, як у JSON,
 * Gson заповнює їх сам. Час — рядок ISO-8601, парсити через Instant.
 */
public final class Dto {
    private Dto() {
    }

    public static final class Auth {
        public String token;
        public String expiresAt;
        public String userId;
        public String displayName;
    }

    public static final class Profile {
        public String id;
        public String email;
        public String displayName;
        public List<String> instruments;
        public String createdAt;
    }

    public static final class ClassInfo {
        public String id;
        /** тільки викладачу; учню null */
        public String code;
        public String name;
        public String teacherId;
        public String teacherName;
        /** teacher або student */
        public String role;
        public int students;
        public String createdAt;
    }

    /** Картка партитури в усіх списках. */
    public static final class Summary {
        public String id;
        public String title;
        public String composer;
        public String arranger;
        public String kind;
        public String visibility;
        public int measures;
        public String updatedAt;
        public String ownerId;
        public String ownerName;
        public List<String> instruments;
        public boolean fits;
    }

    public static final class LibraryEntry {
        public String classId;
        public String className;
        public String assignedAt;
        public Summary score;
    }

    public static final class ScoreInfo {
        public String id;
        public String title;
        public String composer;
        public String arranger;
        public String kind;
        public String rights;
        public String visibility;
        /** musicxml або mxl */
        public String format;
        public long sizeBytes;
        public int measures;
        public String sha256;
        public String ownerName;
        public boolean saved;
    }

    public static final class PartInfo {
        public int position;
        public String partId;
        public String name;
        /** код інструмента або null, якщо сервер не впізнав */
        public String instrument;
    }

    public static final class ScoreView {
        public ScoreInfo score;
        public List<PartInfo> parts;
        public boolean canEdit;
    }
}
