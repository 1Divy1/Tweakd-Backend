package com.tweakdapp.backend.business.internal.repositories;

import com.tweakdapp.backend.business.internal.entities.BusinessTypeOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

/** Read-only reference data for {@code business_type_options}. */
public interface BusinessTypeOptionRepository extends JpaRepository<BusinessTypeOptionEntity, String> {

    List<BusinessTypeOptionEntity> findAllByOrderByTypeAsc();
}
