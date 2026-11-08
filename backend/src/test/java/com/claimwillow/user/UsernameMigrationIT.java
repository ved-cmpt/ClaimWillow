package com.claimwillow.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;
import java.util.UUID;

import javax.sql.DataSource;

import com.claimwillow.support.IntegrationTest;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

/**
 * Runs V6/V7 against a database that already holds email-only accounts (as the hosted demo did)
 * in a separate scratch database on the test container.
 */
@IntegrationTest
class UsernameMigrationIT {

    private static final String DB = "cw_username_migration";

    @Autowired
    DataSource appDataSource;

    private JdbcTemplate admin;
    private DriverManagerDataSource scratch;
    private JdbcTemplate jdbc;

    @BeforeEach
    void createScratchDatabase() {
        HikariDataSource hikari = (HikariDataSource) appDataSource;
        admin = new JdbcTemplate(appDataSource);
        admin.execute("DROP DATABASE IF EXISTS " + DB);
        admin.execute("CREATE DATABASE " + DB);
        String url = hikari.getJdbcUrl().replaceFirst("/[^/?]+(\\?|$)", "/" + DB + "$1");
        scratch = new DriverManagerDataSource(url, hikari.getUsername(), hikari.getPassword());
        jdbc = new JdbcTemplate(scratch);
    }

    @AfterEach
    void dropScratchDatabase() {
        admin.execute("DROP DATABASE IF EXISTS " + DB + " WITH (FORCE)");
    }

    private void migrate(String target) {
        Flyway.configure().dataSource(scratch).locations("classpath:db/migration").target(target).load().migrate();
    }

    private UUID user(String email, String name, String role) {
        UUID id = UUID.randomUUID();
        jdbc.update("insert into users (id, email, password_hash, display_name, role, created_at, updated_at) "
                + "values (?, ?, '$2a$04$x', ?, ?, now(), now())", id, email, name, role);
        return id;
    }

    private Map<String, String> usernamesByEmail() {
        Map<String, String> result = new java.util.HashMap<>();
        jdbc.query("select email, username from users",
                rs -> {
                    result.put(rs.getString(1), rs.getString(2));
                });
        return result;
    }

    @Test
    void existingAccountsGetUniqueUsernamesAndElenaIsFoldedIntoMarcus() {
        migrate("5");
        user("priya@example.com", "Priya Shah", "POLICYHOLDER");
        UUID marcus = user("marcus@example.com", "Marcus Bell", "POLICYHOLDER");
        UUID elena = user("elena@example.com", "Elena Ruiz", "POLICYHOLDER");
        user("dana.adjuster@example.com", "Dana Okafor", "ADJUSTER");
        user("sam.adjuster@example.com", "Sam Lindqvist", "ADJUSTER");
        user("admin@example.com", "Robin Hale", "ADMIN");
        user("priya@other.example", "Another Priya", "POLICYHOLDER");
        user("jo@example.org", "Jo", "POLICYHOLDER");
        user(".odd+name@example.org", "Odd", "POLICYHOLDER");

        UUID policy = UUID.randomUUID();
        jdbc.update("insert into policies (id, policy_number, policyholder_user_id, status, product_name, "
                + "insured_address, effective_date, expiration_date, coverage_limit, deductible, created_at) "
                + "values (?, 'HC-120904', ?, 'ACTIVE', 'Home Contents Plus', '77 Cedar Crescent', current_date, "
                + "current_date + 365, 75000, 250, now())", policy, elena);
        UUID claim = UUID.randomUUID();
        jdbc.update("insert into claims (id, owner_user_id, policy_id, status, created_at, updated_at) "
                + "values (?, ?, ?, 'DRAFT', now(), now())", claim, elena, policy);
        jdbc.update("insert into claim_events (id, claim_id, actor_user_id, event_type, summary, occurred_at) "
                + "values (?, ?, ?, 'DRAFT_CREATED', 'Draft created', now())", UUID.randomUUID(), claim, elena);
        jdbc.update("insert into claim_notes (id, claim_id, author_user_id, note_type, body, created_at) "
                + "values (?, ?, ?, 'POLICYHOLDER_RESPONSE', 'Photos attached', now())",
                UUID.randomUUID(), claim, elena);
        jdbc.update("insert into claim_attachments (id, claim_id, uploaded_by_user_id, original_filename, "
                + "detected_content_type, storage_key, size_bytes, created_at) "
                + "values (?, ?, ?, 'a.pdf', 'application/pdf', 'k1', 10, now())", UUID.randomUUID(), claim, elena);

        migrate("latest");

        Map<String, String> names = usernamesByEmail();
        assertThat(names).containsEntry("priya@example.com", "priya")
                .containsEntry("marcus@example.com", "marcus")
                .containsEntry("dana.adjuster@example.com", "dana")
                .containsEntry("sam.adjuster@example.com", "sam")
                .containsEntry("admin@example.com", "admin")
                .containsEntry("jo@example.org", "user-jo")
                .containsEntry(".odd+name@example.org", "odd-name")
                .doesNotContainKey("elena@example.com");
        assertThat(names.get("priya@other.example")).startsWith("priya-").hasSize("priya-".length() + 8);
        assertThat(names.values()).doesNotHaveDuplicates();

        assertThat(jdbc.queryForObject("select owner_user_id from claims where id = ?", UUID.class, claim))
                .isEqualTo(marcus);
        assertThat(jdbc.queryForObject("select policyholder_user_id from policies where id = ?", UUID.class,
                policy)).isEqualTo(marcus);
        assertThat(jdbc.queryForObject("select count(*) from claim_events where actor_user_id = ?", Integer.class,
                marcus)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from claim_notes where author_user_id = ?", Integer.class,
                marcus)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from claim_attachments where uploaded_by_user_id = ?",
                Integer.class, marcus)).isEqualTo(1);

        // The append-only guards are back in force after the migration.
        assertThatThrownBy(() -> jdbc.update("update claim_events set summary = 'x'"))
                .hasMessageContaining("append-only");
        assertThatThrownBy(() -> jdbc.update("delete from claim_notes")).hasMessageContaining("append-only");
        // Usernames are unique regardless of case: upper case is refused, lower case duplicates clash.
        assertThatThrownBy(() -> jdbc.update("update users set username = 'PRIYA' where email = 'jo@example.org'"))
                .hasMessageContaining("ck_users_username_format");
        assertThatThrownBy(() -> jdbc.update("update users set username = 'priya' where email = 'jo@example.org'"))
                .hasMessageContaining("uq_users_username");
    }

    @Test
    void emptyDatabaseMigratesCleanly() {
        migrate("latest");
        assertThat(jdbc.queryForObject("select count(*) from users", Integer.class)).isZero();
    }
}
