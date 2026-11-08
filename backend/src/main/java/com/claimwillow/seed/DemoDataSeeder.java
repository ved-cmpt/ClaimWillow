package com.claimwillow.seed;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import com.claimwillow.policy.Policy;
import com.claimwillow.policy.PolicyRepository;
import com.claimwillow.policy.PolicyStatus;
import com.claimwillow.user.AppUser;
import com.claimwillow.user.Role;
import com.claimwillow.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Opt-in, idempotent synthetic demo data (APP_SEED_DEMO_DATA=true). All people, addresses and
 * policies are fictional. Policy dates are relative to "today" so the demo never goes stale.
 * Existing rows (matched by email / policy number) are left untouched.
 */
@Component
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    public static final String PRIYA = "priya@example.com";
    public static final String MARCUS = "marcus@example.com";
    public static final String ELENA = "elena@example.com";
    public static final String DANA = "dana.adjuster@example.com";
    public static final String SAM = "sam.adjuster@example.com";
    public static final String ADMIN = "admin@example.com";

    private final boolean enabled;
    private final String demoPassword;
    private final boolean allowDocumentedPassword;
    private final UserRepository users;
    private final PolicyRepository policies;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final DemoClaimSeeder claimSeeder;

    public DemoDataSeeder(@Value("${app.seed-demo-data:false}") boolean enabled,
            @Value("${app.seed-demo-password:}") String demoPassword,
            @Value("${app.seed-demo-allow-documented-password:false}") boolean allowDocumentedPassword,
            UserRepository users, PolicyRepository policies, PasswordEncoder passwordEncoder, TransactionTemplate tx,
            Clock clock, DemoClaimSeeder claimSeeder) {
        this.enabled = enabled;
        this.demoPassword = demoPassword;
        this.allowDocumentedPassword = allowDocumentedPassword;
        this.users = users;
        this.policies = policies;
        this.passwordEncoder = passwordEncoder;
        this.tx = tx;
        this.clock = clock;
        this.claimSeeder = claimSeeder;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void seedOnStartup() {
        if (!enabled) {
            return;
        }
        Optional<String> problem = DemoPasswordPolicy.problem(demoPassword, allowDocumentedPassword);
        if (problem.isPresent()) {
            log.warn("Demo seeding requested but skipped: {}.", problem.get());
            return;
        }
        seed();
    }

    public void seed() {
        tx.executeWithoutResult(status -> {
            String hash = passwordEncoder.encode(demoPassword);
            AppUser priya = user(PRIYA, "Priya Shah", Role.POLICYHOLDER, hash);
            AppUser marcus = user(MARCUS, "Marcus Bell", Role.POLICYHOLDER, hash);
            AppUser elena = user(ELENA, "Elena Ruiz", Role.POLICYHOLDER, hash);
            user(DANA, "Dana Okafor", Role.ADJUSTER, hash);
            user(SAM, "Sam Lindqvist", Role.ADJUSTER, hash);
            user(ADMIN, "Robin Hale", Role.ADMIN, hash);

            LocalDate today = LocalDate.now(clock);
            LocalDate currentStart = today.minusMonths(10).withDayOfMonth(1);
            policy("HC-100421", priya, PolicyStatus.ACTIVE, "14 Alder Lane, Unit 3, Kingsford ON",
                    currentStart, currentStart.plusYears(1).minusDays(1), "50000.00", "500.00");
            policy("HC-087713", priya, PolicyStatus.EXPIRED, "220 Brook Street, Kingsford ON",
                    currentStart.minusYears(1), currentStart.minusDays(1), "40000.00", "500.00");
            policy("HC-118730", marcus, PolicyStatus.ACTIVE, "9 Harbour View Road, Port Ellis NS",
                    today.minusMonths(4).withDayOfMonth(15), today.minusMonths(4).withDayOfMonth(15)
                            .plusYears(1).minusDays(1), "25000.00", "1000.00");
            policy("HC-120904", elena, PolicyStatus.ACTIVE, "77 Cedar Crescent, Fairmont BC",
                    today.minusMonths(2).withDayOfMonth(1), today.minusMonths(2).withDayOfMonth(1)
                            .plusYears(1).minusDays(1), "75000.00", "250.00");
        });
        claimSeeder.seedClaims();
        log.info("Synthetic demo data is present (idempotent seed complete).");
    }

    private AppUser user(String email, String name, Role role, String hash) {
        return users.findByEmail(email).orElseGet(() -> users.save(new AppUser(email, hash, name, role)));
    }

    private void policy(String number, AppUser holder, PolicyStatus status, String address, LocalDate from,
            LocalDate to, String limit, String deductible) {
        if (policies.existsByPolicyNumber(number)) {
            return;
        }
        policies.save(new Policy(number, holder.getId(), status, "Home Contents Plus", address, from, to,
                new BigDecimal(limit), new BigDecimal(deductible)));
    }

    public static List<String> demoEmails() {
        return List.of(PRIYA, MARCUS, ELENA, DANA, SAM, ADMIN);
    }
}
