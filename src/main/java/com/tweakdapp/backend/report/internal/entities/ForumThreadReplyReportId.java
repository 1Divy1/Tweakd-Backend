package com.tweakdapp.backend.report.internal.entities;

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
 * Composite primary key for {@code public.forum_thread_reply_reports}: ({@code reply_id},
 * {@code reporter_id}). A reporter can report a given reply at most once.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ForumThreadReplyReportId implements Serializable {

    @Column(name = "reply_id")
    private UUID replyId;

    @Column(name = "reporter_id")
    private UUID reporterId;
}
