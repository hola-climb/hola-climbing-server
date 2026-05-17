package com.holaclimbing.server.domain.gym.service;

import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.gym.dto.response.GymDetailResponse;
import com.holaclimbing.server.domain.gym.dto.response.GymSummaryResponse;

import java.util.List;

public interface GymService {

    /** 이름·지역으로 암장 검색. */
    PageResponse<GymSummaryResponse> searchGyms(String keyword, String region, int page, int size);

    /** 좌표 기준 반경 내 암장을 가까운 순으로 조회. */
    List<GymSummaryResponse> findNearbyGyms(double lat, double lng, double radiusKm, int size);

    /** 암장 상세 조회 (사진 포함). */
    GymDetailResponse getGymDetail(Long gymId);
}
