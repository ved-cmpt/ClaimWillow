package com.claimwillow.claim;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ClaimRepository extends JpaRepository<Claim, UUID>, JpaSpecificationExecutor<Claim> {

    Optional<Claim> findByIdAndOwnerUserId(UUID id, UUID ownerUserId);

    boolean existsByReference(String reference);

    Optional<Claim> findByReference(String reference);

    @Query("""
            select c from Claim c
            where c.ownerUserId = :owner
              and (:status is null or c.status = :status)
              and (:query is null
                   or lower(c.reference) like lower(concat('%', cast(:query as string), '%'))
                   or lower(c.title) like lower(concat('%', cast(:query as string), '%')))
            order by c.updatedAt desc
            """)
    Page<Claim> findMine(@Param("owner") UUID owner, @Param("status") ClaimStatus status,
            @Param("query") String query, Pageable pageable);

    @Query("select c.status, count(c) from Claim c where c.ownerUserId = :owner group by c.status")
    List<Object[]> countByStatusForOwner(@Param("owner") UUID owner);

    @Query("select c.status, count(c) from Claim c where c.status <> com.claimwillow.claim.ClaimStatus.DRAFT "
            + "group by c.status")
    List<Object[]> countByStatusExcludingDrafts();

    /**
     * Candidates for R5. Scoped to the same owner and policy, so another policyholder's claims
     * can never be returned.
     */
    @Query("""
            select c from Claim c
            where c.ownerUserId = :owner
              and c.policyId = :policy
              and c.incidentType = :type
              and c.id <> :exclude
              and c.status <> com.claimwillow.claim.ClaimStatus.DRAFT
              and c.incidentDate between :fromDate and :toDate
            """)
    List<Claim> findDuplicateCandidates(@Param("owner") UUID owner, @Param("policy") UUID policy,
            @Param("type") IncidentType type, @Param("exclude") UUID exclude,
            @Param("fromDate") LocalDate fromDate, @Param("toDate") LocalDate toDate);
}
