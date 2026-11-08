package com.claimwillow.system;

import java.time.Instant;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Public, non-sensitive service status used by the UI to confirm the backend is reachable. */
@RestController
@RequestMapping("/api/v1/system")
public class SystemStatusController {

    private final String applicationName;

    public SystemStatusController(@Value("${spring.application.name}") String applicationName) {
        this.applicationName = applicationName;
    }

    @GetMapping("/status")
    public SystemStatus status() {
        return new SystemStatus(applicationName, "UP", Instant.now());
    }

    public record SystemStatus(String name, String status, Instant serverTime) {
    }
}
