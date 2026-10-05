package ua.museclass.score;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;
import ua.museclass.musicxml.ScoreInfo;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ScoreRepository {

    // ------------------------------------------------------------ рядки

    /** Картка партитури у списках. fits — є партія під один з твоїх інструментів. */
    public record Summary(UUID id, String title, String composer, String arranger, String kind, String visibility,
                          int measures, Instant updatedAt, UUID ownerId, String ownerName,
                          List<String> instruments, boolean fits) {}

    public record LibraryEntry(UUID classId, String className, Instant assignedAt, Summary score) {}

    public record Detail(UUID id, String title, String composer, String arranger, String kind, String rights,
                         String visibility, String format, int sizeBytes, int measures, String sha256,
                         Instant createdAt, Instant updatedAt, UUID ownerId, String ownerName, boolean saved) {}

    public record PartRow(int position, String partId, String name, String instrument) {}

    public record FileRow(String title, String format, byte[] content, String sha256) {}

    public record Ownership(UUID ownerId, String visibility, String rights) {}

    public record NewScore(UUID owner, String title, String composer, String arranger, String kind, String rights,
                           String visibility, String format, byte[] content, String sha256, int measures) {}

    // ------------------------------------------------------------ SQL

    static final String INSERT_SCORE = """
            insert into scores (owner_id, title, composer, arranger, kind, rights, visibility,
                                file_format, content, content_sha256, size_bytes, measures)
            values (:owner, :title, :composer, :arranger, :kind, :rights, :visibility,
                    :format, :content, :sha, :size, :measures)
            returning id
            """;

    static final String INSERT_PART = """
            insert into score_parts (score_id, position, part_id, name, instrument, midi_program)
            values (:score, :position, :partId, :name, :instrument, :midi)
            """;

    static final String DELETE_PARTS = """
            delete from score_parts where score_id = :score
            """;

    static final String DETAIL = """
            select s.id, s.title, s.composer, s.arranger, s.kind, s.rights, s.visibility, s.file_format,
                   s.size_bytes, s.measures, s.content_sha256, s.created_at, s.updated_at,
                   s.owner_id, u.display_name as owner_name,
                   exists (select 1 from saved_scores ss where ss.user_id = :me and ss.score_id = s.id) as saved
            from scores s
            join users u on u.id = s.owner_id
            where s.id = :id and score_readable(:me, s.id)
            """;

    static final String PARTS_OF = """
            select position, part_id, name, instrument
            from score_parts
            where score_id = :id
            order by position
            """;

    static final String FILE_OF = """
            select title, file_format, content, content_sha256
            from scores
            where id = :id and score_readable(:me, id)
            """;

    static final String IS_READABLE = """
            select score_readable(:me, :id)
            """;

    static final String OWNERSHIP = """
            select owner_id, visibility, rights from scores where id = :id
            """;

    static final String UPDATE_META = """
            update scores
            set title = :title, composer = :composer, arranger = :arranger,
                kind = :kind, rights = :rights, visibility = :visibility, updated_at = now()
            where id = :id and owner_id = :me
            """;

    static final String REPLACE_FILE = """
            update scores
            set file_format = :format, content = :content, content_sha256 = :sha,
                size_bytes = :size, measures = :measures, updated_at = now()
            where id = :id and owner_id = :me
            """;

    static final String DELETE_SCORE = """
            delete from scores where id = :id and owner_id = :me
            """;

    static final String MY_SCORES = """
            select s.id, s.title, s.composer, s.arranger, s.kind, s.visibility, s.measures, s.updated_at,
                   s.owner_id, u.display_name as owner_name,
                   coalesce((select string_agg(distinct p.instrument, ',' order by p.instrument)
                             from score_parts p where p.score_id = s.id), '') as instruments,
                   exists (select 1 from score_parts p
                           join user_instruments ui on ui.instrument = p.instrument and ui.user_id = :me
                           where p.score_id = s.id) as fits
            from scores s
            join users u on u.id = s.owner_id
            where s.owner_id = :me
            order by s.updated_at desc, s.id
            """;

    // Публічний каталог. Інструмент — м'який сигнал: піднімає, але не ховає решту.
    // Параметри-null типізуємо явно, інакше Postgres не виведе їхній тип.
    static final String CATALOG = """
            select s.id, s.title, s.composer, s.arranger, s.kind, s.visibility, s.measures, s.updated_at,
                   s.owner_id, u.display_name as owner_name,
                   coalesce((select string_agg(distinct p.instrument, ',' order by p.instrument)
                             from score_parts p where p.score_id = s.id), '') as instruments,
                   exists (select 1 from score_parts p
                           join user_instruments ui on ui.instrument = p.instrument and ui.user_id = :me
                           where p.score_id = s.id) as fits
            from scores s
            join users u on u.id = s.owner_id
            where s.visibility = 'public'
              and (cast(:pattern as text) is null
                   or s.title ilike cast(:pattern as text) escape '\\'
                   or s.composer ilike cast(:pattern as text) escape '\\'
                   or s.arranger ilike cast(:pattern as text) escape '\\')
              and (cast(:kind as text) is null or s.kind = cast(:kind as text))
            order by fits desc, lower(s.title), s.id
            limit :limit offset :offset
            """;

    static final String CLASS_LIBRARY = """
            select c.id as class_id, c.name as class_name, cs.assigned_at,
                   s.id, s.title, s.composer, s.arranger, s.kind, s.visibility, s.measures, s.updated_at,
                   s.owner_id, u.display_name as owner_name,
                   coalesce((select string_agg(distinct p.instrument, ',' order by p.instrument)
                             from score_parts p where p.score_id = s.id), '') as instruments,
                   exists (select 1 from score_parts p
                           join user_instruments ui on ui.instrument = p.instrument and ui.user_id = :me
                           where p.score_id = s.id) as fits
            from class_scores cs
            join classes c on c.id = cs.class_id
            join scores s on s.id = cs.score_id
            join users u on u.id = s.owner_id
            where cs.class_id = :classId and score_readable(:me, s.id)
            order by cs.assigned_at desc, s.id
            """;

    // Усе, що видали в усіх моїх класах (як учню, так і як викладачу).
    static final String MY_LIBRARY = """
            select c.id as class_id, c.name as class_name, cs.assigned_at,
                   s.id, s.title, s.composer, s.arranger, s.kind, s.visibility, s.measures, s.updated_at,
                   s.owner_id, u.display_name as owner_name,
                   coalesce((select string_agg(distinct p.instrument, ',' order by p.instrument)
                             from score_parts p where p.score_id = s.id), '') as instruments,
                   exists (select 1 from score_parts p
                           join user_instruments ui on ui.instrument = p.instrument and ui.user_id = :me
                           where p.score_id = s.id) as fits
            from class_scores cs
            join classes c on c.id = cs.class_id
            join scores s on s.id = cs.score_id
            join users u on u.id = s.owner_id
            where (c.teacher_id = :me
                   or exists (select 1 from class_members m where m.class_id = c.id and m.user_id = :me))
              and score_readable(:me, s.id)
            order by cs.assigned_at desc, c.id, s.id
            """;

    static final String ASSIGN = """
            insert into class_scores (class_id, score_id, assigned_by)
            values (:classId, :scoreId, :me)
            on conflict do nothing
            """;

    static final String PROMOTE_PRIVATE = """
            update scores set visibility = 'class', updated_at = now()
            where id = :id and owner_id = :me and visibility = 'private'
            """;

    static final String UNASSIGN = """
            delete from class_scores where class_id = :classId and score_id = :scoreId
            """;

    static final String SAVE = """
            insert into saved_scores (user_id, score_id) values (:me, :id)
            on conflict do nothing
            """;

    static final String UNSAVE = """
            delete from saved_scores where user_id = :me and score_id = :id
            """;

    // Збережене показуємо тільки поки воно читається: якщо автор зробив
    // партитуру приватною, вона зникає зі збережених, але не губиться назавжди.
    static final String SAVED = """
            select s.id, s.title, s.composer, s.arranger, s.kind, s.visibility, s.measures, s.updated_at,
                   s.owner_id, u.display_name as owner_name,
                   coalesce((select string_agg(distinct p.instrument, ',' order by p.instrument)
                             from score_parts p where p.score_id = s.id), '') as instruments,
                   exists (select 1 from score_parts p
                           join user_instruments ui on ui.instrument = p.instrument and ui.user_id = :me
                           where p.score_id = s.id) as fits
            from saved_scores ss
            join scores s on s.id = ss.score_id
            join users u on u.id = s.owner_id
            where ss.user_id = :me and score_readable(:me, s.id)
            order by ss.saved_at desc, s.id
            """;

    // ------------------------------------------------------------ мапери

    private static Summary summary(ResultSet rs) throws SQLException {
        String instr = rs.getString("instruments");
        return new Summary(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getString("composer"),
                rs.getString("arranger"),
                rs.getString("kind"),
                rs.getString("visibility"),
                rs.getInt("measures"),
                rs.getTimestamp("updated_at").toInstant(),
                rs.getObject("owner_id", UUID.class),
                rs.getString("owner_name"),
                instr == null || instr.isEmpty() ? List.of() : Arrays.asList(instr.split(",")),
                rs.getBoolean("fits"));
    }

    private static final RowMapper<Summary> SUMMARY = (rs, i) -> summary(rs);

    private static final RowMapper<LibraryEntry> LIBRARY = (rs, i) -> new LibraryEntry(
            rs.getObject("class_id", UUID.class),
            rs.getString("class_name"),
            rs.getTimestamp("assigned_at").toInstant(),
            summary(rs));

    private static final RowMapper<Detail> DETAIL_ROW = (rs, i) -> new Detail(
            rs.getObject("id", UUID.class),
            rs.getString("title"),
            rs.getString("composer"),
            rs.getString("arranger"),
            rs.getString("kind"),
            rs.getString("rights"),
            rs.getString("visibility"),
            rs.getString("file_format"),
            rs.getInt("size_bytes"),
            rs.getInt("measures"),
            rs.getString("content_sha256"),
            rs.getTimestamp("created_at").toInstant(),
            rs.getTimestamp("updated_at").toInstant(),
            rs.getObject("owner_id", UUID.class),
            rs.getString("owner_name"),
            rs.getBoolean("saved"));

    private static final RowMapper<PartRow> PART = (rs, i) -> new PartRow(
            rs.getInt("position"), rs.getString("part_id"), rs.getString("name"), rs.getString("instrument"));

    // ------------------------------------------------------------ методи

    private final JdbcClient jdbc;

    public ScoreRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public UUID insert(NewScore s) {
        return jdbc.sql(INSERT_SCORE)
                .param("owner", s.owner())
                .param("title", s.title())
                .param("composer", s.composer())
                .param("arranger", s.arranger())
                .param("kind", s.kind())
                .param("rights", s.rights())
                .param("visibility", s.visibility())
                .param("format", s.format())
                .param("content", s.content())
                .param("sha", s.sha256())
                .param("size", s.content().length)
                .param("measures", s.measures())
                .query(UUID.class)
                .single();
    }

    public void replaceParts(UUID scoreId, List<ScoreInfo.PartInfo> parts) {
        jdbc.sql(DELETE_PARTS).param("score", scoreId).update();
        int pos = 0;
        for (ScoreInfo.PartInfo p : parts) {
            jdbc.sql(INSERT_PART)
                    .param("score", scoreId)
                    .param("position", pos++)
                    .param("partId", p.id())
                    .param("name", p.name())
                    .param("instrument", p.instrument())
                    .param("midi", p.midiProgram())
                    .update();
        }
    }

    public Optional<Detail> detail(UUID id, UUID me) {
        return jdbc.sql(DETAIL).param("id", id).param("me", me).query(DETAIL_ROW).optional();
    }

    public List<PartRow> parts(UUID id) {
        return jdbc.sql(PARTS_OF).param("id", id).query(PART).list();
    }

    public Optional<FileRow> file(UUID id, UUID me) {
        return jdbc.sql(FILE_OF).param("id", id).param("me", me)
                .query((rs, i) -> new FileRow(rs.getString("title"), rs.getString("file_format"),
                        rs.getBytes("content"), rs.getString("content_sha256")))
                .optional();
    }

    public boolean readable(UUID id, UUID me) {
        return Boolean.TRUE.equals(jdbc.sql(IS_READABLE).param("id", id).param("me", me)
                .query(Boolean.class).single());
    }

    public Optional<Ownership> ownership(UUID id) {
        return jdbc.sql(OWNERSHIP).param("id", id)
                .query((rs, i) -> new Ownership(rs.getObject("owner_id", UUID.class),
                        rs.getString("visibility"), rs.getString("rights")))
                .optional();
    }

    public int updateMeta(UUID id, UUID me, String title, String composer, String arranger,
                          String kind, String rights, String visibility) {
        return jdbc.sql(UPDATE_META)
                .param("id", id).param("me", me)
                .param("title", title).param("composer", composer).param("arranger", arranger)
                .param("kind", kind).param("rights", rights).param("visibility", visibility)
                .update();
    }

    public int replaceFile(UUID id, UUID me, String format, byte[] content, String sha, int measures) {
        return jdbc.sql(REPLACE_FILE)
                .param("id", id).param("me", me)
                .param("format", format).param("content", content).param("sha", sha)
                .param("size", content.length).param("measures", measures)
                .update();
    }

    public int delete(UUID id, UUID me) {
        return jdbc.sql(DELETE_SCORE).param("id", id).param("me", me).update();
    }

    public List<Summary> mine(UUID me) {
        return jdbc.sql(MY_SCORES).param("me", me).query(SUMMARY).list();
    }

    public List<Summary> catalog(UUID me, String pattern, String kind, int limit, int offset) {
        return jdbc.sql(CATALOG)
                .param("me", me).param("pattern", pattern).param("kind", kind)
                .param("limit", limit).param("offset", offset)
                .query(SUMMARY).list();
    }

    public List<LibraryEntry> classLibrary(UUID classId, UUID me) {
        return jdbc.sql(CLASS_LIBRARY).param("classId", classId).param("me", me).query(LIBRARY).list();
    }

    public List<LibraryEntry> myLibrary(UUID me) {
        return jdbc.sql(MY_LIBRARY).param("me", me).query(LIBRARY).list();
    }

    public void assign(UUID classId, UUID scoreId, UUID me) {
        jdbc.sql(ASSIGN).param("classId", classId).param("scoreId", scoreId).param("me", me).update();
    }

    public void promotePrivate(UUID scoreId, UUID me) {
        jdbc.sql(PROMOTE_PRIVATE).param("id", scoreId).param("me", me).update();
    }

    public int unassign(UUID classId, UUID scoreId) {
        return jdbc.sql(UNASSIGN).param("classId", classId).param("scoreId", scoreId).update();
    }

    public void save(UUID scoreId, UUID me) {
        jdbc.sql(SAVE).param("id", scoreId).param("me", me).update();
    }

    public void unsave(UUID scoreId, UUID me) {
        jdbc.sql(UNSAVE).param("id", scoreId).param("me", me).update();
    }

    public List<Summary> saved(UUID me) {
        return jdbc.sql(SAVED).param("me", me).query(SUMMARY).list();
    }
}
