package com.holaclimbing.server.domain.gym.service;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.gym.domain.Gym;
import com.holaclimbing.server.domain.gym.dto.response.GymDetailResponse;
import com.holaclimbing.server.domain.gym.dto.response.GymPhotoResponse;
import com.holaclimbing.server.domain.gym.dto.response.GymSummaryResponse;
import com.holaclimbing.server.domain.gym.mapper.GymMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GymServiceImpl implements GymService {

    private final GymMapper gymMapper;

    @Override
    public PageResponse<GymSummaryResponse> searchGyms(String keyword, String region, int page, int size) {
        long total = gymMapper.countSearch(keyword, region);
        List<GymSummaryResponse> content = gymMapper.search(keyword, region, size, page * size)
                .stream().map(GymSummaryResponse::from).toList();
        return PageResponse.of(content, page, size, total);
    }

    @Override
    public List<GymSummaryResponse> findNearbyGyms(double lat, double lng, double radiusKm, int size) {
        if (lat < -90 || lat > 90) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "위도는 -90~90 범위여야 합니다.");
        }
        if (lng < -180 || lng > 180) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "경도는 -180~180 범위여야 합니다.");
        }
        return gymMapper.findNearby(lat, lng, radiusKm, size)
                .stream().map(GymSummaryResponse::from).toList();
    }

    @Override
    public GymDetailResponse getGymDetail(Long gymId) {
        Gym gym = gymMapper.findById(gymId);
        if (gym == null) {
            throw new BusinessException(ErrorCode.GYM_NOT_FOUND);
        }
        List<GymPhotoResponse> photos = gymMapper.findPhotosByGymId(gymId)
                .stream().map(GymPhotoResponse::from).toList();
        return GymDetailResponse.of(gym, photos);
    }
}
