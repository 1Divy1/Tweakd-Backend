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
 * Composite primary key for {@code public.comment_reports}: ({@code comment_id}, {@code reporter_id}).
 * A reporter can report a given comment at most once.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CommentReportId implements Serializable {

    @Column(name = "comment_id")
    private UUID commentId;

    @Column(name = "reporter_id")
    private UUID reporterId;
}
