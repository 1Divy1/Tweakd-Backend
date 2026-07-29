package com.carsocialmedia.backend.forums.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.util.UUID;

/**
 * Composite primary key for {@code public.forum_thread_tagged_cars}:
 * ({@code thread_id}, {@code car_id}).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ForumThreadTaggedCarId implements Serializable {

    @Column(name = "thread_id")
    private UUID threadId;

    @Column(name = "car_id")
    private UUID carId;
}
