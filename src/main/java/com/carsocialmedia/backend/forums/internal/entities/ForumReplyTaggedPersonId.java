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
 * Composite primary key for {@code public.forum_thread_reply_tagged_people}:
 * ({@code reply_id}, {@code user_id}).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ForumReplyTaggedPersonId implements Serializable {

    @Column(name = "reply_id")
    private UUID replyId;

    @Column(name = "user_id")
    private UUID userId;
}
