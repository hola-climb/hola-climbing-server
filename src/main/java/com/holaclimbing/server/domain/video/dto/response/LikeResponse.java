package com.holaclimbing.server.domain.video.dto.response;

/**
 * 좋아요/좋아요 취소 응답.
 */
public record LikeResponse(
        boolean isLiked,
        long likeCount
) {
}
