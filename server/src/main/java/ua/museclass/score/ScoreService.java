package ua.museclass.score;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ua.museclass.common.ApiException;
import ua.museclass.klass.ClassService;
import ua.museclass.musicxml.MusicXmlInspector;
import ua.museclass.musicxml.ScoreInfo;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ScoreService {

    static final Set<String> KINDS = Set.of("classical", "folk", "cover", "technique", "other");
    static final Set<String> RIGHTS = Set.of("public_domain", "folk", "arrangement", "original", "unknown");
    static final Set<String> VISIBILITY = Set.of("public", "class", "private");
    /** Межа, яку тримаємо, поки немає ліцензії: у публічний каталог — тільки це. */
    static final Set<String> PUBLIC_RIGHTS = Set.of("public_domain", "folk");

    public record Metadata(String title, String composer, String arranger, String kind, String rights,
                           String visibility) {}

    public record ScoreView(ScoreRepository.Detail score, List<ScoreRepository.PartRow> parts, boolean canEdit) {}

    private final ScoreRepository scores;
    private final ClassService classes;

    public ScoreService(ScoreRepository scores, ClassService classes) {
        this.scores = scores;
        this.classes = classes;
    }

    @Transactional
    public ScoreView upload(UUID me, byte[] data, String fileName, Metadata meta) {
        ScoreInfo info = MusicXmlInspector.inspect(data);
        String title = firstNonBlank(meta.title(), info.title(), baseName(fileName), "Без назви");
        String kind = pick(meta.kind(), KINDS, "other", "Невідомий жанр");
        String rights = pick(meta.rights(), RIGHTS, "unknown", "Невідомий тип прав");
        String visibility = pick(meta.visibility(), VISIBILITY, "private", "Невідомий рівень доступу");
        checkPublic(visibility, rights);

        UUID id = scores.insert(new ScoreRepository.NewScore(me, limit(title, 200),
                limit(firstNonBlank(meta.composer(), info.composer()), 200),
                limit(firstNonBlank(meta.arranger(), info.arranger()), 200),
                kind, rights, visibility, info.format(), data, sha256(data), info.measures()));
        scores.replaceParts(id, info.parts());
        return view(me, id);
    }

    /** Нова версія файлу з редактора. Метадані лишаються, партії перераховуються. */
    @Transactional
    public ScoreView replaceFile(UUID me, UUID id, byte[] data) {
        requireOwner(me, id);
        ScoreInfo info = MusicXmlInspector.inspect(data);
        scores.replaceFile(id, me, info.format(), data, sha256(data), info.measures());
        scores.replaceParts(id, info.parts());
        return view(me, id);
    }

    public ScoreView view(UUID me, UUID id) {
        ScoreRepository.Detail d = scores.detail(id, me).orElseThrow(() -> ApiException.notFound("Партитуру"));
        return new ScoreView(d, scores.parts(id), d.ownerId().equals(me));
    }

    public ScoreRepository.FileRow file(UUID me, UUID id) {
        return scores.file(id, me).orElseThrow(() -> ApiException.notFound("Партитуру"));
    }

    /** Часткове оновлення: null у полі — не чіпати. Порожній рядок у composer/arranger — стерти. */
    @Transactional
    public ScoreView updateMeta(UUID me, UUID id, Metadata patch) {
        requireOwner(me, id);
        ScoreRepository.Detail cur = scores.detail(id, me).orElseThrow();
        String title = patch.title() != null ? patch.title().trim() : cur.title();
        if (title.isEmpty()) throw ApiException.badRequest("Назва не може бути порожньою.");
        String composer = patch.composer() != null ? blankToNull(patch.composer()) : cur.composer();
        String arranger = patch.arranger() != null ? blankToNull(patch.arranger()) : cur.arranger();
        String kind = patch.kind() != null ? pick(patch.kind(), KINDS, null, "Невідомий жанр") : cur.kind();
        String rights = patch.rights() != null ? pick(patch.rights(), RIGHTS, null, "Невідомий тип прав") : cur.rights();
        String visibility = patch.visibility() != null
                ? pick(patch.visibility(), VISIBILITY, null, "Невідомий рівень доступу") : cur.visibility();
        checkPublic(visibility, rights);
        scores.updateMeta(id, me, limit(title, 200), limit(composer, 200), limit(arranger, 200),
                kind, rights, visibility);
        return view(me, id);
    }

    public void delete(UUID me, UUID id) {
        requireOwner(me, id);
        scores.delete(id, me);
    }

    public List<ScoreRepository.Summary> mine(UUID me) {
        return scores.mine(me);
    }

    public List<ScoreRepository.Summary> catalog(UUID me, String query, String kind, int limit, int offset) {
        String k = kind == null || kind.isBlank() ? null : pick(kind, KINDS, null, "Невідомий жанр");
        String q = query == null ? null : query.trim();
        String pattern = q == null || q.isEmpty() ? null : "%" + escapeLike(q) + "%";
        int l = Math.max(1, Math.min(limit, 100));
        int o = Math.max(0, offset);
        return scores.catalog(me, pattern, k, l, o);
    }

    public List<ScoreRepository.LibraryEntry> classLibrary(UUID me, UUID classId) {
        classes.requireMember(me, classId);
        return scores.classLibrary(classId, me);
    }

    public List<ScoreRepository.LibraryEntry> myLibrary(UUID me) {
        return scores.myLibrary(me);
    }

    /**
     * Видати партитуру класу. Можна свою (приватна тоді стає класною — інакше
     * учні її не побачать) або будь-яку публічну з каталогу.
     */
    @Transactional
    public void assign(UUID me, UUID classId, UUID scoreId) {
        classes.requireTeacher(me, classId);
        ScoreRepository.Ownership o = scores.ownership(scoreId)
                .orElseThrow(() -> ApiException.notFound("Партитуру"));
        boolean mine = o.ownerId().equals(me);
        if (!mine && !"public".equals(o.visibility())) {
            // чужа класна чи приватна: не світимо, що вона існує
            throw ApiException.notFound("Партитуру");
        }
        if (mine) scores.promotePrivate(scoreId, me);
        scores.assign(classId, scoreId, me);
    }

    public void unassign(UUID me, UUID classId, UUID scoreId) {
        classes.requireTeacher(me, classId);
        if (scores.unassign(classId, scoreId) == 0) {
            throw ApiException.notFound("Партитуру в цьому класі");
        }
    }

    public void save(UUID me, UUID scoreId) {
        if (!scores.readable(scoreId, me)) throw ApiException.notFound("Партитуру");
        scores.save(scoreId, me);
    }

    public void unsave(UUID me, UUID scoreId) {
        scores.unsave(scoreId, me);
    }

    public List<ScoreRepository.Summary> saved(UUID me) {
        return scores.saved(me);
    }

    // ------------------------------------------------------------ допоміжне

    private void requireOwner(UUID me, UUID id) {
        ScoreRepository.Ownership o = scores.ownership(id).orElseThrow(() -> ApiException.notFound("Партитуру"));
        if (!o.ownerId().equals(me)) {
            if (scores.readable(id, me)) throw ApiException.forbidden("Змінювати партитуру може тільки автор.");
            throw ApiException.notFound("Партитуру");
        }
    }

    static void checkPublic(String visibility, String rights) {
        if ("public".equals(visibility) && !PUBLIC_RIGHTS.contains(rights)) {
            throw ApiException.badRequest(
                    "У публічний каталог — тільки public domain і народні твори. "
                            + "Кавери можна тримати приватними або видавати класу.");
        }
    }

    static String pick(String value, Set<String> allowed, String def, String error) {
        if (value == null || value.isBlank()) {
            if (def == null) throw ApiException.badRequest(error + ".");
            return def;
        }
        String v = value.trim().toLowerCase(Locale.ROOT);
        if (!allowed.contains(v)) throw ApiException.badRequest(error + ": " + value);
        return v;
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    static String sha256(byte[] data) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    static String baseName(String fileName) {
        if (fileName == null) return null;
        String n = fileName.replace('\\', '/');
        n = n.substring(n.lastIndexOf('/') + 1);
        int dot = n.lastIndexOf('.');
        return dot > 0 ? n.substring(0, dot) : n;
    }

    static String firstNonBlank(String... values) {
        for (String v : values) {
            if (v != null && !v.isBlank()) return v.trim();
        }
        return null;
    }

    static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    static String limit(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
