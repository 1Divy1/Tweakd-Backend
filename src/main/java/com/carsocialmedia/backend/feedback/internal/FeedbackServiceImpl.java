package com.carsocialmedia.backend.feedback.internal;

import com.carsocialmedia.backend.feedback.FeedbackService;
import com.carsocialmedia.backend.feedback.dto.FeedbackFeatureDto;
import com.carsocialmedia.backend.feedback.dto.FeedbackRequest;
import com.carsocialmedia.backend.feedback.dto.FeedbackTypeDto;
import com.carsocialmedia.backend.feedback.dto.MyFeedbackDto;
import com.carsocialmedia.backend.feedback.exception.InvalidFeedbackFeatureException;
import com.carsocialmedia.backend.feedback.exception.InvalidFeedbackTypeException;
import com.carsocialmedia.backend.feedback.internal.entities.FeedbackEntity;
import com.carsocialmedia.backend.feedback.internal.entities.FeedbackFeatureOptionEntity;
import com.carsocialmedia.backend.feedback.internal.entities.FeedbackTypeOptionEntity;
import com.carsocialmedia.backend.feedback.internal.repositories.FeedbackFeatureOptionRepository;
import com.carsocialmedia.backend.feedback.internal.repositories.FeedbackRepository;
import com.carsocialmedia.backend.feedback.internal.repositories.FeedbackTypeOptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class FeedbackServiceImpl implements FeedbackService {

    private final FeedbackRepository feedbackRepository;
    private final FeedbackTypeOptionRepository typeOptionRepository;
    private final FeedbackFeatureOptionRepository featureOptionRepository;

    public FeedbackServiceImpl(FeedbackRepository feedbackRepository,
                               FeedbackTypeOptionRepository typeOptionRepository,
                               FeedbackFeatureOptionRepository featureOptionRepository) {
        this.feedbackRepository = feedbackRepository;
        this.typeOptionRepository = typeOptionRepository;
        this.featureOptionRepository = featureOptionRepository;
    }

    @Override
    @Transactional
    public void submitFeedback(UUID userId, FeedbackRequest request) {
        if (!typeOptionRepository.existsById(request.type())) {
            throw new InvalidFeedbackTypeException(request.type());
        }
        // feature is optional; validate only when the client actually attached one.
        String feature = normalize(request.feature());
        if (feature != null && !featureOptionRepository.existsById(feature)) {
            throw new InvalidFeedbackFeatureException(feature);
        }

        FeedbackEntity feedback = new FeedbackEntity();
        feedback.setId(UUID.randomUUID());
        feedback.setUserId(userId);
        feedback.setContent(request.content());
        feedback.setType(request.type());
        feedback.setFeature(feature);
        feedback.setReproductionSteps(normalize(request.reproductionSteps()));
        feedbackRepository.save(feedback);
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackTypeDto> listFeedbackTypes() {
        return typeOptionRepository.findAllByOrderByCreatedAtAsc().stream()
                .map(option -> new FeedbackTypeDto(option.getId(), option.getType()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<FeedbackFeatureDto> listFeedbackFeatures() {
        return featureOptionRepository.findAllByOrderByNameAsc().stream()
                .map(option -> new FeedbackFeatureDto(option.getId(), option.getName()))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<MyFeedbackDto> listMyFeedback(UUID userId) {
        List<FeedbackEntity> feedback = feedbackRepository.findByUserIdOrderByCreatedAtDesc(userId);

        // Resolve every referenced type/feature label in one batch each, to avoid an N+1 lookup.
        Map<String, String> typeLabels = typeOptionRepository.findAll().stream()
                .collect(Collectors.toMap(FeedbackTypeOptionEntity::getId, FeedbackTypeOptionEntity::getType));
        Map<String, String> featureLabels = featureOptionRepository.findAll().stream()
                .collect(Collectors.toMap(FeedbackFeatureOptionEntity::getId, FeedbackFeatureOptionEntity::getName));

        return feedback.stream()
                .map(entity -> new MyFeedbackDto(
                        entity.getId(),
                        entity.getContent(),
                        typeLabels.getOrDefault(entity.getType(), entity.getType()),
                        entity.getFeature() == null ? null
                                : featureLabels.getOrDefault(entity.getFeature(), entity.getFeature()),
                        entity.getReproductionSteps(),
                        entity.getCreatedAt()))
                .toList();
    }

    /** Treats blank optional text as absent, so an empty string doesn't become a stored value. */
    private String normalize(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.strip();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
