package ua.museclass.user;

import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public class UserRepository {

    public record UserRow(UUID id, String email, String passwordHash, String displayName, Instant createdAt) {}

    static final String INSERT_USER = """
            insert into users (email, password_hash, display_name)
            values (:email, :hash, :name)
            returning id
            """;

    static final String FIND_BY_EMAIL = """
            select id, email, password_hash, display_name, created_at
            from users
            where lower(email) = lower(:email)
            """;

    static final String FIND_BY_ID = """
            select id, email, password_hash, display_name, created_at
            from users
            where id = :id
            """;

    static final String UPDATE_NAME = """
            update users set display_name = :name where id = :id
            """;

    static final String INSTRUMENTS_OF = """
            select instrument from user_instruments where user_id = :id order by instrument
            """;

    static final String CLEAR_INSTRUMENTS = """
            delete from user_instruments where user_id = :id
            """;

    static final String ADD_INSTRUMENT = """
            insert into user_instruments (user_id, instrument) values (:id, :instrument)
            on conflict do nothing
            """;

    private static final RowMapper<UserRow> USER = (rs, i) -> new UserRow(
            rs.getObject("id", UUID.class),
            rs.getString("email"),
            rs.getString("password_hash"),
            rs.getString("display_name"),
            rs.getTimestamp("created_at").toInstant());

    private final JdbcClient jdbc;

    public UserRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    /** Кидає DuplicateKeyException, якщо пошта вже зайнята. */
    public UUID insert(String email, String passwordHash, String displayName) {
        return jdbc.sql(INSERT_USER)
                .param("email", email)
                .param("hash", passwordHash)
                .param("name", displayName)
                .query(UUID.class)
                .single();
    }

    public Optional<UserRow> findByEmail(String email) {
        return jdbc.sql(FIND_BY_EMAIL).param("email", email).query(USER).optional();
    }

    public Optional<UserRow> findById(UUID id) {
        return jdbc.sql(FIND_BY_ID).param("id", id).query(USER).optional();
    }

    public void updateName(UUID id, String name) {
        jdbc.sql(UPDATE_NAME).param("id", id).param("name", name).update();
    }

    public List<String> instruments(UUID id) {
        return jdbc.sql(INSTRUMENTS_OF).param("id", id).query(String.class).list();
    }

    public void replaceInstruments(UUID id, List<String> instruments) {
        jdbc.sql(CLEAR_INSTRUMENTS).param("id", id).update();
        for (String code : instruments) {
            jdbc.sql(ADD_INSTRUMENT).param("id", id).param("instrument", code).update();
        }
    }
}
