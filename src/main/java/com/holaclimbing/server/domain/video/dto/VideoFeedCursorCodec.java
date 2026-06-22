package com.holaclimbing.server.domain.video.dto;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Base64;

public final class VideoFeedCursorCodec {

    private static final Base64.Encoder ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder DECODER = Base64.getUrlDecoder();
    private static final String SEPARATOR = ":";

    private VideoFeedCursorCodec() {
    }

    public static String encode(VideoFeedCursor cursor) {
        if (cursor == null || cursor.recordedDate() == null || cursor.id() == null) {
            throw new IllegalArgumentException("영상 피드 커서 필드가 비어 있습니다.");
        }
        String payload = cursor.recordedDate() + SEPARATOR + cursor.id();
        return ENCODER.encodeToString(payload.getBytes(StandardCharsets.UTF_8));
    }

    public static VideoFeedCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String payload = new String(DECODER.decode(cursor), StandardCharsets.UTF_8);
            String[] parts = payload.split(SEPARATOR, -1);
            if (parts.length != 2) {
                throw new IllegalArgumentException("invalid cursor payload");
            }
            LocalDate recordedDate = LocalDate.parse(parts[0]);
            long id = Long.parseLong(parts[1]);
            if (id <= 0) {
                throw new IllegalArgumentException("invalid cursor id");
            }
            return new VideoFeedCursor(recordedDate, id);
        } catch (IllegalArgumentException | DateTimeParseException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 커서입니다.");
        }
    }
}
