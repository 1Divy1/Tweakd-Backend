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
 * Composite primary key for {@code public.forum_thread_topics}: ({@code thread_id}, {@code topic_id}).
 * {@code topicId} is a topic slug (text), not a UUID.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ForumThreadTopicId implements Serializable {

    @Column(name = "thread_id")
    private UUID threadId;

    @Column(name = "topic_id")
    private String topicId;
}
