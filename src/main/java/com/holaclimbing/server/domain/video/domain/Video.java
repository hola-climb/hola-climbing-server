package com.holaclimbing.server.domain.video.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;

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
    private String title;
    private String description;
    private String grade;
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
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
