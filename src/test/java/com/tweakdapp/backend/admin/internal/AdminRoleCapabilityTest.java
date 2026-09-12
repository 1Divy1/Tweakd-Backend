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
    void theOtherPublishingCapabilitiesShareTheSameAudience() {
        // MANAGE_ROADMAP, MANAGE_BADGES, VERIFY_BUSINESSES and MANAGE_CONTESTS are all owner /
        // senior-admin for the same reason as APPROVE_EVENTS: each one publishes something, or pays
        // something out, that no moderation rule decides and that cannot be cleanly undone.
        for (Capability capability : new Capability[]{
                Capability.MANAGE_ROADMAP,
                Capability.MANAGE_BADGES,
                Capability.VERIFY_BUSINESSES,
                Capability.MANAGE_CONTESTS}) {
            assertThat(AdminRole.owner.can(capability)).as("owner %s", capability).isTrue();
            assertThat(AdminRole.senior_admin.can(capability)).as("senior_admin %s", capability).isTrue();

            assertThat(AdminRole.content_moderator.can(capability)).as("content_moderator %s", capability).isFalse();
            assertThat(AdminRole.support_agent.can(capability)).as("support_agent %s", capability).isFalse();
            assertThat(AdminRole.technical.can(capability)).as("technical %s", capability).isFalse();
        }
    }

    @Test
    void supportAgentsKeepTicketsAndTheOlderFeedbackBoardOnly() {
        assertThat(AdminRole.support_agent.can(Capability.ANSWER_TICKETS)).isTrue();
        // The community roadmap is a product commitment, not user support.
        assertThat(AdminRole.support_agent.can(Capability.MANAGE_ROADMAP)).isFalse();
    }

    @Test
    void onlyTheOwnerMayTransferOwnership() {
        assertThat(AdminRole.owner.can(Capability.TRANSFER_OWNERSHIP)).isTrue();
        assertThat(AdminRole.senior_admin.can(Capability.TRANSFER_OWNERSHIP)).isFalse();
    }
}
