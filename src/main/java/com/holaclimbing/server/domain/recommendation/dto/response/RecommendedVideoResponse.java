package com.holaclimbing.server.domain.recommendation.dto.response;

import com.holaclimbing.server.domain.gym.dto.response.GymGradeResponse;
import com.holaclimbing.server.domain.video.domain.Video;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 추천 피드 영상. source는 'following'(팔로잉) 또는 'recommended'(추천).
 */
public record RecommendedVideoResponse(
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
        String source,
        LocalDateTime createdAt
) {
    public static RecommendedVideoResponse of(Video video, String streamUrl, String source) {
        return of(video, streamUrl, null, source);
    }

    public static RecommendedVideoResponse of(Video video, String streamUrl, String thumbnailUrl, String source) {
        return new RecommendedVideoResponse(
                video.getId(), video.getUserId(), video.getGymId(), video.getGymName(), gymGradeOf(video), video.getTitle(),
                video.getThumbnailPath(), thumbnailUrl, streamUrl, video.getDurationSeconds(), video.getRecordedDate(), video.getViewCount(),
                video.getLikeCount(), video.getCommentCount(), source, video.getCreatedAt());
    }

    private static GymGradeResponse gymGradeOf(Video video) {
        return new GymGradeResponse(
                video.getGymGradeId(),
                video.getGymId(),
                video.getGymGradeLabel(),
                video.getGymGradeDifficultyOrder());
    }
}
