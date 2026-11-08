package com.claimwillow.validation;

import java.math.BigDecimal;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Tunable rule parameters (application.yml {@code app.rules.*}). Changing them is an operator
 * action, never something the browser can do.
 */
@ConfigurationProperties(prefix = "app.rules")
public record RuleSettings(int duplicateWindowDays, BigDecimal duplicateAmountTolerance, int minDescriptionLength) {

    public RuleSettings {
        if (duplicateWindowDays <= 0) {
            duplicateWindowDays = 7;
        }
        if (duplicateAmountTolerance == null || duplicateAmountTolerance.signum() < 0) {
            duplicateAmountTolerance = new BigDecimal("0.20");
        }
        if (minDescriptionLength <= 0) {
            minDescriptionLength = 60;
        }
    }

    public static RuleSettings defaults() {
        return new RuleSettings(7, new BigDecimal("0.20"), 60);
    }
}
