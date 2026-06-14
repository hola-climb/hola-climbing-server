package com.holaclimbing.server.domain.video.dto.response;

import com.holaclimbing.server.domain.gym.dto.response.GymGradeResponse;
import com.holaclimbing.server.domain.video.domain.Video;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record VideoSummaryResponse(
        Long id,
        Long userId,
        Long gymId,
        String gymName,
        GymGradeResponse gymGrade,
        String title,
        String thumbnailPath,
        String thumbnailUrl,
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
        return from(video, streamUrl, null);
    }

    public static VideoSummaryResponse from(Video video, String streamUrl, String thumbnailUrl) {
        return new VideoSummaryResponse(
                video.getId(), video.getUserId(), video.getGymId(), video.getGymName(), gymGradeOf(video), video.getTitle(),
                video.getThumbnailPath(), thumbnailUrl, streamUrl, video.getDurationSeconds(), video.getRecordedDate(), video.getViewCount(),
                video.getLikeCount(), video.getCommentCount(), video.getCreatedAt());
    }

    private static GymGradeResponse gymGradeOf(Video video) {
        return new GymGradeResponse(
                video.getGymGradeId(),
                video.getGymId(),
                video.getGymGradeLabel(),
                video.getGymGradeDifficultyOrder());
    }
}
