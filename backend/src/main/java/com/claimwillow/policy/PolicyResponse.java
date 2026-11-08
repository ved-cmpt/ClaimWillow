package com.claimwillow.policy;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

public record PolicyResponse(
        UUID id,
        String policyNumber,
        PolicyStatus status,
        String productName,
        String insuredAddress,
        LocalDate effectiveDate,
        LocalDate expirationDate,
        BigDecimal coverageLimit,
        BigDecimal deductible,
        String currency) {

    public static PolicyResponse of(Policy p) {
        return new PolicyResponse(p.getId(), p.getPolicyNumber(), p.getStatus(), p.getProductName(),
                p.getInsuredAddress(), p.getEffectiveDate(), p.getExpirationDate(), p.getCoverageLimit(),
                p.getDeductible(), "CAD");
    }
}
