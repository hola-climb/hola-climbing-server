package com.holaclimbing.server.domain.recommendation.service;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.common.response.CursorPageResponse;
import com.holaclimbing.server.domain.recommendation.dto.RecommendationCursor;
import com.holaclimbing.server.domain.recommendation.dto.RecommendationCursorCodec;
import com.holaclimbing.server.domain.recommendation.dto.response.RecommendedGymResponse;
import com.holaclimbing.server.domain.recommendation.dto.response.RecommendedVideoResponse;
import com.holaclimbing.server.domain.recommendation.domain.RecommendationFeedSnapshot;
import com.holaclimbing.server.domain.recommendation.mapper.RecommendationInteractionMapper;
import com.holaclimbing.server.domain.recommendation.mapper.RecommendationMapper;
import com.holaclimbing.server.domain.gym.dto.DayHours;
import com.holaclimbing.server.domain.gym.service.GymOperatingStatusResolver;
import com.holaclimbing.server.domain.gym.service.GymProfileImageUrlResolver;
import com.holaclimbing.server.domain.video.domain.Video;
import com.holaclimbing.server.infrastructure.gcs.GcsStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private static final String SOURCE_FOLLOWING = "following";
    private static final String SOURCE_RECOMMENDED = "recommended";
    private static final int DEFAULT_FEED_CANDIDATE_WINDOW = 5_000;
    private static final int MIN_FEED_CANDIDATE_WINDOW_MULTIPLIER = 50;
    private static final int DEFAULT_FEED_SNAPSHOT_SIZE = 1_000;
    private static final int SNAPSHOT_LOOKAHEAD_MULTIPLIER = 3;
    private static final double MIN_LAT = -90.0;
    private static final double MAX_LAT = 90.0;
    private static final double MIN_LNG = -180.0;
    private static final double MAX_LNG = 180.0;

    private final RecommendationMapper recommendationMapper;
    private final RecommendationInteractionMapper recommendationInteractionMapper;
    private final RecommendationFeedSnapshotStore snapshotStore;
    private final GcsStorageService gcsStorageService;
    private final GymProfileImageUrlResolver profileImageUrlResolver;
    private final GymOperatingStatusResolver operatingStatusResolver;

    @Override
    public CursorPageResponse<RecommendedVideoResponse> getVideoFeed(Long userId, String cursor, int size) {
        if (cursor != null && !cursor.isBlank()) {
            RecommendationCursor decodedCursor = RecommendationCursorCodec.decode(cursor);
            if (decodedCursor == null || !decodedCursor.isSnapshot()) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "잘못된 추천 피드 커서입니다.");
            }
            return getVideoFeedFromSnapshot(userId, decodedCursor, size);
        }
        return getFirstVideoFeedPage(userId, size);
    }

    private CursorPageResponse<RecommendedVideoResponse> getFirstVideoFeedPage(Long userId, int size) {
        int candidateWindow = Math.max(DEFAULT_FEED_CANDIDATE_WINDOW, DEFAULT_FEED_SNAPSHOT_SIZE * 2);
        List<Video> ranked = recommendationMapper.findFeedSnapshotCandidates(
                userId,
                DEFAULT_FEED_SNAPSHOT_SIZE,
                candidateWindow);
        if (ranked.isEmpty()) {
            return CursorPageResponse.of(List.of(), null, false);
        }

        boolean hasNext = ranked.size() > size;
        List<Video> pageVideos = ranked.subList(0, Math.min(size, ranked.size()));
        recordImpressions(userId, pageVideos);
        String nextCursor = null;
        if (hasNext) {
            String snapshotId = snapshotStore.save(userId, ranked.stream().map(Video::getId).toList());
            nextCursor = RecommendationCursorCodec.encode(RecommendationCursor.snapshot(snapshotId, pageVideos.size()));
        }

        return CursorPageResponse.of(toVideoResponses(pageVideos), nextCursor, hasNext);
    }

    private CursorPageResponse<RecommendedVideoResponse> getVideoFeedFromSnapshot(
            Long userId,
            RecommendationCursor cursor,
            int size) {
        RecommendationFeedSnapshot snapshot = snapshotStore.find(userId, cursor.getSnapshotId())
                .orElseThrow(() -> new BusinessException(
                        ErrorCode.INVALID_INPUT,
                        "만료된 추천 피드 커서입니다. 첫 페이지를 다시 요청하세요."));

        List<Long> snapshotVideoIds = snapshot.videoIds();
        if (cursor.getOffset() >= snapshotVideoIds.size()) {
            return CursorPageResponse.of(List.of(), null, false);
        }

        SnapshotPage snapshotPage = findSnapshotPage(userId, snapshotVideoIds, cursor.getOffset(), size);
        recordImpressions(userId, snapshotPage.videos());
        String nextCursor = snapshotPage.hasNext()
                ? RecommendationCursorCodec.encode(RecommendationCursor.snapshot(cursor.getSnapshotId(), snapshotPage.nextOffset()))
                : null;
        return CursorPageResponse.of(toVideoResponses(snapshotPage.videos()), nextCursor, snapshotPage.hasNext());
    }

    private SnapshotPage findSnapshotPage(Long userId, List<Long> snapshotVideoIds, int offset, int size) {
        List<Video> collected = new ArrayList<>();
        int scanOffset = offset;
        int nextOffset = offset;
        int fetchSize = Math.max(size + 1, size * SNAPSHOT_LOOKAHEAD_MULTIPLIER + 1);

        while (scanOffset < snapshotVideoIds.size() && collected.size() <= size) {
            int sliceEnd = Math.min(snapshotVideoIds.size(), scanOffset + fetchSize);
            List<Long> slice = snapshotVideoIds.subList(scanOffset, sliceEnd);
            List<Video> visibleVideos = recommendationMapper.findFeedVideosByIds(userId, slice);
            for (Video video : visibleVideos) {
                if (collected.size() <= size) {
                    collected.add(video);
                }
            }

            if (collected.size() > size) {
                nextOffset = indexAfter(snapshotVideoIds, collected.get(size - 1).getId(), sliceEnd);
                break;
            }

            scanOffset = sliceEnd;
            nextOffset = sliceEnd;
        }

        boolean hasExtraVisible = collected.size() > size;
        List<Video> pageVideos = hasExtraVisible ? collected.subList(0, size) : collected;
        boolean hasNext = hasExtraVisible || nextOffset < snapshotVideoIds.size();
        return new SnapshotPage(pageVideos, nextOffset, hasNext);
    }

    private int indexAfter(List<Long> snapshotVideoIds, Long videoId, int fallback) {
        int index = snapshotVideoIds.indexOf(videoId);
        return index >= 0 ? index + 1 : fallback;
    }

    private List<RecommendedVideoResponse> toVideoResponses(List<Video> pageVideos) {
        return pageVideos
                .stream()
                .map(v -> RecommendedVideoResponse.of(v,
                        null,
                        gcsStorageService.createPublicThumbnailUrl(v.getThumbnailPath()),
                        Integer.valueOf(1).equals(v.getFollowingRank()) ? SOURCE_FOLLOWING : SOURCE_RECOMMENDED))
                .toList();
    }

    private record SnapshotPage(List<Video> videos, int nextOffset, boolean hasNext) {
    }

    @Deprecated
    private CursorPageResponse<RecommendedVideoResponse> getVideoFeedByRankingCursor(Long userId, String cursor, int size) {
        RecommendationCursor decodedCursor = RecommendationCursorCodec.decode(cursor);
        int limit = size + 1;
        int candidateWindow = Math.max(DEFAULT_FEED_CANDIDATE_WINDOW, limit * MIN_FEED_CANDIDATE_WINDOW_MULTIPLIER);
        List<Video> videos = recommendationMapper.findFeedVideos(userId, decodedCursor, limit, candidateWindow);
        boolean hasNext = videos.size() > size;
        List<Video> pageVideos = hasNext ? videos.subList(0, size) : videos;
        recordImpressions(userId, pageVideos);

        List<RecommendedVideoResponse> content = pageVideos
                .stream()
                .map(v -> RecommendedVideoResponse.of(v,
                        null,
                        gcsStorageService.createPublicThumbnailUrl(v.getThumbnailPath()),
                        Integer.valueOf(1).equals(v.getFollowingRank()) ? SOURCE_FOLLOWING : SOURCE_RECOMMENDED))
                .toList();

        String nextCursor = hasNext
                ? RecommendationCursorCodec.encode(RecommendationCursor.from(pageVideos.get(pageVideos.size() - 1)))
                : null;
        return CursorPageResponse.of(content, nextCursor, hasNext);
    }

    private void recordImpressions(Long userId, List<Video> pageVideos) {
        if (pageVideos.isEmpty()) {
            return;
        }
        recommendationInteractionMapper.upsertImpressions(
                userId,
                pageVideos.stream().map(Video::getId).toList());
    }

    @Override
    public List<RecommendedGymResponse> getNearbyGyms(Long userId, double lat, double lng, double radiusKm, int size) {
        validateNearbyGymRequest(lat, lng, radiusKm);
        return recommendationMapper.findNearbyGyms(userId, lat, lng, radiusKm, size)
                .stream()
                .map(gym -> {
                    Map<String, DayHours> businessHours =
                            operatingStatusResolver.parseBusinessHours(gym.getBusinessHours());
                    return RecommendedGymResponse.from(
                            gym,
                            profileImageUrlResolver.resolve(gym.getThumbnailUrl()),
                            businessHours,
                            operatingStatusResolver.isOpenNow(businessHours),
                            Boolean.TRUE.equals(gym.getFavorite()));
                })
                .toList();
    }

    private void validateNearbyGymRequest(double lat, double lng, double radiusKm) {
        if (!Double.isFinite(lat) || lat < MIN_LAT || lat > MAX_LAT) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "위도는 -90~90 범위여야 합니다.");
        }
        if (!Double.isFinite(lng) || lng < MIN_LNG || lng > MAX_LNG) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "경도는 -180~180 범위여야 합니다.");
        }
        if (!Double.isFinite(radiusKm) || radiusKm <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "반경은 0보다 커야 합니다.");
        }
    }
}
