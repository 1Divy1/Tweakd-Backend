package com.tweakdapp.backend.feedback.internal.repositories;

import com.tweakdapp.backend.feedback.internal.entities.FeedbackTypeOptionEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface FeedbackTypeOptionRepository extends JpaRepository<FeedbackTypeOptionEntity, String> {

    List<FeedbackTypeOptionEntity> findAllByOrderByCreatedAtAsc();
}
