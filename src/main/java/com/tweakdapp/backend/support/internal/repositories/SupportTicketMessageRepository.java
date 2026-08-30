package com.tweakdapp.backend.support.internal.repositories;

import com.tweakdapp.backend.support.internal.entities.SupportTicketMessageEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessageEntity, UUID> {

    List<SupportTicketMessageEntity> findByTicketIdOrderByCreatedAtAscIdAsc(UUID ticketId);

    /** The newest message of each given ticket — one query per ticket-list page, for the previews. */
    @Query(value = """
            select distinct on (ticket_id) ticket_id as ticketId, content
            from support_ticket_messages
            where ticket_id in (:ticketIds)
            order by ticket_id, created_at desc, id desc
            """, nativeQuery = true)
    List<PreviewRow> findLatestPerTicket(@Param("ticketIds") Collection<UUID> ticketIds);

    /** Interface projection for {@link #findLatestPerTicket} (aliases {@code ticketId} / {@code content}). */
    interface PreviewRow {
        UUID getTicketId();
        String getContent();
    }
}
