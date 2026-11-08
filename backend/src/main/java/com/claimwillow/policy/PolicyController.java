package com.claimwillow.policy;

import java.util.List;
import java.util.UUID;

import com.claimwillow.user.AppUserDetails;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/policies")
@Tag(name = "Policies")
public class PolicyController {

    private final PolicyService policyService;

    public PolicyController(PolicyService policyService) {
        this.policyService = policyService;
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('POLICYHOLDER')")
    @Operation(summary = "Policies held by the signed-in policyholder")
    public List<PolicyResponse> mine(@AuthenticationPrincipal AppUserDetails user) {
        return policyService.policiesOwnedBy(user).stream().map(PolicyResponse::of).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "One policy (owner or staff only; otherwise 404)")
    public PolicyResponse get(@PathVariable UUID id, @AuthenticationPrincipal AppUserDetails user) {
        return PolicyResponse.of(policyService.getVisiblePolicy(id, user));
    }
}
