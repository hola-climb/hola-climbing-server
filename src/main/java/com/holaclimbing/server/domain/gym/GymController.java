package com.holaclimbing.server.domain.gym;

import static com.holaclimbing.server.common.exception.error.ErrorCode.*;

import com.holaclimbing.server.common.exception.docs.ApiErrorCodes;
import com.holaclimbing.server.common.response.ApiResponse;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.gym.dto.request.CreateGymRequest;
import com.holaclimbing.server.domain.gym.dto.request.UpdateBusinessHoursRequest;
import com.holaclimbing.server.domain.gym.dto.response.CreateGymResponse;
import com.holaclimbing.server.domain.gym.dto.response.GymDetailResponse;
import com.holaclimbing.server.domain.gym.dto.response.GymGradeResponse;
import com.holaclimbing.server.domain.gym.dto.response.GymSummaryResponse;
import com.holaclimbing.server.domain.gym.service.GymService;
import com.holaclimbing.server.domain.video.dto.response.VideoSummaryResponse;
import com.holaclimbing.server.domain.video.service.VideoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 암장 조회 API. 모두 공개(비로그인 허용) 엔드포인트.
 */
@RestController
@RequestMapping("/api/gyms")
@RequiredArgsConstructor
@Validated
public class GymController {

    private final GymService gymService;
    private final VideoService videoService;

    @GetMapping
    public ApiResponse<PageResponse<GymSummaryResponse>> searchGyms(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, name = "q") String query,
            @RequestParam(required = false) String name,
            @RequestParam(required = false) String region,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
        return ApiResponse.success(gymService.searchGyms(firstNonBlank(keyword, query, name), region, page, size));
    }

    @ApiErrorCodes({INVALID_INPUT})
    @GetMapping("/nearby")
    public ApiResponse<List<GymSummaryResponse>> findNearbyGyms(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5") @Positive double radius,
            @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
        return ApiResponse.success(gymService.findNearbyGyms(lat, lng, radius, size));
    }

    @ApiErrorCodes({GYM_NOT_FOUND})
    @GetMapping("/{gymId}")
    public ApiResponse<GymDetailResponse> getGymDetail(@PathVariable Long gymId) {
        return ApiResponse.success(gymService.getGymDetail(gymId));
    }

    @ApiErrorCodes({GYM_NOT_FOUND})
    @GetMapping("/{gymId}/grades")
    public ApiResponse<List<GymGradeResponse>> getGymGrades(@PathVariable Long gymId) {
        return ApiResponse.success(gymService.getGrades(gymId));
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    @GetMapping("/{gymId}/videos")
    public ApiResponse<PageResponse<VideoSummaryResponse>> getGymVideos(
            @PathVariable Long gymId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Positive @Max(100) int size,
            @AuthenticationPrincipal Long viewerId) {
        return ApiResponse.success(videoService.getGymVideos(gymId, page, size, viewerId));
    }

    /** 암장 등록 제안 (인증 필요). status='pending'으로 등록된다. */
    @PostMapping
    public ResponseEntity<ApiResponse<CreateGymResponse>> suggestGym(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateGymRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(gymService.suggestGym(userId, request)));
    }

    /** 암장 요일별 운영시간 수정 (등록 제안자만 허용). */
    @ApiErrorCodes({GYM_NOT_FOUND, FORBIDDEN})
    @PatchMapping("/{gymId}/business-hours")
    public ApiResponse<GymDetailResponse> updateBusinessHours(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gymId,
            @Valid @RequestBody UpdateBusinessHoursRequest request) {
        return ApiResponse.success(gymService.updateBusinessHours(gymId, userId, request));
    }
}
