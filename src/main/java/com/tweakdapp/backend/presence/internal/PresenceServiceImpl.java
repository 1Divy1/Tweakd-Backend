package com.tweakdapp.backend.presence.internal;

import com.tweakdapp.backend.presence.PresenceService;
import com.tweakdapp.backend.presence.dto.PresenceDto;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
class PresenceServiceImpl implements PresenceService {

    private final PresenceRegistry registry;
    private final UserPresenceRepository repository;

    PresenceServiceImpl(PresenceRegistry registry, UserPresenceRepository repository) {
        this.registry = registry;
        this.repository = repository;
    }

    @Override
    public Map<UUID, PresenceDto> getPresence(Collection<UUID> userIds) {
        Map<UUID, PresenceDto> result = new HashMap<>();
        List<UUID> offlineIds = userIds.stream()
                .distinct()
                .filter(id -> {
                    if (registry.isOnline(id)) {
                        result.put(id, PresenceDto.online(id));
                        return false;
                    }
                    return true;
                })
                .toList();

        if (!offlineIds.isEmpty()) {
            Map<UUID, UserPresenceEntity> lastSeens = repository.findAllById(offlineIds).stream()
                    .collect(Collectors.toMap(UserPresenceEntity::getUserId, Function.identity()));
            for (UUID id : offlineIds) {
                UserPresenceEntity entity = lastSeens.get(id);
                result.put(id, PresenceDto.offline(id, entity == null ? null : entity.getLastSeenAt()));
            }
        }
        return result;
    }
}
