package com.tweakdapp.backend.forums.internal.entities;

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
 * Composite primary key for {@code public.forum_thread_reply_tagged_cars}:
 * ({@code reply_id}, {@code car_id}).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ForumReplyTaggedCarId implements Serializable {

    @Column(name = "reply_id")
    private UUID replyId;

    @Column(name = "car_id")
    private UUID carId;
}
