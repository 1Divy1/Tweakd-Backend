package com.tweakdapp.backend.mapevents.internal.repositories;

import com.tweakdapp.backend.mapevents.internal.entities.ContestEntryEntity;
import com.tweakdapp.backend.mapevents.internal.entities.ContestEntryId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface ContestEntryRepository extends JpaRepository<ContestEntryEntity, ContestEntryId> {

    /** Every entry row of several contests, one query — the list endpoint's ballots. */
    List<ContestEntryEntity> findByIdContestIdIn(Collection<UUID> contestIds);

    List<ContestEntryEntity> findByIdContestId(UUID contestId);

    List<ContestEntryEntity> findByIdContestIdAndStatus(UUID contestId, String status);

    long countByIdContestIdAndStatus(UUID contestId, String status);

    /** A car's entries across every contest — the history read. Served by the car index. */
    List<ContestEntryEntity> findByIdCarId(UUID carId);

    /**
     * Removes the given cars' entries from every contest of one event — what approving a
     * withdrawal, or rejecting a previously accepted car, has to do so a car that left the meet
     * is not still on a ballot. Votes pointing at the rows go with them (FK cascade), and the
     * counting triggers fire on the cascade.
     */
    @Modifying
    @Query("""
            delete from ContestEntryEntity e
             where e.id.carId in :carIds
               and e.id.contestId in (select c.id from ContestEntity c where c.eventId = :eventId)
            """)
    int deleteByEventIdAndCarIds(@Param("eventId") UUID eventId, @Param("carIds") Collection<UUID> carIds);
}
