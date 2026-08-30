package com.tweakdapp.backend.dms.internal.entities;

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
 * Composite primary key for {@code public.dm_message_car_tags}: ({@code message_id}, {@code car_id}).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class DmMessageCarTagId implements Serializable {

    @Column(name = "message_id")
    private UUID messageId;

    @Column(name = "car_id")
    private UUID carId;
}
