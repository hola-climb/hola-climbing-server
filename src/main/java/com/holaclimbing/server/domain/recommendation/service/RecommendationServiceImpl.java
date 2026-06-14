package com.holaclimbing.server.domain.recommendation.service;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.common.response.CursorPageResponse;
import com.holaclimbing.server.domain.recommendation.dto.RecommendationCursor;
import com.holaclimbing.server.domain.recommendation.dto.RecommendationCursorCodec;
import com.holaclimbing.server.domain.recommendation.dto.response.RecommendedGymResponse;
import com.holaclimbing.server.domain.recommendation.dto.response.RecommendedVideoResponse;
import com.holaclimbing.server.domain.recommendation.mapper.RecommendationMapper;
import com.holaclimbing.server.domain.video.domain.Video;
import com.holaclimbing.server.infrastructure.gcs.GcsStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private static final String SOURCE_FOLLOWING = "following";
    private static final String SOURCE_RECOMMENDED = "recommended";
    private static final double MIN_LAT = -90.0;
    private static final double MAX_LAT = 90.0;
    private static final double MIN_LNG = -180.0;
    private static final double MAX_LNG = 180.0;

    private final RecommendationMapper recommendationMapper;
    private final GcsStorageService gcsStorageService;

    @Override
    public CursorPageResponse<RecommendedVideoResponse> getVideoFeed(Long userId, String cursor, int size) {
        RecommendationCursor decodedCursor = RecommendationCursorCodec.decode(cursor);
        List<Video> videos = recommendationMapper.findFeedVideos(userId, decodedCursor, size + 1);
        boolean hasNext = videos.size() > size;
        List<Video> pageVideos = hasNext ? videos.subList(0, size) : videos;

        List<RecommendedVideoResponse> content = pageVideos
                .stream()
                .map(v -> RecommendedVideoResponse.of(v,
                        gcsStorageService.createReadUrl(v.getGcsPath()),
                        gcsStorageService.createReadUrl(v.getThumbnailPath()),
                        Integer.valueOf(1).equals(v.getFollowingRank()) ? SOURCE_FOLLOWING : SOURCE_RECOMMENDED))
                .toList();

        String nextCursor = hasNext
                ? RecommendationCursorCodec.encode(RecommendationCursor.from(pageVideos.get(pageVideos.size() - 1)))
                : null;
        return CursorPageResponse.of(content, nextCursor, hasNext);
    }

    @Override
    public List<RecommendedGymResponse> getNearbyGyms(Long userId, double lat, double lng, double radiusKm, int size) {
        validateNearbyGymRequest(lat, lng, radiusKm);
        return recommendationMapper.findNearbyGyms(userId, lat, lng, radiusKm, size)
                .stream()
                .map(RecommendedGymResponse::from)
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
