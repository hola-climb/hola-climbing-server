package com.holaclimbing.server.domain.recommendation.dto.response;

import com.holaclimbing.server.domain.video.domain.Video;

import java.time.LocalDateTime;

/**
 * 추천 피드 영상. source는 'following'(팔로잉) 또는 'recommended'(추천).
 */
public record RecommendedVideoResponse(
        Long id,
        Long userId,
        Long gymId,
        String title,
        String grade,
        String thumbnailPath,
        String streamUrl,
        Integer durationSeconds,
        int viewCount,
        int likeCount,
        int commentCount,
        String source,
        LocalDateTime createdAt
) {
    public static RecommendedVideoResponse of(Video video, String streamUrl, String source) {
        return new RecommendedVideoResponse(
                video.getId(), video.getUserId(), video.getGymId(), video.getTitle(), video.getGrade(),
                video.getThumbnailPath(), streamUrl, video.getDurationSeconds(), video.getViewCount(),
                video.getLikeCount(), video.getCommentCount(), source, video.getCreatedAt());
    }
}
