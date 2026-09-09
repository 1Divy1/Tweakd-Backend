package com.tweakdapp.backend.mapevents.internal.repositories;

import com.tweakdapp.backend.mapevents.internal.entities.ContestVoteEntity;
import com.tweakdapp.backend.mapevents.internal.entities.ContestVoteId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ContestVoteRepository extends JpaRepository<ContestVoteEntity, ContestVoteId> {

    /** One voter's choices across several contests — the viewer state of the list endpoint, in one query. */
    List<ContestVoteEntity> findByIdContestIdInAndIdVoterId(Collection<UUID> contestIds, UUID voterId);

    /**
     * Drops every vote for one car in one contest — when an organizer pulls an accepted car off
     * the ballot mid-vote, so the people who voted for it can vote again.
     */
    @Modifying
    @Query("delete from ContestVoteEntity v where v.id.contestId = :contestId and v.carId = :carId")
    int deleteByContestIdAndCarId(@Param("contestId") UUID contestId, @Param("carId") UUID carId);
}
