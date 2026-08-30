package com.tweakdapp.backend.relationships.internal;

import jakarta.persistence.Embeddable;
import lombok.*;

import java.util.UUID;

@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class BlockedId {
    private UUID blockerId;
    private UUID blockedId;
}
