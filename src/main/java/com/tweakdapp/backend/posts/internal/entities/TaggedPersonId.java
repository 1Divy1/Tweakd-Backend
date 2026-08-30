package com.tweakdapp.backend.posts.internal.entities;

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
 * Composite primary key for {@code public.tagged_people}: ({@code post_id}, {@code user_id}).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class TaggedPersonId implements Serializable {

    @Column(name = "post_id")
    private UUID postId;

    @Column(name = "user_id")
    private UUID userId;
}
