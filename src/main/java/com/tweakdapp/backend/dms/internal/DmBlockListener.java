package com.tweakdapp.backend.dms.internal;

import com.tweakdapp.backend.dms.internal.entities.DmConversationEntity;
import com.tweakdapp.backend.dms.internal.repositories.DmConversationRepository;
import com.tweakdapp.backend.dms.internal.repositories.DmParticipantStateRepository;
import com.tweakdapp.backend.shared.blocking.UserBlockedEvent;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

import java.util.Optional;
import java.util.UUID;

/**
 * Clears the unread badge on a blocked pair's conversation, for both sides. While the block stands
 * the conversation is filtered out of the chats list, so any unread count left on it would inflate
 * the app badge with messages nobody can open. The messages themselves are kept: unblocking brings
 * the conversation back as it was.
 */
@Component
class DmBlockListener {

    private final DmConversationRepository conversationRepository;
    private final DmParticipantStateRepository stateRepository;

    DmBlockListener(DmConversationRepository conversationRepository,
                    DmParticipantStateRepository stateRepository) {
        this.conversationRepository = conversationRepository;
        this.stateRepository = stateRepository;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    @TransactionalEventListener
    void on(UserBlockedEvent event) {
        UUID blocker = event.blockerId();
        UUID blocked = event.blockedId();
        // Both orders: the canonical user_a < user_b ordering is Postgres uuid order, which is not
        // java.util.UUID#compareTo order.
        Optional<DmConversationEntity> conversation = conversationRepository.findByUserAAndUserB(blocker, blocked)
                .or(() -> conversationRepository.findByUserAAndUserB(blocked, blocker));
        conversation.ifPresent(c -> {
            stateRepository.findByConversationIdAndUserId(c.getId(), blocker).ifPresent(s -> s.setUnreadCount(0));
            stateRepository.findByConversationIdAndUserId(c.getId(), blocked).ifPresent(s -> s.setUnreadCount(0));
        });
    }
}
