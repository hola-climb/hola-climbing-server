package com.holaclimbing.server.domain.video.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * 클라이밍 영상 엔티티. videos 테이블 매핑.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Video {

    private Long id;
    private Long userId;
    private Long gymId;
    private String gymName;
    private Long gymGradeId;
    private String gymGradeLabel;
    private int gymGradeDifficultyOrder;
    private String title;
    private String description;
    private String gcsPath;
    private String gcsStreamingPath;
    private String thumbnailPath;
    private Integer durationSeconds;
    private LocalDate recordedDate;
    private Long fileSizeBytes;
    private String mimeType;
    private int viewCount;
    private int likeCount;
    private int commentCount;
    private String status;
    private boolean isPublic;
    private Integer distanceNullRank;
    private Double rankingDistance;
    private Integer followingRank;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
}
