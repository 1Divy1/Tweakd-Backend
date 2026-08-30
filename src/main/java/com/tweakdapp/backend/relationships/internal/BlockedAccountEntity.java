package com.tweakdapp.backend.relationships.internal;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "blocked_accounts")
@Getter
@Setter
public class BlockedAccountEntity {

    @EmbeddedId
    private BlockedId id;

    @Column(insertable = false, updatable = false)
    private Instant createdAt;
}
