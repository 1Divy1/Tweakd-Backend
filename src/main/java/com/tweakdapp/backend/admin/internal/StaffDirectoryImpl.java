package com.tweakdapp.backend.admin.internal;

import com.tweakdapp.backend.admin.internal.entities.AdminTeamMemberEntity;
import com.tweakdapp.backend.admin.internal.repositories.AdminTeamMemberRepository;
import com.tweakdapp.backend.shared.staff.StaffDirectory;
import com.tweakdapp.backend.shared.staff.StaffRefDto;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

/** The admin module's side of {@link StaffDirectory}: staff identity is the team row itself. */
@Component
class StaffDirectoryImpl implements StaffDirectory {

    private final AdminTeamMemberRepository teamRepository;

    StaffDirectoryImpl(AdminTeamMemberRepository teamRepository) {
        this.teamRepository = teamRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public Map<UUID, StaffRefDto> findByIds(Collection<UUID> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return teamRepository.findAllById(ids).stream()
                .collect(Collectors.toMap(
                        AdminTeamMemberEntity::getUserId,
                        member -> new StaffRefDto(
                                member.getUserId(), member.getDisplayName(), member.getAvatarUrl())));
    }
}
