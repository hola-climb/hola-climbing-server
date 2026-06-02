package com.holaclimbing.server.domain.video.dto.response;

import com.holaclimbing.server.domain.video.domain.Video;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record VideoSummaryResponse(
        Long id,
        Long userId,
        Long gymId,
        String title,
        String grade,
        String thumbnailPath,
        String streamUrl,
        Integer durationSeconds,
        LocalDate recordedDate,
        int viewCount,
        int likeCount,
        int commentCount,
        LocalDateTime createdAt
) {
    /** streamUrl은 GCS 읽기 Signed URL — 서비스 계층에서 발급해 주입한다. */
    public static VideoSummaryResponse from(Video video, String streamUrl) {
        return new VideoSummaryResponse(
                video.getId(), video.getUserId(), video.getGymId(), video.getTitle(), video.getGrade(),
                video.getThumbnailPath(), streamUrl, video.getDurationSeconds(), video.getRecordedDate(), video.getViewCount(),
                video.getLikeCount(), video.getCommentCount(), video.getCreatedAt());
    }
}
