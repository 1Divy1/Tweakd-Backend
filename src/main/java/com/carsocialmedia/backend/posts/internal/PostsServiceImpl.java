package com.carsocialmedia.backend.posts.internal;

import com.carsocialmedia.backend.posts.PostsService;
import com.carsocialmedia.backend.posts.dto.CommentDto;
import com.carsocialmedia.backend.posts.dto.CommentPageDto;
import com.carsocialmedia.backend.posts.dto.LikerPageDto;
import com.carsocialmedia.backend.posts.internal.entities.CommentEntity;
import com.carsocialmedia.backend.posts.internal.entities.PostLikeEntity;
import com.carsocialmedia.backend.posts.internal.repositories.CommentLikeRepository;
import com.carsocialmedia.backend.posts.internal.repositories.CommentRepository;
import com.carsocialmedia.backend.posts.internal.repositories.PostLikeRepository;
import com.carsocialmedia.backend.profile.ProfileService;
import com.carsocialmedia.backend.profile.dto.ProfileSearchResultDto;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class PostsServiceImpl implements PostsService {

    /** Hard cap on page size so a client can't request an unbounded page. */
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final CommentRepository commentRepository;
    private final CommentLikeRepository commentLikeRepository;
    private final PostLikeRepository postLikeRepository;
    private final ProfileService profileService;

    public PostsServiceImpl(CommentRepository commentRepository,
                            CommentLikeRepository commentLikeRepository,
                            PostLikeRepository postLikeRepository,
                            ProfileService profileService) {
        this.commentRepository = commentRepository;
        this.commentLikeRepository = commentLikeRepository;
        this.postLikeRepository = postLikeRepository;
        this.profileService = profileService;
    }

    @Override
    @Transactional(readOnly = true)
    public CommentPageDto getComments(UUID viewerId, UUID postId, String cursor, int size) {
        int limit = clampSize(size);
        PageCursor from = PageCursor.decode(cursor);

        // Fetch one extra row to detect whether a further page exists.
        List<CommentEntity> rows = commentRepository.findRootCommentPage(
                postId,
                from == null ? null : from.createdAt(),
                from == null ? null : from.id(),
                PageRequest.of(0, limit + 1));

        boolean hasMore = rows.size() > limit;
        List<CommentEntity> page = hasMore ? rows.subList(0, limit) : rows;

        // Batch-resolve authors and the viewer's likes across the whole page (no per-row queries).
        List<UUID> authorIds = page.stream().map(CommentEntity::getUserId).distinct().toList();
        Map<UUID, ProfileSearchResultDto> authors = profileService.findByIds(authorIds).stream()
                .collect(Collectors.toMap(ProfileSearchResultDto::id, Function.identity()));

        List<UUID> commentIds = page.stream().map(CommentEntity::getId).toList();
        Set<UUID> likedByViewer = Set.copyOf(commentLikeRepository.findLikedCommentIds(viewerId, commentIds));

        List<CommentDto> items = page.stream()
                .map(c -> new CommentDto(
                        c.getId(),
                        authors.get(c.getUserId()),
                        c.isDeleted() ? null : c.getContent(),
                        c.getParentCommentId(),
                        c.isDeleted(),
                        c.getLikesCount(),
                        likedByViewer.contains(c.getId()),
                        c.getCreatedAt()))
                .toList();

        String nextCursor = hasMore ? lastCommentCursor(page) : null;
        return new CommentPageDto(items, nextCursor);
    }

    @Override
    @Transactional(readOnly = true)
    public LikerPageDto getPostLikers(UUID postId, String cursor, int size) {
        int limit = clampSize(size);
        PageCursor from = PageCursor.decode(cursor);

        List<PostLikeEntity> rows = postLikeRepository.findLikerPage(
                postId,
                from == null ? null : from.createdAt(),
                from == null ? null : from.id(),
                PageRequest.of(0, limit + 1));

        boolean hasMore = rows.size() > limit;
        List<PostLikeEntity> page = hasMore ? rows.subList(0, limit) : rows;

        // Resolve profiles in one call, then re-order them to match the like ordering
        // (findByIds does not guarantee order).
        List<UUID> likerIds = page.stream().map(pl -> pl.getId().getUserId()).toList();
        Map<UUID, ProfileSearchResultDto> profiles = profileService.findByIds(likerIds).stream()
                .collect(Collectors.toMap(ProfileSearchResultDto::id, Function.identity()));

        List<ProfileSearchResultDto> items = likerIds.stream()
                .map(profiles::get)
                .filter(java.util.Objects::nonNull)
                .toList();

        String nextCursor = hasMore ? lastLikerCursor(page) : null;
        return new LikerPageDto(items, nextCursor);
    }

    private int clampSize(int size) {
        if (size <= 0) {
            return DEFAULT_PAGE_SIZE;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }

    private String lastCommentCursor(List<CommentEntity> page) {
        CommentEntity last = page.getLast();
        return new PageCursor(last.getCreatedAt(), last.getId()).encode();
    }

    private String lastLikerCursor(List<PostLikeEntity> page) {
        PostLikeEntity last = page.getLast();
        return new PageCursor(last.getCreatedAt(), last.getId().getUserId()).encode();
    }
}