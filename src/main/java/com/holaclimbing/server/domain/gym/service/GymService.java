package com.holaclimbing.server.domain.gym.service;

import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.gym.dto.request.CreateGymRequest;
import com.holaclimbing.server.domain.gym.dto.request.UpdateBusinessHoursRequest;
import com.holaclimbing.server.domain.gym.dto.response.CreateGymResponse;
import com.holaclimbing.server.domain.gym.dto.response.GymDetailResponse;
import com.holaclimbing.server.domain.gym.dto.response.GymGradeResponse;
import com.holaclimbing.server.domain.gym.dto.response.GymSummaryResponse;

import java.util.List;

public interface GymService {

    /** 이름·지역으로 암장 검색. */
    PageResponse<GymSummaryResponse> searchGyms(String keyword, String region, int page, int size);

    /** 좌표 기준 반경 내 암장을 가까운 순으로 조회. */
    List<GymSummaryResponse> findNearbyGyms(double lat, double lng, double radiusKm, int size);

    /** 암장 상세 조회. */
    GymDetailResponse getGymDetail(Long gymId);

    /** 암장별 활성 난이도 목록 조회. */
    List<GymGradeResponse> getGrades(Long gymId);

    /** 암장 등록 제안 (status='pending'으로 등록). */
    CreateGymResponse suggestGym(Long userId, CreateGymRequest request);

    /** 암장 요일별 운영시간 수정 (등록 제안자만 허용). 갱신된 암장 상세를 반환한다. */
    GymDetailResponse updateBusinessHours(Long gymId, Long userId, UpdateBusinessHoursRequest request);
}
