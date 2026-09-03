package com.project.monu.domain.comment.repository;

import java.time.Instant;
import java.util.UUID;

public record CommentQueryResult(
        UUID id,
        UUID articleId,
        UUID userId,
        String userNickname,
        String content,
        long likeCount,
        boolean likedByMe,
        Instant createdAt
) {
}