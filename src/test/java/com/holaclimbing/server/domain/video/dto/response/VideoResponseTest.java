package com.holaclimbing.server.domain.video.dto.response;

import com.holaclimbing.server.domain.recommendation.dto.response.RecommendedVideoResponse;
import com.holaclimbing.server.domain.video.domain.Video;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

class VideoResponseTest {

    @Test
    @DisplayName("영상 요약 응답에 암장 이름을 포함한다")
    void summaryResponse_includesGymName() {
        VideoSummaryResponse response = VideoSummaryResponse.from(video(), "https://cdn.example/video.mp4");

        assertThat(response.gymName()).isEqualTo("TheClimb Gangnam");
    }

    @Test
    @DisplayName("영상 상세 응답에 암장 이름을 포함한다")
    void detailResponse_includesGymName() {
        VideoDetailResponse response = VideoDetailResponse.of(video(), false, "https://cdn.example/video.mp4");

        assertThat(response.gymName()).isEqualTo("TheClimb Gangnam");
    }

    @Test
    @DisplayName("추천 영상 응답에 암장 이름을 포함한다")
    void recommendedResponse_includesGymName() {
        RecommendedVideoResponse response = RecommendedVideoResponse.of(
                video(), "https://cdn.example/video.mp4", "recommended");

        assertThat(response.gymName()).isEqualTo("TheClimb Gangnam");
    }

    @Test
    @DisplayName("영상 응답의 생성 시각은 timezone offset을 포함한다")
    void videoResponsesExposeOffsetDateTime() {
        VideoDetailResponse response = VideoDetailResponse.of(video(), false, "https://cdn.example/video.mp4");

        assertThat(response.createdAt()).isInstanceOf(OffsetDateTime.class);
    }

    private Video video() {
        return Video.builder()
                .id(1L)
                .userId(2L)
                .gymId(1L)
                .gymName("TheClimb Gangnam")
                .gymGradeId(1003L)
                .gymGradeLabel("빨강")
                .gymGradeDifficultyOrder(30)
                .title("send")
                .description("clean ascent")
                .gcsPath("videos/uploads/2/send.mp4")
                .durationSeconds(45)
                .recordedDate(LocalDate.of(2026, 6, 3))
                .status("done")
                .isPublic(true)
                .createdAt(OffsetDateTime.of(2026, 6, 3, 12, 0, 0, 0, ZoneOffset.ofHours(9)))
                .updatedAt(OffsetDateTime.of(2026, 6, 3, 12, 1, 0, 0, ZoneOffset.ofHours(9)))
                .build();
    }
}
