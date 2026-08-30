package com.tweakdapp.backend.forums.internal.entities;

import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/**
 * Junction row tagging a thread with a topic (many topics per thread — the topic lens).
 */
@Entity
@Table(name = "forum_thread_topics")
@Getter
@Setter
public class ForumThreadTopicEntity {

    @EmbeddedId
    private ForumThreadTopicId id;
}
