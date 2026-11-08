package com.claimwillow.validation;

import java.util.Arrays;
import java.util.List;

import com.claimwillow.claim.ClaimDtos.RuleInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/rules")
@Tag(name = "Rules", description = "Catalog of deterministic claim rules (read-only)")
public class RuleController {

    @GetMapping
    @Operation(summary = "All claim rules with version and plain-language explanation")
    public List<RuleInfo> catalog() {
        return Arrays.stream(RuleCode.values())
                .map(r -> new RuleInfo(r, r.shortCode(), r.version(), r.title(), r.description(),
                        r == RuleCode.REQUIRED_INPUTS))
                .toList();
    }
}
