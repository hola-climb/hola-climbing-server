package com.holaclimbing.server.domain.gym.service;

import com.holaclimbing.server.infrastructure.gcs.GcsStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class GymProfileImageUrlResolver {

    private final GcsStorageService gcsStorageService;

    public String resolve(String thumbnailUrl) {
        if (thumbnailUrl == null || thumbnailUrl.isBlank()) {
            return null;
        }
        if (thumbnailUrl.startsWith("http://") || thumbnailUrl.startsWith("https://")) {
            return thumbnailUrl;
        }
        return gcsStorageService.createReadUrl(thumbnailUrl);
    }
}
