package com.holaclimbing.server.domain.recommendation.dto;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.Base64;

public final class RecommendationCursorCodec {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private RecommendationCursorCodec() {
    }

    public static String encode(RecommendationCursor cursor) {
        try {
            CursorPayload payload = new CursorPayload(
                    cursor.getDistanceNullRank(),
                    cursor.getRankingDistance(),
                    cursor.getFollowingRank(),
                    cursor.getCreatedAt().toString(),
                    cursor.getId());
            byte[] bytes = OBJECT_MAPPER.writeValueAsBytes(payload);
            return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("추천 피드 커서 생성에 실패했습니다.", e);
        }
    }

    public static RecommendationCursor decode(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }

        try {
            byte[] decoded = Base64.getUrlDecoder().decode(cursor);
            CursorPayload payload = OBJECT_MAPPER.readValue(
                    new String(decoded, StandardCharsets.UTF_8),
                    CursorPayload.class);
            validate(payload);
            return new RecommendationCursor(
                    payload.distanceNullRank(),
                    payload.rankingDistance(),
                    payload.followingRank(),
                    OffsetDateTime.parse(payload.createdAt()),
                    payload.id());
        } catch (IllegalArgumentException | IOException | DateTimeParseException e) {
            throw invalidCursor();
        }
    }

    private static void validate(CursorPayload payload) {
        if (payload == null
                || payload.distanceNullRank() == null
                || payload.followingRank() == null
                || payload.createdAt() == null
                || payload.id() == null) {
            throw new IllegalArgumentException("missing cursor field");
        }
        if ((payload.distanceNullRank() != 0 && payload.distanceNullRank() != 1)
                || (payload.followingRank() != 0 && payload.followingRank() != 1)) {
            throw new IllegalArgumentException("invalid cursor rank");
        }
        if (payload.distanceNullRank() == 0 && payload.rankingDistance() == null) {
            throw new IllegalArgumentException("missing ranking distance");
        }
    }

    private static BusinessException invalidCursor() {
        return new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 커서입니다.");
    }

    private record CursorPayload(
            Integer distanceNullRank,
            Double rankingDistance,
            Integer followingRank,
            String createdAt,
            Long id) {
    }
}
