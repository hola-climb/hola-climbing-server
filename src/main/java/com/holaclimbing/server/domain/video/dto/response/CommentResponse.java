package com.holaclimbing.server.domain.video.dto.response;

import com.holaclimbing.server.domain.video.domain.Comment;

import java.time.OffsetDateTime;

public record CommentResponse(
        Long id,
        Long videoId,
        Long userId,
        String nickname,
        String profileImage,
        Long parentId,
        String content,
        OffsetDateTime createdAt
) {
    public static CommentResponse from(Comment comment) {
        return from(comment, comment.getProfileImage());
    }

    public static CommentResponse from(Comment comment, String profileImage) {
        return new CommentResponse(
                comment.getId(), comment.getVideoId(), comment.getUserId(),
                comment.getNickname(), profileImage,
                comment.getParentId(), comment.getContent(), comment.getCreatedAt());
    }
}
