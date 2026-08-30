package com.tweakdapp.backend.shared.staff;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;

/**
 * Read-only lookup of dashboard staff identities, implemented by the {@code admin} module (the
 * owner of {@code admin_team_members}). Lives in {@code shared} so lower-level modules (e.g.
 * {@code support}, which must render staff senders and assignees) can resolve staff without
 * depending on {@code admin} — that would be a cycle, since {@code admin} orchestrates them.
 */
public interface StaffDirectory {

    /** Resolves the given staff ids; ids that are not (or no longer) team members are absent. */
    Map<UUID, StaffRefDto> findByIds(Collection<UUID> ids);
}
