package com.carsocialmedia.backend.business.internal.repositories;

import com.carsocialmedia.backend.business.internal.entities.BusinessHoursEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface BusinessHoursRepository extends JpaRepository<BusinessHoursEntity, UUID> {

    /** One business's week, Monday first — the business profile page. */
    List<BusinessHoursEntity> findByBusinessIdOrderByWeekdayAsc(UUID businessId);

    /**
     * Hours for a whole page of businesses in one round trip. The map's {@code is_open_now} flag is
     * derived per business, so fetching hours per pin would be a classic N+1 across a screen that
     * can hold hundreds of markers.
     */
    List<BusinessHoursEntity> findByBusinessIdIn(Collection<UUID> businessIds);
}
