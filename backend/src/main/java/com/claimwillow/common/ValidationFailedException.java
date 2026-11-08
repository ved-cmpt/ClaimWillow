package com.claimwillow.common;

import java.util.List;

/** Thrown when server-side business validation blocks an operation (HTTP 400). */
public class ValidationFailedException extends RuntimeException {

    private final transient List<ApiError.FieldViolation> violations;

    public ValidationFailedException(String message, List<ApiError.FieldViolation> violations) {
        super(message);
        this.violations = List.copyOf(violations);
    }

    public List<ApiError.FieldViolation> getViolations() {
        return violations;
    }
}
