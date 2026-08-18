package com.carsocialmedia.backend.admin.internal;

import java.util.EnumSet;
import java.util.Set;

/**
 * The dashboard team roles and their capability matrix (the Moderators page's permission table).
 * The constant names match the lowercase {@code admin_team_members.role} CHECK values exactly.
 *
 * <p>The matrix is code, not data, on purpose: capabilities change with the product, roles are the
 * stable vocabulary stored in the DB.
 */
enum AdminRole {

    owner(EnumSet.allOf(Capability.class)),

    senior_admin(EnumSet.complementOf(EnumSet.of(Capability.TRANSFER_OWNERSHIP))),

    // Note: no APPROVE_EVENTS — content moderators handle reported content, but publishing an
    // event to the map is an owner / senior-admin decision.
    content_moderator(EnumSet.of(Capability.REVIEW_CONTENT, Capability.WARN_BAN)),

    // Note: no MANAGE_ROADMAP — ANSWER_TICKETS covers the older feedback board and support tickets,
    // but committing the community feed to "in development" or "shipped" is an owner / senior-admin
    // decision.
    support_agent(EnumSet.of(Capability.ANSWER_TICKETS)),

    technical(EnumSet.of(Capability.VIEW_ANALYTICS));

    private final Set<Capability> capabilities;

    AdminRole(Set<Capability> capabilities) {
        this.capabilities = capabilities;
    }

    boolean can(Capability capability) {
        return capabilities.contains(capability);
    }
}
