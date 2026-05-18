package com.holaclimbing.server.domain.recommendation.service;

import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.recommendation.dto.response.RecommendedVideoResponse;
import com.holaclimbing.server.domain.recommendation.mapper.RecommendationMapper;
import com.holaclimbing.server.infrastructure.gcs.GcsStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class RecommendationServiceImpl implements RecommendationService {

    private static final String SOURCE_FOLLOWING = "following";
    private static final String SOURCE_RECOMMENDED = "recommended";

    private final RecommendationMapper recommendationMapper;
    private final GcsStorageService gcsStorageService;

    @Override
    public PageResponse<RecommendedVideoResponse> getVideoFeed(Long userId, int page, int size) {
        long total = recommendationMapper.countFeedVideos(userId);
        Set<Long> followingIds = new HashSet<>(recommendationMapper.findFollowingIds(userId));
        List<RecommendedVideoResponse> content = recommendationMapper
                .findFeedVideos(userId, size, page * size)
                .stream()
                .map(v -> RecommendedVideoResponse.of(v,
                        gcsStorageService.createReadUrl(v.getGcsPath()),
                        followingIds.contains(v.getUserId()) ? SOURCE_FOLLOWING : SOURCE_RECOMMENDED))
                .toList();
        return PageResponse.of(content, page, size, total);
    }
}
