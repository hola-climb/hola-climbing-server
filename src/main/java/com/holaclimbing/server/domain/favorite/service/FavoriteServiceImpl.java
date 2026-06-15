package com.holaclimbing.server.domain.favorite.service;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.favorite.mapper.FavoriteMapper;
import com.holaclimbing.server.domain.gym.dto.response.GymSummaryResponse;
import com.holaclimbing.server.domain.gym.mapper.GymMapper;
import com.holaclimbing.server.domain.gym.service.GymProfileImageUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class FavoriteServiceImpl implements FavoriteService {

    private final FavoriteMapper favoriteMapper;
    private final GymMapper gymMapper;
    private final GymProfileImageUrlResolver profileImageUrlResolver;

    @Override
    @Transactional
    public void addFavorite(Long userId, Long gymId) {
        if (gymMapper.findById(gymId) == null) {
            throw new BusinessException(ErrorCode.GYM_NOT_FOUND);
        }
        if (favoriteMapper.exists(userId, gymId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 즐겨찾기한 암장입니다.");
        }
        favoriteMapper.insert(userId, gymId);
    }

    @Override
    @Transactional
    public void removeFavorite(Long userId, Long gymId) {
        favoriteMapper.delete(userId, gymId);
    }

    @Override
    public PageResponse<GymSummaryResponse> getFavoriteGyms(Long userId, int page, int size) {
        long total = favoriteMapper.countByUser(userId);
        List<GymSummaryResponse> content = favoriteMapper.findFavoriteGyms(userId, size, page * size)
                .stream()
                .map(gym -> GymSummaryResponse.from(gym, profileImageUrlResolver.resolve(gym.getThumbnailUrl())))
                .toList();
        return PageResponse.of(content, page, size, total);
    }
}
