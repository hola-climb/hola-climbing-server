package com.holaclimbing.server.domain.video.dto.response;

import com.holaclimbing.server.domain.video.domain.Video;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record VideoDetailResponse(
        Long id,
        Long userId,
        Long gymId,
        String title,
        String description,
        String grade,
        String gcsPath,
        String gcsStreamingPath,
        String thumbnailPath,
        String streamUrl,
        Integer durationSeconds,
        LocalDate recordedDate,
        String status,
        boolean isPublic,
        int viewCount,
        int likeCount,
        int commentCount,
        boolean isLiked,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {
    /** streamUrl은 GCS 읽기 Signed URL — 서비스 계층에서 발급해 주입한다. */
    public static VideoDetailResponse of(Video video, boolean isLiked, String streamUrl) {
        return new VideoDetailResponse(
                video.getId(), video.getUserId(), video.getGymId(), video.getTitle(),
                video.getDescription(), video.getGrade(), video.getGcsPath(),
                video.getGcsStreamingPath(), video.getThumbnailPath(), streamUrl,
                video.getDurationSeconds(), video.getRecordedDate(), video.getStatus(), video.isPublic(),
                video.getViewCount(), video.getLikeCount(), video.getCommentCount(),
                isLiked, video.getCreatedAt(), video.getUpdatedAt());
    }
}
