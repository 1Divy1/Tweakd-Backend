package com.carsocialmedia.backend.support.internal.repositories;

import com.carsocialmedia.backend.support.internal.entities.TicketCategoryOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface TicketCategoryOptionRepository extends JpaRepository<TicketCategoryOptionEntity, String> {

    List<TicketCategoryOptionEntity> findAllByOrderBySortOrderAsc();
}
