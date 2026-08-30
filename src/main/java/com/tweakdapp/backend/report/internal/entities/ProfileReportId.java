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
 * Composite primary key for {@code public.profile_reports}: ({@code profile_id}, {@code reporter_id}).
 * A reporter can report a given profile at most once.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class ProfileReportId implements Serializable {

    @Column(name = "profile_id")
    private UUID profileId;

    @Column(name = "reporter_id")
    private UUID reporterId;
}
