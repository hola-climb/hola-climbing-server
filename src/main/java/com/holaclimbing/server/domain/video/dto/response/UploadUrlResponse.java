package com.holaclimbing.server.domain.video.dto.response;

/**
 * 업로드용 Signed URL 발급 응답.
 * - uploadUrl: 클라이언트가 PUT으로 직접 업로드할 GCS Signed URL
 * - objectPath: 영상 등록(POST /api/videos) 시 그대로 넘길 객체 경로
 * - expiresIn: Signed URL 유효 시간(초)
 */
public record UploadUrlResponse(
        String uploadUrl,
        String objectPath,
        long expiresIn
) {
}
