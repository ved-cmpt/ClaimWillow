package com.claimwillow.config;

import java.time.Clock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ClockConfig {

    /** UTC clock for all instants; injectable so tests can control time. */
    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }
}
