package com.holaclimbing.server.domain.video.dto.response;

public record ThumbnailUploadResponse(
        String thumbnailPath,
        String thumbnailUrl
) {
}
