package com.holaclimbing.server.domain.video.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * 업로드용 Signed URL 발급 요청.
 * fileName은 확장자 추출용, mimeType은 발급된 URL의 서명에 포함된다.
 */
public record UploadUrlRequest(
        @NotBlank String fileName,
        @NotNull @Positive Long fileSize,
        @NotBlank String mimeType
) {
}
