package com.holaclimbing.server.domain.video.dto;

import com.holaclimbing.server.domain.video.domain.Video;

import java.time.LocalDate;

public record VideoFeedCursor(LocalDate recordedDate, Long id) {

    public static VideoFeedCursor from(Video video) {
        return new VideoFeedCursor(video.getRecordedDate(), video.getId());
    }
}
