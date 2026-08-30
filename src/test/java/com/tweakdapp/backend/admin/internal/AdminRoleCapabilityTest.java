package com.tweakdapp.backend.admin.internal;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The role→capability matrix, asserted for the decisions that are easy to undo by accident.
 *
 * <p>{@code APPROVE_EVENTS} is the pointed one: publishing a user-submitted event to the map is an
 * owner / senior-admin decision, deliberately <em>not</em> bundled with the content-moderation
 * capability. Because {@code senior_admin} is defined as "everything except TRANSFER_OWNERSHIP",
 * new capabilities land there automatically — but a content moderator gaining this one would be a
 * silent policy change, so it is pinned here.
 */
class AdminRoleCapabilityTest {

    @Test
    void onlyOwnerAndSeniorAdminMayApproveMapEvents() {
        assertThat(AdminRole.owner.can(Capability.APPROVE_EVENTS)).isTrue();
        assertThat(AdminRole.senior_admin.can(Capability.APPROVE_EVENTS)).isTrue();

        assertThat(AdminRole.content_moderator.can(Capability.APPROVE_EVENTS)).isFalse();
        assertThat(AdminRole.support_agent.can(Capability.APPROVE_EVENTS)).isFalse();
        assertThat(AdminRole.technical.can(Capability.APPROVE_EVENTS)).isFalse();
    }

    @Test
    void contentModeratorsKeepTheirExistingModerationCapabilities() {
        // The new capability must not have disturbed what this role could already do.
        assertThat(AdminRole.content_moderator.can(Capability.REVIEW_CONTENT)).isTrue();
        assertThat(AdminRole.content_moderator.can(Capability.WARN_BAN)).isTrue();
    }

    @Test
    void onlyTheOwnerMayTransferOwnership() {
        assertThat(AdminRole.owner.can(Capability.TRANSFER_OWNERSHIP)).isTrue();
        assertThat(AdminRole.senior_admin.can(Capability.TRANSFER_OWNERSHIP)).isFalse();
    }
}
