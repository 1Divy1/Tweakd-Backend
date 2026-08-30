package com.tweakdapp.backend.support.internal.entities;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

/** Seeded reference data: a category a user picks when opening a ticket. */
@Entity
@Table(name = "support_ticket_categories")
@Getter
@Setter
public class TicketCategoryOptionEntity {

    @Id
    private String id;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;
}
