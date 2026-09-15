package com.tweakdapp.backend.relationships.internal;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

interface BlockedAccountRepository extends JpaRepository<BlockedAccountEntity, BlockedId> {

    /**
     * The other side of every block involving {@code userId}, in either direction: accounts they
     * blocked plus accounts that blocked them. Served by the primary key on {@code blocker_id} and
     * {@code blocked_accounts_blocked_id_idx} on {@code blocked_id}.
     */
    @Query("""
            select case when b.id.blockerId = :userId then b.id.blockedId else b.id.blockerId end
              from BlockedAccountEntity b
             where b.id.blockerId = :userId or b.id.blockedId = :userId
            """)
    List<UUID> findCounterpartIds(@Param("userId") UUID userId);

    /** Whether a block exists between the two accounts, whoever placed it. */
    @Query("""
            select count(b) > 0
              from BlockedAccountEntity b
             where (b.id.blockerId = :first and b.id.blockedId = :second)
                or (b.id.blockerId = :second and b.id.blockedId = :first)
            """)
    boolean existsBetween(@Param("first") UUID first, @Param("second") UUID second);

    /** The accounts {@code blockerId} blocked, most recent block first — the settings list. */
    @Query("""
            select b
              from BlockedAccountEntity b
             where b.id.blockerId = :blockerId
             order by b.createdAt desc
            """)
    List<BlockedAccountEntity> findByBlockerNewestFirst(@Param("blockerId") UUID blockerId);
}
