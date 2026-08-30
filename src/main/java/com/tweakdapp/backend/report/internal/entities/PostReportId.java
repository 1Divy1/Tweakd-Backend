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
 * Composite primary key for {@code public.post_reports}: ({@code post_id}, {@code reporter_id}).
 * A reporter can report a given post at most once.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class PostReportId implements Serializable {

    @Column(name = "post_id")
    private UUID postId;

    @Column(name = "reporter_id")
    private UUID reporterId;
}
