package com.carsocialmedia.backend.admin.internal;

import com.carsocialmedia.backend.admin.internal.dto.OverviewDto;
import com.carsocialmedia.backend.admin.internal.dto.OverviewDto.BadgeCountsDto;
import com.carsocialmedia.backend.admin.internal.dto.OverviewDto.ContentMixDto;
import com.carsocialmedia.backend.admin.internal.dto.OverviewDto.DayCountDto;
import com.carsocialmedia.backend.admin.internal.dto.OverviewDto.PostVolumeDto;
import com.carsocialmedia.backend.admin.internal.dto.OverviewDto.WeekCountDto;
import com.carsocialmedia.backend.admin.internal.dto.CaseSummaryDto;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Date;
import java.util.List;

/**
 * The Overview page's aggregates. This is the documented exception to strict table ownership: the
 * queries below read other modules' tables ({@code posts}, {@code comments}, {@code forum_threads},
 * {@code forum_thread_replies}, {@code profiles}, {@code support_tickets}, {@code feedback})
 * natively — but read-only and aggregate-only, so no business rule of those modules is bypassed.
 * All calendar bucketing is UTC.
 */
@Service
public class AdminOverviewService {

    private final JdbcClient jdbc;
    private final AdminModerationService moderationService;

    AdminOverviewService(JdbcClient jdbc, AdminModerationService moderationService) {
        this.jdbc = jdbc;
        this.moderationService = moderationService;
    }

    @Transactional(readOnly = true)
    public OverviewDto getOverview() {
        return new OverviewDto(
                postVolume(),
                postsSeries30d(),
                contentMix(),
                newProfiles8w(),
                moderationService.latestPendingCases(5),
                badges());
    }

    private PostVolumeDto postVolume() {
        return jdbc.sql("""
                        select count(*) filter (where created_at >= date_trunc('day', now())) as today,
                               count(*) filter (where created_at >= date_trunc('week', now())) as week,
                               count(*) filter (where created_at >= date_trunc('month', now())) as month
                        from posts
                        """)
                .query((rs, i) -> new PostVolumeDto(rs.getLong("today"), rs.getLong("week"), rs.getLong("month")))
                .single();
    }

    private List<DayCountDto> postsSeries30d() {
        return jdbc.sql("""
                        select d::date as day, count(p.id) as count
                        from generate_series(current_date - 29, current_date, interval '1 day') d
                        left join posts p on p.created_at >= d and p.created_at < d + interval '1 day'
                        group by d
                        order by d
                        """)
                .query((rs, i) -> new DayCountDto(rs.getObject("day", Date.class).toLocalDate(), rs.getLong("count")))
                .list();
    }

    private List<ContentMixDto> contentMix() {
        return jdbc.sql("""
                        select 'posts' as type, count(*) as count from posts
                        union all select 'comments', count(*) from comments where is_deleted = false
                        union all select 'forum_threads', count(*) from forum_threads where is_deleted = false
                        union all select 'forum_replies', count(*) from forum_thread_replies where is_deleted = false
                        """)
                .query((rs, i) -> new ContentMixDto(rs.getString("type"), rs.getLong("count")))
                .list();
    }

    private List<WeekCountDto> newProfiles8w() {
        return jdbc.sql("""
                        select w::date as week_start, count(p.id) as count
                        from generate_series(date_trunc('week', now()) - interval '7 weeks',
                                             date_trunc('week', now()), interval '1 week') w
                        left join profiles p on p.created_at >= w and p.created_at < w + interval '1 week'
                        group by w
                        order by w
                        """)
                .query((rs, i) -> new WeekCountDto(rs.getObject("week_start", Date.class).toLocalDate(), rs.getLong("count")))
                .list();
    }

    private BadgeCountsDto badges() {
        return jdbc.sql("""
                        select (select count(*) from moderation_cases where status in ('open', 'escalated')) as cases,
                               (select count(*) from support_tickets where status = 'open') as tickets,
                               (select count(*) from feedback where status = 'submitted') as feedback
                        """)
                .query((rs, i) -> new BadgeCountsDto(rs.getLong("cases"), rs.getLong("tickets"), rs.getLong("feedback")))
                .single();
    }
}
