package com.tweakdapp.backend.feedback.internal.repositories;

import com.tweakdapp.backend.feedback.internal.entities.FeedbackFeatureOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackFeatureOptionRepository extends JpaRepository<FeedbackFeatureOptionEntity, String> {

    List<FeedbackFeatureOptionEntity> findAllByOrderByNameAsc();
}
