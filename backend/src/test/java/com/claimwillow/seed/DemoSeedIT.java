package com.claimwillow.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import com.claimwillow.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.TestPropertySource;

@IntegrationTest
@TestPropertySource(properties = "app.seed-demo-password=demo1234")
class DemoSeedIT {

    private static final String DEMO_USERS = "('priya','marcus','dana','sam','admin')";
    private static final String DEMO_HOLDERS = "('priya','marcus')";

    @Autowired
    DemoDataSeeder seeder;

    @Autowired
    JdbcTemplate jdbc;

    @Autowired
    PasswordEncoder passwordEncoder;

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    private String hashOf(String username) {
        return jdbc.queryForObject("select password_hash from users where username = ?", String.class, username);
    }

    @Test
    void seedIsIdempotentAndCoversSeveralStatuses() {
        seeder.seed();
        int users = count("select count(*) from users where username in " + DEMO_USERS);
        int claims = count("select count(*) from claims c join users u on u.id = c.owner_user_id "
                + "where u.username in " + DEMO_HOLDERS);
        int events = count("select count(*) from claim_events");
        seeder.seed();
        assertThat(users).isEqualTo(5);
        assertThat(DemoDataSeeder.demoUsernames()).containsExactly("priya", "marcus", "dana", "sam", "admin");
        assertThat(count("select count(*) from users where email = 'elena@example.com' or username = 'elena'"))
                .isZero();
        assertThat(count("select count(*) from claims c join users u on u.id = c.owner_user_id "
                + "where u.username in " + DEMO_HOLDERS)).isEqualTo(claims);
        assertThat(count("select count(*) from claim_events")).isEqualTo(events);
        assertThat(claims).isEqualTo(6);
        assertThat(count("select count(distinct c.status) from claims c join users u on u.id = c.owner_user_id "
                + "where u.username in " + DEMO_HOLDERS)).isGreaterThanOrEqualTo(5);
        assertThat(count("select count(*) from claims c join users u on u.id = c.owner_user_id "
                + "where u.username = 'marcus' and c.estimated_loss > c.snapshot_coverage_limit")).isEqualTo(1);
        for (String username : DemoDataSeeder.demoUsernames()) {
            assertThat(passwordEncoder.matches("demo1234", hashOf(username))).as(username).isTrue();
        }
    }

    @Test
    void existingDemoAccountsAreReconciledOnEverySeed() {
        seeder.seed();
        String priyaHash = hashOf("priya");
        jdbc.update("update users set username = 'priya-old', display_name = 'Someone Else', role = 'ADMIN', "
                + "enabled = false, password_hash = ? where email = 'priya@example.com'",
                passwordEncoder.encode("an-old-password"));
        jdbc.update("update users set display_name = 'Renamed' where username = 'dana'");
        String samHash = hashOf("sam");

        seeder.seed();

        List<String> priya = jdbc.queryForList("select display_name || '|' || role || '|' || enabled from users "
                + "where username = 'priya'", String.class);
        assertThat(priya).containsExactly("Priya Shah|POLICYHOLDER|true");
        assertThat(count("select count(*) from users where username = 'priya-old'")).isZero();
        assertThat(passwordEncoder.matches("demo1234", hashOf("priya"))).isTrue();
        assertThat(hashOf("priya")).isNotEqualTo(priyaHash);
        assertThat(jdbc.queryForObject("select display_name from users where username = 'dana'", String.class))
                .isEqualTo("Dana Okafor");
        // A password that already matches is not re-hashed.
        assertThat(hashOf("sam")).isEqualTo(samHash);
        assertThat(count("select count(*) from users where username in " + DEMO_USERS)).isEqualTo(5);
    }
}
