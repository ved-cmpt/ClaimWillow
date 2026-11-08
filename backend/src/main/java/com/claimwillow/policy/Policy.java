package com.claimwillow.policy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

@Entity
@Table(name = "policies")
public class Policy {

    @Id
    private UUID id;

    @Column(name = "policy_number", nullable = false, unique = true)
    private String policyNumber;

    @Column(name = "policyholder_user_id", nullable = false)
    private UUID policyholderUserId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PolicyStatus status;

    @Column(name = "product_name", nullable = false)
    private String productName;

    @Column(name = "insured_address", nullable = false)
    private String insuredAddress;

    @Column(name = "effective_date", nullable = false)
    private LocalDate effectiveDate;

    @Column(name = "expiration_date", nullable = false)
    private LocalDate expirationDate;

    @Column(name = "coverage_limit", nullable = false, precision = 12, scale = 2)
    private BigDecimal coverageLimit;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal deductible;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Policy() {
    }

    public Policy(String policyNumber, UUID policyholderUserId, PolicyStatus status, String productName,
            String insuredAddress, LocalDate effectiveDate, LocalDate expirationDate, BigDecimal coverageLimit,
            BigDecimal deductible) {
        this.id = UUID.randomUUID();
        this.policyNumber = policyNumber;
        this.policyholderUserId = policyholderUserId;
        this.status = status;
        this.productName = productName;
        this.insuredAddress = insuredAddress;
        this.effectiveDate = effectiveDate;
        this.expirationDate = expirationDate;
        this.coverageLimit = coverageLimit;
        this.deductible = deductible;
    }

    @PrePersist
    void onCreate() {
        createdAt = Instant.now();
    }

    /** True when the given date falls within the policy period (both ends inclusive). */
    public boolean covers(LocalDate date) {
        return date != null && !date.isBefore(effectiveDate) && !date.isAfter(expirationDate);
    }

    public UUID getId() {
        return id;
    }

    public String getPolicyNumber() {
        return policyNumber;
    }

    public UUID getPolicyholderUserId() {
        return policyholderUserId;
    }

    public PolicyStatus getStatus() {
        return status;
    }

    public String getProductName() {
        return productName;
    }

    public String getInsuredAddress() {
        return insuredAddress;
    }

    public LocalDate getEffectiveDate() {
        return effectiveDate;
    }

    public LocalDate getExpirationDate() {
        return expirationDate;
    }

    public BigDecimal getCoverageLimit() {
        return coverageLimit;
    }

    public BigDecimal getDeductible() {
        return deductible;
    }

    public void setCoverageLimit(BigDecimal coverageLimit) {
        this.coverageLimit = coverageLimit;
    }
}
