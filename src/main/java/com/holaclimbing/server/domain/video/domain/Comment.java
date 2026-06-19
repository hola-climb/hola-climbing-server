package com.holaclimbing.server.domain.video.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 영상 댓글 엔티티. comments 테이블 매핑.
 * parentId가 있으면 대댓글(답글).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Comment {

    private Long id;
    private Long userId;
    private Long videoId;
    private Long parentId;
    private String content;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
}
