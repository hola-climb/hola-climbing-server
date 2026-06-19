package com.holaclimbing.server.domain.gym.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.favorite.mapper.FavoriteMapper;
import com.holaclimbing.server.domain.gym.domain.Gym;
import com.holaclimbing.server.domain.gym.dto.DayHours;
import com.holaclimbing.server.domain.gym.dto.request.CreateGymRequest;
import com.holaclimbing.server.domain.gym.dto.request.UpdateBusinessHoursRequest;
import com.holaclimbing.server.domain.gym.dto.response.CreateGymResponse;
import com.holaclimbing.server.domain.gym.dto.response.GymDetailResponse;
import com.holaclimbing.server.domain.gym.dto.response.GymGradeResponse;
import com.holaclimbing.server.domain.gym.dto.response.GymSummaryResponse;
import com.holaclimbing.server.domain.gym.mapper.GymGradeMapper;
import com.holaclimbing.server.domain.gym.mapper.GymMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class GymServiceImpl implements GymService {

    private final GymMapper gymMapper;
    private final GymGradeMapper gymGradeMapper;
    private final FavoriteMapper favoriteMapper;
    private final ObjectMapper objectMapper;
    private final GymProfileImageUrlResolver profileImageUrlResolver;
    private final GymOperatingStatusResolver operatingStatusResolver;

    @Override
    public PageResponse<GymSummaryResponse> searchGyms(String keyword, String region, int page, int size, Long viewerId) {
        String normalizedKeyword = normalizeLikeKeyword(keyword);
        String normalizedRegion = normalizeText(region);
        long total = gymMapper.countSearch(normalizedKeyword, normalizedRegion);
        List<GymSummaryResponse> content = gymMapper.search(normalizedKeyword, normalizedRegion, size, page * size, viewerId)
                .stream()
                .map(this::toSummaryResponse)
                .toList();
        return PageResponse.of(content, page, size, total);
    }

    @Override
    public List<GymSummaryResponse> findNearbyGyms(double lat, double lng, double radiusKm, int size, Long viewerId) {
        if (lat < -90 || lat > 90) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "위도는 -90~90 범위여야 합니다.");
        }
        if (lng < -180 || lng > 180) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "경도는 -180~180 범위여야 합니다.");
        }
        return gymMapper.findNearby(lat, lng, radiusKm, size, viewerId)
                .stream()
                .map(this::toSummaryResponse)
                .toList();
    }

    @Override
    public GymDetailResponse getGymDetail(Long gymId, Long viewerId) {
        Gym gym = gymMapper.findById(gymId);
        if (gym == null) {
            throw new BusinessException(ErrorCode.GYM_NOT_FOUND);
        }
        boolean isFavorite = viewerId != null && favoriteMapper.exists(viewerId, gymId);
        return GymDetailResponse.of(gym, operatingStatusResolver.parseBusinessHours(gym.getBusinessHours()),
                profileImageUrlResolver.resolve(gym.getThumbnailUrl()), isFavorite);
    }

    @Override
    public List<GymGradeResponse> getGrades(Long gymId) {
        requireGym(gymId);
        return gymGradeMapper.findActiveByGymId(gymId).stream()
                .map(GymGradeResponse::from)
                .toList();
    }

    @Override
    @Transactional
    public CreateGymResponse suggestGym(Long userId, CreateGymRequest request) {
        Gym gym = Gym.builder()
                .name(request.name())
                .address(request.address())
                .lat(request.lat())
                .lng(request.lng())
                .description(request.description())
                .phone(request.phone())
                .website(request.website())
                .businessHours(writeBusinessHours(request.businessHours()))
                .regionCode(request.regionCode())
                .status("pending")
                .createdBy(userId)
                .build();
        gymMapper.insertGym(gym);
        return CreateGymResponse.of(gym);
    }

    @Override
    @Transactional
    public GymDetailResponse updateBusinessHours(Long gymId, Long userId, UpdateBusinessHoursRequest request) {
        // pending 암장도 본인이 만든 거면 수정 가능 (검토 전 정정 시나리오).
        Gym gym = gymMapper.findByIdIncludingPending(gymId);
        if (gym == null) {
            throw new BusinessException(ErrorCode.GYM_NOT_FOUND);
        }
        // 운영시간 변경 권한 — 등록 제안자(또는 향후 ADMIN/GYM_STAFF 역할)만 허용.
        // 임시 모델: 제안자가 없는(legacy) 암장은 누구도 수정 불가 → 운영 도구로 처리.
        if (gym.getCreatedBy() == null || !gym.getCreatedBy().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        gymMapper.updateBusinessHours(gymId, writeBusinessHours(request.businessHours()));
        // active 상태가 아니면 getGymDetail이 404를 낸다. 상세 응답 대신 본인이 본 직전 상태로 응답.
        return gym.getStatus() != null && "active".equals(gym.getStatus())
                ? getGymDetail(gymId, userId)
                : pendingDetail(gymMapper.findByIdIncludingPending(gymId));
    }

    /** pending 상태 암장의 detail. getGymDetail의 active 필터를 우회. */
    private GymDetailResponse pendingDetail(Gym gym) {
        return GymDetailResponse.of(gym, operatingStatusResolver.parseBusinessHours(gym.getBusinessHours()),
                profileImageUrlResolver.resolve(gym.getThumbnailUrl()));
    }

    private GymSummaryResponse toSummaryResponse(Gym gym) {
        Map<String, DayHours> businessHours = operatingStatusResolver.parseBusinessHours(gym.getBusinessHours());
        return GymSummaryResponse.from(
                gym,
                profileImageUrlResolver.resolve(gym.getThumbnailUrl()),
                businessHours,
                operatingStatusResolver.isOpenNow(businessHours),
                Boolean.TRUE.equals(gym.getFavorite()));
    }

    private void requireGym(Long gymId) {
        if (gymMapper.findById(gymId) == null) {
            throw new BusinessException(ErrorCode.GYM_NOT_FOUND);
        }
    }

    /** 요일별 운영시간 맵을 JSONB 저장용 JSON 문자열로 직렬화. null이면 null. */
    private String writeBusinessHours(Map<String, DayHours> businessHours) {
        if (businessHours == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(businessHours);
        } catch (Exception e) {
            throw new IllegalStateException("business_hours 직렬화 실패", e);
        }
    }

    private String normalizeText(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeLikeKeyword(String keyword) {
        String normalized = normalizeText(keyword);
        if (normalized == null) {
            return null;
        }
        return normalized
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
