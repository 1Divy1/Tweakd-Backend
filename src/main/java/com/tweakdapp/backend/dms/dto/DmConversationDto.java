package com.tweakdapp.backend.dms.dto;

import com.tweakdapp.backend.profile.dto.ProfileSearchResultDto;

import java.time.Instant;
import java.util.UUID;

/**
 * One row of the chats list. {@code peer} resolves through the profile module (same convention as
 * forums' {@code author}). The {@code lastMessage*} trio is denormalized on the conversation so the
 * list renders without touching {@code dm_messages}; {@code lastMessagePreview} is {@code null}
 * when the last message was deleted.
 *
 * <p>{@code peerOnline} / {@code peerLastSeenAt} carry the peer's presence flat on the row (the
 * garage flat-field convention) — semantics as in
 * {@link com.tweakdapp.backend.presence.dto.PresenceDto}.
 */
public record DmConversationDto(
        UUID id,
        ProfileSearchResultDto peer,
        String lastMessagePreview,
        UUID lastMessageSenderId,
        Instant lastMessageAt,
        int unreadCount,
        boolean peerOnline,
        Instant peerLastSeenAt
) {}
