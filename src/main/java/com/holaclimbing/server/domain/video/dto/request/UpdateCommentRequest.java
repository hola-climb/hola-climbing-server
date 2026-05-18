package com.holaclimbing.server.domain.video.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 댓글 수정 요청.
 */
public record UpdateCommentRequest(
        @NotBlank @Size(max = 1000) String content
) {
}
