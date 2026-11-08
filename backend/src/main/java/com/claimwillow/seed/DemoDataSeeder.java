package com.claimwillow.seed;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
 *
 * <p>The five demo accounts are reconciled on every start: an existing account (matched by
 * username, or by its demo email for databases seeded before usernames existed) gets its username,
 * display name, role and the configured demo password re-applied and is re-enabled, so a hosted
 * demo picks up changes without a reset. Policies (matched by number) and claims are only created
 * when missing.
 */
@Component
public class DemoDataSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoDataSeeder.class);

    public static final String PRIYA = "priya";
    public static final String MARCUS = "marcus";
    public static final String DANA = "dana";
    public static final String SAM = "sam";
    public static final String ADMIN = "admin";

    static final String PRIYA_POLICY = "HC-100421";
    static final String MARCUS_POLICY = "HC-118730";
    static final String MARCUS_SECOND_POLICY = "HC-120904";

    private record DemoAccount(String username, String email, String displayName, Role role) {
    }

    private static final List<DemoAccount> ACCOUNTS = List.of(
            new DemoAccount(PRIYA, "priya@example.com", "Priya Shah", Role.POLICYHOLDER),
            new DemoAccount(MARCUS, "marcus@example.com", "Marcus Bell", Role.POLICYHOLDER),
            new DemoAccount(DANA, "dana.adjuster@example.com", "Dana Okafor", Role.ADJUSTER),
            new DemoAccount(SAM, "sam.adjuster@example.com", "Sam Lindqvist", Role.ADJUSTER),
            new DemoAccount(ADMIN, "admin@example.com", "Robin Hale", Role.ADMIN));

    private final boolean enabled;
    private final String demoPassword;
    private final UserRepository users;
    private final PolicyRepository policies;
    private final PasswordEncoder passwordEncoder;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final DemoClaimSeeder claimSeeder;

    public DemoDataSeeder(@Value("${app.seed-demo-data:false}") boolean enabled,
            @Value("${app.seed-demo-password:}") String demoPassword,
            UserRepository users, PolicyRepository policies, PasswordEncoder passwordEncoder, TransactionTemplate tx,
            Clock clock, DemoClaimSeeder claimSeeder) {
        this.enabled = enabled;
        this.demoPassword = demoPassword;
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
        Optional<String> problem = DemoPasswordPolicy.problem(demoPassword);
        if (problem.isPresent()) {
            log.warn("Demo seeding requested but skipped: {}.", problem.get());
            return;
        }
        seed();
    }

    public void seed() {
        tx.executeWithoutResult(status -> {
            Map<String, AppUser> accounts = new HashMap<>();
            for (DemoAccount account : ACCOUNTS) {
                accounts.put(account.username(), reconcile(account));
            }
            AppUser priya = accounts.get(PRIYA);
            AppUser marcus = accounts.get(MARCUS);

            LocalDate today = LocalDate.now(clock);
            LocalDate currentStart = today.minusMonths(10).withDayOfMonth(1);
            policy(PRIYA_POLICY, priya, PolicyStatus.ACTIVE, "14 Alder Lane, Unit 3, Kingsford ON",
                    currentStart, currentStart.plusYears(1).minusDays(1), "50000.00", "500.00");
            policy("HC-087713", priya, PolicyStatus.EXPIRED, "220 Brook Street, Kingsford ON",
                    currentStart.minusYears(1), currentStart.minusDays(1), "40000.00", "500.00");
            policy(MARCUS_POLICY, marcus, PolicyStatus.ACTIVE, "9 Harbour View Road, Port Ellis NS",
                    today.minusMonths(4).withDayOfMonth(15), today.minusMonths(4).withDayOfMonth(15)
                            .plusYears(1).minusDays(1), "25000.00", "1000.00");
            policy(MARCUS_SECOND_POLICY, marcus, PolicyStatus.ACTIVE, "77 Cedar Crescent, Fairmont BC",
                    today.minusMonths(2).withDayOfMonth(1), today.minusMonths(2).withDayOfMonth(1)
                            .plusYears(1).minusDays(1), "75000.00", "250.00");
        });
        claimSeeder.seedClaims();
        log.info("Synthetic demo data is present (idempotent seed complete).");
    }

    /** Creates the demo account or re-applies its username, name, role and password. */
    private AppUser reconcile(DemoAccount account) {
        Optional<AppUser> existing = users.findByUsername(account.username())
                .or(() -> users.findByEmail(account.email()));
        if (existing.isEmpty()) {
            return users.save(new AppUser(account.username(), account.email(), passwordEncoder.encode(demoPassword),
                    account.displayName(), account.role()));
        }
        AppUser user = existing.get();
        user.setUsername(account.username());
        user.setDisplayName(account.displayName());
        user.setRole(account.role());
        user.setEnabled(true);
        if (!passwordEncoder.matches(demoPassword, user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(demoPassword));
        }
        return users.save(user);
    }

    private void policy(String number, AppUser holder, PolicyStatus status, String address, LocalDate from,
            LocalDate to, String limit, String deductible) {
        if (policies.existsByPolicyNumber(number)) {
            return;
        }
        policies.save(new Policy(number, holder.getId(), status, "Home Contents Plus", address, from, to,
                new BigDecimal(limit), new BigDecimal(deductible)));
    }

    public static List<String> demoUsernames() {
        return ACCOUNTS.stream().map(DemoAccount::username).toList();
    }
}
