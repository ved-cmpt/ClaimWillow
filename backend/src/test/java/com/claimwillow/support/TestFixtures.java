package com.claimwillow.support;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.claimwillow.policy.Policy;
import com.claimwillow.policy.PolicyRepository;
import com.claimwillow.policy.PolicyStatus;
import com.claimwillow.user.AppUser;
import com.claimwillow.user.AppUserDetails;
import com.claimwillow.user.Role;
import com.claimwillow.user.UserRepository;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

/** Creates isolated synthetic users/policies with unique identifiers for each test. */
@Component
public class TestFixtures {

    public static final String PASSWORD = "Test-Password-123";
    private static final String HASH = new BCryptPasswordEncoder(4).encode(PASSWORD);

    private final UserRepository users;
    private final PolicyRepository policies;

    public TestFixtures(UserRepository users, PolicyRepository policies) {
        this.users = users;
        this.policies = policies;
    }

    public AppUser user(Role role) {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        String username = role.name().toLowerCase() + "-" + tag;
        return users.save(new AppUser(username, username + "@example.com", HASH,
                "Test " + role.name().toLowerCase() + " " + tag, role));
    }

    public AppUser disabled(AppUser user) {
        user.setEnabled(false);
        return users.save(user);
    }

    public AppUser policyholder() {
        return user(Role.POLICYHOLDER);
    }

    public AppUser adjuster() {
        return user(Role.ADJUSTER);
    }

    public AppUser admin() {
        return user(Role.ADMIN);
    }

    /** Active policy covering the last year and the next month: limit 10,000.00, deductible 500.00. */
    public Policy activePolicy(AppUser holder) {
        LocalDate today = LocalDate.now();
        return policy(holder, today.minusYears(1), today.plusMonths(1), "10000.00", "500.00");
    }

    public Policy policy(AppUser holder, LocalDate from, LocalDate to, String limit, String deductible) {
        return policies.save(new Policy("T-" + UUID.randomUUID().toString().substring(0, 12).toUpperCase(),
                holder.getId(), PolicyStatus.ACTIVE, "Home Contents Plus", "1 Test Street, Testville ON", from, to,
                new BigDecimal(limit), new BigDecimal(deductible)));
    }

    /** Authenticates a MockMvc request as the given user (session principal equivalent). */
    public static RequestPostProcessor as(AppUser user) {
        AppUserDetails details = AppUserDetails.from(user);
        return SecurityMockMvcRequestPostProcessors.authentication(
                UsernamePasswordAuthenticationToken.authenticated(details, null, details.getAuthorities()));
    }
}
