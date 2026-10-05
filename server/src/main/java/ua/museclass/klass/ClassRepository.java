package ua.museclass.klass;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class ClassRepository {

    /** code заповнений тільки для викладача: учням код не віддаємо. */
    public record ClassRow(UUID id, String code, String name, UUID teacherId, String teacherName,
                           String role, int students, Instant createdAt) {}

    public record MemberRow(UUID userId, String displayName, Instant joinedAt) {}

    static final String INSERT_CLASS = """
            insert into classes (code, name, teacher_id)
            values (:code, :name, :teacher)
            on conflict (code) do nothing
            returning id
            """;

    static final String FIND_ID_BY_CODE = """
            select id from classes where code = :code
            """;

    // Роль того, хто питає: teacher, student або null (чужий клас).
    // Порожній результат — класу не існує.
    static final String ROLE_IN_CLASS = """
            select case
                     when c.teacher_id = :me then 'teacher'
                     when exists (select 1 from class_members m
                                  where m.class_id = c.id and m.user_id = :me) then 'student'
                   end as role
            from classes c
            where c.id = :id
            """;

    static final String CLASS_VIEW = """
            select c.id,
                   case when c.teacher_id = :me then c.code end as code,
                   c.name, c.teacher_id, u.display_name as teacher_name,
                   case when c.teacher_id = :me then 'teacher' else 'student' end as role,
                   (select count(*) from class_members m2 where m2.class_id = c.id) as students,
                   c.created_at
            from classes c
            join users u on u.id = c.teacher_id
            where c.id = :id
            """;

    static final String MY_CLASSES = """
            select c.id,
                   case when c.teacher_id = :me then c.code end as code,
                   c.name, c.teacher_id, u.display_name as teacher_name,
                   case when c.teacher_id = :me then 'teacher' else 'student' end as role,
                   (select count(*) from class_members m2 where m2.class_id = c.id) as students,
                   c.created_at
            from classes c
            join users u on u.id = c.teacher_id
            where c.teacher_id = :me
               or exists (select 1 from class_members m where m.class_id = c.id and m.user_id = :me)
            order by c.created_at, c.id
            """;

    static final String TEACHER_OF = """
            select teacher_id from classes where id = :id
            """;

    static final String ADD_MEMBER = """
            insert into class_members (class_id, user_id) values (:id, :user)
            on conflict do nothing
            """;

    static final String REMOVE_MEMBER = """
            delete from class_members where class_id = :id and user_id = :user
            """;

    static final String MEMBERS = """
            select u.id as user_id, u.display_name, m.joined_at
            from class_members m
            join users u on u.id = m.user_id
            where m.class_id = :id
            order by u.display_name, u.id
            """;

    static final String UPDATE_CODE = """
            update classes set code = :code
            where id = :id
              and not exists (select 1 from classes other where other.code = :code)
            """;

    static final String RENAME = """
            update classes set name = :name where id = :id
            """;

    static final String DELETE_CLASS = """
            delete from classes where id = :id
            """;

    private static final RowMapper<ClassRow> CLASS = (rs, i) -> new ClassRow(
            rs.getObject("id", UUID.class),
            rs.getString("code"),
            rs.getString("name"),
            rs.getObject("teacher_id", UUID.class),
            rs.getString("teacher_name"),
            rs.getString("role"),
            rs.getInt("students"),
            rs.getTimestamp("created_at").toInstant());

    private static final RowMapper<MemberRow> MEMBER = (rs, i) -> new MemberRow(
            rs.getObject("user_id", UUID.class),
            rs.getString("display_name"),
            rs.getTimestamp("joined_at").toInstant());

    private final JdbcClient jdbc;

    public ClassRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * empty — такий код уже зайнятий, сервіс генерує інший.
     * on conflict замість винятку: так вставка безпечна і всередині транзакції.
     */
    public Optional<UUID> insert(String code, String name, UUID teacher) {
        return jdbc.sql(INSERT_CLASS)
                .param("code", code).param("name", name).param("teacher", teacher)
                .query(UUID.class).optional();
    }

    public Optional<UUID> findIdByCode(String code) {
        return jdbc.sql(FIND_ID_BY_CODE).param("code", code).query(UUID.class).optional();
    }

    /** empty — класу немає; "none" — клас є, але ти в ньому ніхто. */
    public Optional<String> roleOf(UUID classId, UUID me) {
        return jdbc.sql(ROLE_IN_CLASS).param("id", classId).param("me", me)
                .query((rs, i) -> {
                    String r = rs.getString("role");
                    return r == null ? "none" : r;
                })
                .optional();
    }

    public Optional<ClassRow> view(UUID classId, UUID me) {
        return jdbc.sql(CLASS_VIEW).param("id", classId).param("me", me).query(CLASS).optional();
    }

    public List<ClassRow> mine(UUID me) {
        return jdbc.sql(MY_CLASSES).param("me", me).query(CLASS).list();
    }

    public Optional<UUID> teacherOf(UUID classId) {
        return jdbc.sql(TEACHER_OF).param("id", classId).query(UUID.class).optional();
    }

    public void addMember(UUID classId, UUID user) {
        jdbc.sql(ADD_MEMBER).param("id", classId).param("user", user).update();
    }

    public int removeMember(UUID classId, UUID user) {
        return jdbc.sql(REMOVE_MEMBER).param("id", classId).param("user", user).update();
    }

    public List<MemberRow> members(UUID classId) {
        return jdbc.sql(MEMBERS).param("id", classId).query(MEMBER).list();
    }

    /** false — код зайнятий. У рідкісній гонці може кинути DuplicateKeyException. */
    public boolean updateCode(UUID classId, String code) {
        return jdbc.sql(UPDATE_CODE).param("id", classId).param("code", code).update() == 1;
    }

    public void rename(UUID classId, String name) {
        jdbc.sql(RENAME).param("id", classId).param("name", name).update();
    }

    public void delete(UUID classId) {
        jdbc.sql(DELETE_CLASS).param("id", classId).update();
    }
}
