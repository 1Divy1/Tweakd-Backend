package com.carsocialmedia.backend.posts.internal.entities;

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
 * Composite primary key for {@code public.tagged_cars}: ({@code post_id}, {@code car_id}).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TaggedCarId implements Serializable {

    @Column(name = "post_id")
    private UUID postId;

    @Column(name = "car_id")
    private UUID carId;
}
