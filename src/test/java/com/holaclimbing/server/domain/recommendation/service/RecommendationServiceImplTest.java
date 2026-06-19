package com.holaclimbing.server.domain.recommendation.service;

import com.holaclimbing.server.domain.gym.service.GymOperatingStatusResolver;
import com.holaclimbing.server.domain.gym.service.GymProfileImageUrlResolver;
import com.holaclimbing.server.domain.recommendation.domain.RecommendationFeedSnapshot;
import com.holaclimbing.server.domain.recommendation.dto.RecommendationCursor;
import com.holaclimbing.server.domain.recommendation.dto.RecommendationCursorCodec;
import com.holaclimbing.server.domain.recommendation.mapper.RecommendationInteractionMapper;
import com.holaclimbing.server.domain.recommendation.mapper.RecommendationMapper;
import com.holaclimbing.server.domain.video.domain.Video;
import com.holaclimbing.server.infrastructure.gcs.GcsStorageService;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RecommendationServiceImplTest {

    private final RecommendationMapper recommendationMapper = mock(RecommendationMapper.class);
    private final RecommendationInteractionMapper recommendationInteractionMapper = mock(RecommendationInteractionMapper.class);
    private final RecommendationFeedSnapshotStore snapshotStore = mock(RecommendationFeedSnapshotStore.class);
    private final GcsStorageService gcsStorageService = mock(GcsStorageService.class);
    private final GymProfileImageUrlResolver profileImageUrlResolver = mock(GymProfileImageUrlResolver.class);
    private final GymOperatingStatusResolver operatingStatusResolver = mock(GymOperatingStatusResolver.class);
    private final RecommendationServiceImpl service = new RecommendationServiceImpl(
            recommendationMapper,
            recommendationInteractionMapper,
            snapshotStore,
            gcsStorageService,
            profileImageUrlResolver,
            operatingStatusResolver);

    @Test
    void getVideoFeedCreatesSnapshotForFirstPage() {
        Video first = video(1L);
        Video second = video(2L);
        Video third = video(3L);
        when(recommendationMapper.findFeedSnapshotCandidates(eq(42L), eq(1_000), eq(5_000)))
                .thenReturn(List.of(first, second, third));
        when(snapshotStore.save(eq(42L), eq(List.of(1L, 2L, 3L)))).thenReturn("snap-1");

        var page = service.getVideoFeed(42L, null, 2);

        assertThat(page.content()).extracting("id").containsExactly(1L, 2L);
        assertThat(page.hasNext()).isTrue();
        RecommendationCursor nextCursor = RecommendationCursorCodec.decode(page.nextCursor());
        assertThat(nextCursor.getSnapshotId()).isEqualTo("snap-1");
        assertThat(nextCursor.getOffset()).isEqualTo(2);
        verify(recommendationInteractionMapper).upsertImpressions(eq(42L), eq(List.of(1L, 2L)));
    }

    @Test
    void getVideoFeedPassesDefaultCandidateWindowForFirstPageSnapshot() {
        when(recommendationMapper.findFeedSnapshotCandidates(eq(42L), eq(1_000), eq(5_000)))
                .thenReturn(List.of());

        service.getVideoFeed(42L, null, 20);

        verify(recommendationMapper).findFeedSnapshotCandidates(eq(42L), eq(1_000), eq(5_000));
    }

    @Test
    void getVideoFeedDoesNotCreateSignedUrlsForFeedItems() {
        Video first = video(1L);
        when(recommendationMapper.findFeedSnapshotCandidates(eq(42L), eq(1_000), eq(5_000)))
                .thenReturn(List.of(first));
        when(gcsStorageService.createPublicThumbnailUrl(eq("videos/thumbnails/1.jpg")))
                .thenReturn("https://storage.googleapis.com/hola-climbing-thumbnails-public/videos/thumbnails/1.jpg");

        var page = service.getVideoFeed(42L, null, 20);

        assertThat(page.content()).hasSize(1);
        assertThat(page.content().getFirst().thumbnailUrl())
                .isEqualTo("https://storage.googleapis.com/hola-climbing-thumbnails-public/videos/thumbnails/1.jpg");
        assertThat(page.content().getFirst().streamUrl()).isNull();
        verify(gcsStorageService, never()).createReadUrl(eq("videos/uploads/1.mp4"));
        verify(gcsStorageService, never()).createReadUrl(eq("videos/thumbnails/1.jpg"));
        verify(gcsStorageService).createPublicThumbnailUrl(eq("videos/thumbnails/1.jpg"));
    }

    @Test
    void getVideoFeedReadsCursorPageFromSnapshotWithoutRebuildingRanking() {
        RecommendationFeedSnapshot snapshot = new RecommendationFeedSnapshot(
                "snap-1",
                42L,
                List.of(1L, 2L, 3L, 4L, 5L),
                OffsetDateTime.parse("2026-06-19T10:00:00Z"));
        when(snapshotStore.find(eq(42L), eq("snap-1"))).thenReturn(Optional.of(snapshot));
        when(recommendationMapper.findFeedVideosByIds(eq(42L), eq(List.of(3L, 4L, 5L))))
                .thenReturn(List.of(video(3L), video(4L), video(5L)));

        String cursor = RecommendationCursorCodec.encode(RecommendationCursor.snapshot("snap-1", 2));
        var page = service.getVideoFeed(42L, cursor, 2);

        assertThat(page.content()).extracting("id").containsExactly(3L, 4L);
        assertThat(page.hasNext()).isTrue();
        RecommendationCursor nextCursor = RecommendationCursorCodec.decode(page.nextCursor());
        assertThat(nextCursor.getSnapshotId()).isEqualTo("snap-1");
        assertThat(nextCursor.getOffset()).isEqualTo(4);
        verify(recommendationMapper, never()).findFeedSnapshotCandidates(eq(42L), eq(1_000), eq(5_000));
        verify(recommendationInteractionMapper).upsertImpressions(eq(42L), eq(List.of(3L, 4L)));
    }

    private Video video(Long id) {
        return Video.builder()
                .id(id)
                .userId(id + 100)
                .gymId(1L)
                .gymName("TheClimb Gangnam")
                .gymGradeId(1002L)
                .gymGradeLabel("파랑")
                .gymGradeDifficultyOrder(20)
                .title("clip " + id)
                .gcsPath("videos/uploads/" + id + ".mp4")
                .thumbnailPath("videos/thumbnails/" + id + ".jpg")
                .followingRank(0)
                .createdAt(OffsetDateTime.parse("2026-06-19T10:00:00Z").plusSeconds(id))
                .build();
    }
}
