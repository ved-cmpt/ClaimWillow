package com.claimwillow.seed;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;

import com.claimwillow.claim.Claim;
import com.claimwillow.claim.ClaimDtos.DraftRequest;
import com.claimwillow.claim.ClaimRepository;
import com.claimwillow.claim.ClaimService;
import com.claimwillow.claim.IncidentType;
import com.claimwillow.claim.StaffClaimService;
import com.claimwillow.claim.StaffDtos.Decision;
import com.claimwillow.policy.Policy;
import com.claimwillow.policy.PolicyRepository;
import com.claimwillow.user.AppUserDetails;
import com.claimwillow.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Seeds a handful of synthetic claims at different lifecycle stages, driven through the real
 * services so findings, notes and audit events are genuine. Skipped if the demo policyholder
 * already has claims.
 */
@Component
public class DemoClaimSeeder {

    private static final Logger log = LoggerFactory.getLogger(DemoClaimSeeder.class);

    private final UserRepository users;
    private final PolicyRepository policies;
    private final ClaimRepository claims;
    private final ClaimService claimService;
    private final StaffClaimService staff;
    private final Clock clock;

    public DemoClaimSeeder(UserRepository users, PolicyRepository policies, ClaimRepository claims,
            ClaimService claimService, StaffClaimService staff, Clock clock) {
        this.users = users;
        this.policies = policies;
        this.claims = claims;
        this.claimService = claimService;
        this.staff = staff;
        this.clock = clock;
    }

    public void seedClaims() {
        AppUserDetails priya = details(DemoDataSeeder.PRIYA);
        AppUserDetails marcus = details(DemoDataSeeder.MARCUS);
        AppUserDetails dana = details(DemoDataSeeder.DANA);
        AppUserDetails sam = details(DemoDataSeeder.SAM);
        if (priya == null || marcus == null || dana == null || sam == null) {
            return;
        }
        if (claims.findMine(priya.id(), null, null, org.springframework.data.domain.PageRequest.of(0, 1))
                .getTotalElements() > 0) {
            return;
        }
        LocalDate today = LocalDate.now(clock);
        Policy priyaPolicy = policy(DemoDataSeeder.PRIYA_POLICY);
        Policy marcusPolicy = policy(DemoDataSeeder.MARCUS_POLICY);
        Policy marcusSecondPolicy = policy(DemoDataSeeder.MARCUS_SECOND_POLICY);
        if (priyaPolicy == null || marcusPolicy == null || marcusSecondPolicy == null) {
            return;
        }

        // 1. Submitted, unassigned: waiting in the queue.
        submit(priya, priyaPolicy, today.minusDays(6), IncidentType.DAMAGE,
                "Water damage to living room furniture",
                "A supply line behind the washing machine failed overnight. Water spread into the living room and "
                        + "soaked the sofa, an area rug and the lower shelves of a bookcase.", "3200.00");

        // 2. In review with Dana.
        Claim bike = submit(priya, priyaPolicy, today.minusDays(12), IncidentType.LOSS,
                "Bicycle stolen from locked garage",
                "Our garage was broken into while we were away for the weekend. A commuter bicycle and a child "
                        + "seat were taken; a police report was filed the same day.", "1850.00");
        bike = staff.assign(bike.getId(), dana, bike.getVersion(), null);
        staff.startReview(bike.getId(), dana, bike.getVersion());

        // 3. Needs information from Marcus.
        Claim laptop = submit(marcus, marcusPolicy, today.minusDays(9), IncidentType.DAMAGE,
                "Laptop damaged by coffee spill", "Coffee was spilled on a work laptop.", "2400.00");
        laptop = staff.assign(laptop.getId(), dana, laptop.getVersion(), null);
        laptop = staff.startReview(laptop.getId(), dana, laptop.getVersion());
        staff.requestInfo(laptop.getId(), dana, laptop.getVersion(),
                "Please upload a repair estimate or receipt for the laptop and a photo of the damage.");

        // 4. Approved and closed by Sam.
        Claim kitchen = submit(marcus, marcusPolicy, today.minusDays(40), IncidentType.DAMAGE,
                "Small kitchen fire damaged appliances",
                "A pan fire spread to the range hood. The microwave, toaster oven and a stand mixer were damaged "
                        + "by heat and smoke; the fire service report is available.", "4200.00");
        kitchen = staff.assign(kitchen.getId(), sam, kitchen.getVersion(), null);
        kitchen = staff.startReview(kitchen.getId(), sam, kitchen.getVersion());
        kitchen = staff.decide(kitchen.getId(), sam, kitchen.getVersion(), Decision.APPROVED,
                "Fire service report and photos reviewed; damaged appliances are covered contents.");
        staff.close(kitchen.getId(), sam, kitchen.getVersion(), null);

        // 5. Over the coverage limit, unassigned.
        submit(marcus, marcusSecondPolicy, today.minusDays(3), IncidentType.DAMAGE,
                "Basement flood damaged stored contents",
                "Heavy rain caused groundwater to enter the basement. Stored furniture, a home gym and archived "
                        + "boxes of books were damaged beyond repair.", "82000.00");

        // 6. A draft Priya has not finished.
        claimService.createDraft(priya, new DraftRequest(priyaPolicy.getId(), today.minusDays(1),
                IncidentType.DAMAGE, "Television knocked over", null, null, null));
        log.info("Seeded synthetic demo claims.");
    }

    private Claim submit(AppUserDetails owner, Policy policy, LocalDate incident, IncidentType type, String title,
            String description, String estimate) {
        Claim draft = claimService.createDraft(owner, new DraftRequest(policy.getId(), incident, type, title,
                description, new BigDecimal(estimate), null));
        return claimService.submit(draft.getId(), owner, draft.getVersion());
    }

    private Policy policy(String number) {
        return policies.findByPolicyNumber(number).orElse(null);
    }

    private AppUserDetails details(String username) {
        return users.findByUsername(username).map(AppUserDetails::from).orElse(null);
    }
}
