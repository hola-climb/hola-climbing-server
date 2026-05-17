package com.holaclimbing.server.domain.gym;

import com.holaclimbing.server.common.response.ApiResponse;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.gym.dto.response.GymDetailResponse;
import com.holaclimbing.server.domain.gym.dto.response.GymSummaryResponse;
import com.holaclimbing.server.domain.gym.service.GymService;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
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

    @GetMapping
    public ApiResponse<PageResponse<GymSummaryResponse>> searchGyms(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String region,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Positive int size) {
        return ApiResponse.success(gymService.searchGyms(keyword, region, page, size));
    }

    @GetMapping("/nearby")
    public ApiResponse<List<GymSummaryResponse>> findNearbyGyms(
            @RequestParam double lat,
            @RequestParam double lng,
            @RequestParam(defaultValue = "5") @Positive double radius,
            @RequestParam(defaultValue = "20") @Positive int size) {
        return ApiResponse.success(gymService.findNearbyGyms(lat, lng, radius, size));
    }

    @GetMapping("/{gymId}")
    public ApiResponse<GymDetailResponse> getGymDetail(@PathVariable Long gymId) {
        return ApiResponse.success(gymService.getGymDetail(gymId));
    }
}
