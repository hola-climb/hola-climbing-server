package com.holaclimbing.server.domain.gym;

import com.holaclimbing.server.common.response.ApiResponse;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.gym.dto.request.CreateGymPhotoRequest;
import com.holaclimbing.server.domain.gym.dto.request.CreateGymRequest;
import com.holaclimbing.server.domain.gym.dto.request.UpdateBusinessHoursRequest;
import com.holaclimbing.server.domain.gym.dto.response.CreateGymResponse;
import com.holaclimbing.server.domain.gym.dto.response.GymDetailResponse;
import com.holaclimbing.server.domain.gym.dto.response.GymPhotoResponse;
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
            @RequestParam(required = false) String region,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
        return ApiResponse.success(gymService.searchGyms(keyword, region, page, size));
    }

    @GetMapping("/nearby")
    public ApiResponse<List<GymSummaryResponse>> findNearbyGyms(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5") @Positive double radius,
            @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
        return ApiResponse.success(gymService.findNearbyGyms(lat, lng, radius, size));
    }

    @GetMapping("/{gymId}")
    public ApiResponse<GymDetailResponse> getGymDetail(@PathVariable Long gymId) {
        return ApiResponse.success(gymService.getGymDetail(gymId));
    }

    @GetMapping("/{gymId}/videos")
    public ApiResponse<PageResponse<VideoSummaryResponse>> getGymVideos(
            @PathVariable Long gymId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
        return ApiResponse.success(videoService.getGymVideos(gymId, page, size));
    }

    /** 암장 등록 제안 (인증 필요). status='pending'으로 등록된다. */
    @PostMapping
    public ResponseEntity<ApiResponse<CreateGymResponse>> suggestGym(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateGymRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(gymService.suggestGym(userId, request)));
    }

    /** 암장 사진 업로드 (인증 필요). */
    @PostMapping("/{gymId}/photos")
    public ResponseEntity<ApiResponse<GymPhotoResponse>> uploadGymPhoto(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long gymId,
            @Valid @RequestBody CreateGymPhotoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(gymService.uploadPhoto(userId, gymId, request)));
    }

    /** 암장 사진 목록 조회 (공개). */
    @GetMapping("/{gymId}/photos")
    public ApiResponse<List<GymPhotoResponse>> getGymPhotos(@PathVariable Long gymId) {
        return ApiResponse.success(gymService.getPhotos(gymId));
    }

    /** 암장 요일별 운영시간 수정 (인증 필요). */
    @PatchMapping("/{gymId}/business-hours")
    public ApiResponse<GymDetailResponse> updateBusinessHours(
            @PathVariable Long gymId,
            @Valid @RequestBody UpdateBusinessHoursRequest request) {
        return ApiResponse.success(gymService.updateBusinessHours(gymId, request));
    }
}
