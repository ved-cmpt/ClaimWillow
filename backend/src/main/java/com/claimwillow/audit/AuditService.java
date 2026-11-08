package com.claimwillow.audit;

import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import com.claimwillow.claim.Claim;
import com.claimwillow.claim.ClaimStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

/**
 * Records audit events. MANDATORY propagation: an event can only be written inside the same
 * transaction as the state change it describes, so either both commit or neither does.
 */
@Service
public class AuditService {

    private final ClaimEventRepository events;
    private final JsonMapper jsonMapper;
    private final Clock clock;

    public AuditService(ClaimEventRepository events, JsonMapper jsonMapper, Clock clock) {
        this.events = events;
        this.jsonMapper = jsonMapper;
        this.clock = clock;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ClaimEvent record(Claim claim, UUID actorId, EventType type, ClaimStatus from, ClaimStatus to,
            String summary, EventVisibility visibility, Map<String, ?> metadata) {
        String json = metadata == null || metadata.isEmpty() ? null : jsonMapper.writeValueAsString(metadata);
        return events.save(new ClaimEvent(claim.getId(), actorId, type, from, to, summary, visibility,
                Instant.now(clock), json));
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ClaimEvent record(Claim claim, UUID actorId, EventType type, ClaimStatus from, ClaimStatus to,
            String summary) {
        return record(claim, actorId, type, from, to, summary, EventVisibility.ALL, Map.of());
    }
}
