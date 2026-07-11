package com.carsocialmedia.backend.admin.internal.dto;

import java.time.LocalDate;
import java.util.List;

/**
 * The dashboard's Overview page in one response. Everything here is derivable from data that
 * already exists — DAU / MAU / session / churn analytics are deferred until activity tracking
 * exists (see the progress file).
 *
 * @param posts        posts created today / this week / this month (calendar buckets, UTC)
 * @param postsSeries  posts per day for the last 30 days, oldest first (zero-filled)
 * @param contentMix   total rows per content type (posts, comments, forum threads, forum replies)
 * @param newProfiles  new sign-ups per week for the last 8 weeks, oldest first (zero-filled)
 * @param latestCases  the 5 most recently reported open / escalated moderation cases
 * @param badges       the sidebar badge numbers
 */
public record OverviewDto(
        PostVolumeDto posts,
        List<DayCountDto> postsSeries,
        List<ContentMixDto> contentMix,
        List<WeekCountDto> newProfiles,
        List<CaseSummaryDto> latestCases,
        BadgeCountsDto badges) {

    public record PostVolumeDto(long today, long thisWeek, long thisMonth) {
    }

    public record DayCountDto(LocalDate day, long count) {
    }

    public record ContentMixDto(String type, long count) {
    }

    public record WeekCountDto(LocalDate weekStart, long count) {
    }

    /**
     * @param pendingCases open + escalated moderation cases
     * @param openTickets  support tickets waiting on staff
     * @param newFeedback  feedback still in {@code submitted}
     */
    public record BadgeCountsDto(long pendingCases, long openTickets, long newFeedback) {
    }
}
