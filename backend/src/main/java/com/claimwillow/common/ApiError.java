package com.claimwillow.common;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;

/** Standard error body returned by every API endpoint. */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        Instant timestamp,
        int status,
        String code,
        String message,
        String path,
        String requestId,
        List<FieldViolation> fieldErrors,
        Map<String, Object> details) {

    public record FieldViolation(String field, String message) {
    }
}
