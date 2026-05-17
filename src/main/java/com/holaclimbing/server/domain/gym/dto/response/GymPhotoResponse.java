package com.holaclimbing.server.domain.gym.dto.response;

import com.holaclimbing.server.domain.gym.domain.GymPhoto;

public record GymPhotoResponse(
        Long id,
        String gcsPath,
        String caption,
        int displayOrder
) {
    public static GymPhotoResponse from(GymPhoto photo) {
        return new GymPhotoResponse(photo.getId(), photo.getGcsPath(), photo.getCaption(), photo.getDisplayOrder());
    }
}
