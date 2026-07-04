package com.carsocialmedia.backend.report.internal.entities;

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
 * Composite primary key for {@code public.forum_thread_reports}: ({@code thread_id},
 * {@code reporter_id}). A reporter can report a given thread at most once.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ForumThreadReportId implements Serializable {

    @Column(name = "thread_id")
    private UUID threadId;

    @Column(name = "reporter_id")
    private UUID reporterId;
}
