package com.claimwillow.seed;

import static org.assertj.core.api.Assertions.assertThat;

import com.claimwillow.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

@IntegrationTest
class DemoSeedIT {

    @Autowired
    DemoDataSeeder seeder;

    @Autowired
    JdbcTemplate jdbc;

    private int count(String sql) {
        return jdbc.queryForObject(sql, Integer.class);
    }

    @Test
    void seedIsIdempotentAndCoversSeveralStatuses() {
        seeder.seed();
        int users = count("select count(*) from users where email like '%@example.com' and email in "
                + "('priya@example.com','marcus@example.com','elena@example.com','dana.adjuster@example.com',"
                + "'sam.adjuster@example.com','admin@example.com')");
        int claims = count("select count(*) from claims c join users u on u.id = c.owner_user_id "
                + "where u.email in ('priya@example.com','marcus@example.com','elena@example.com')");
        int events = count("select count(*) from claim_events");
        seeder.seed();
        assertThat(users).isEqualTo(6);
        assertThat(count("select count(*) from claims c join users u on u.id = c.owner_user_id "
                + "where u.email in ('priya@example.com','marcus@example.com','elena@example.com')")).isEqualTo(claims);
        assertThat(count("select count(*) from claim_events")).isEqualTo(events);
        assertThat(claims).isEqualTo(6);
        assertThat(count("select count(distinct c.status) from claims c join users u on u.id = c.owner_user_id "
                + "where u.email in ('priya@example.com','marcus@example.com','elena@example.com')"))
                .isGreaterThanOrEqualTo(5);
        assertThat(count("select count(*) from users where email = 'priya@example.com' "
                + "and password_hash like '$2%'")).isEqualTo(1);
    }
}
