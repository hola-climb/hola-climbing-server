package com.holaclimbing.server.domain.admin;

import com.holaclimbing.server.common.response.ApiResponse;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.admin.dto.request.AdminGymGradeReplaceRequest;
import com.holaclimbing.server.domain.admin.dto.request.AdminGymImportRequest;
import com.holaclimbing.server.domain.admin.dto.request.AdminGymUpsertRequest;
import com.holaclimbing.server.domain.admin.dto.request.AdminReasonRequest;
import com.holaclimbing.server.domain.admin.dto.response.AdminGymImportApplyResponse;
import com.holaclimbing.server.domain.admin.dto.response.AdminGymImportPreviewResponse;
import com.holaclimbing.server.domain.admin.dto.response.AdminGymSearchResponse;
import com.holaclimbing.server.domain.admin.service.AdminGymService;
import com.holaclimbing.server.domain.gym.dto.response.GymDetailResponse;
import com.holaclimbing.server.domain.gym.dto.response.GymGradeResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/admin/gyms")
@RequiredArgsConstructor
@Validated
public class AdminGymController {

    private final AdminGymService adminGymService;

    @GetMapping
    public ApiResponse<PageResponse<AdminGymSearchResponse>> search(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String regionCode,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
        return ApiResponse.success(adminGymService.search(status, keyword, regionCode, page, size));
    }

    @GetMapping("/{gymId}")
    public ApiResponse<GymDetailResponse> getGym(@PathVariable Long gymId) {
        return ApiResponse.success(adminGymService.getGym(gymId));
    }

    @PostMapping
    public ApiResponse<GymDetailResponse> createGym(@AuthenticationPrincipal Long adminId,
                                                    @Valid @RequestBody AdminGymUpsertRequest request) {
        return ApiResponse.success(adminGymService.createGym(adminId, request));
    }

    @PostMapping("/import/preview")
    public ApiResponse<AdminGymImportPreviewResponse> previewImport(
            @Valid @RequestBody AdminGymImportRequest request) {
        return ApiResponse.success(adminGymService.previewImport(request));
    }

    @PostMapping("/import")
    public ApiResponse<AdminGymImportApplyResponse> importGyms(@AuthenticationPrincipal Long adminId,
                                                               @Valid @RequestBody AdminGymImportRequest request) {
        return ApiResponse.success(adminGymService.importGyms(adminId, request));
    }

    @PatchMapping("/{gymId}")
    public ApiResponse<GymDetailResponse> updateGym(@AuthenticationPrincipal Long adminId,
                                                    @PathVariable Long gymId,
                                                    @Valid @RequestBody AdminGymUpsertRequest request) {
        return ApiResponse.success(adminGymService.updateGym(adminId, gymId, request));
    }

    @PostMapping("/{gymId}/approve")
    public ApiResponse<GymDetailResponse> approve(@AuthenticationPrincipal Long adminId,
                                                  @PathVariable Long gymId,
                                                  @Valid @RequestBody AdminReasonRequest request) {
        return ApiResponse.success(adminGymService.approveGym(adminId, gymId, request));
    }

    @PostMapping("/{gymId}/reject")
    public ApiResponse<GymDetailResponse> reject(@AuthenticationPrincipal Long adminId,
                                                 @PathVariable Long gymId,
                                                 @Valid @RequestBody AdminReasonRequest request) {
        return ApiResponse.success(adminGymService.rejectGym(adminId, gymId, request));
    }

    @PostMapping("/{gymId}/close")
    public ApiResponse<GymDetailResponse> close(@AuthenticationPrincipal Long adminId,
                                                @PathVariable Long gymId,
                                                @Valid @RequestBody AdminReasonRequest request) {
        return ApiResponse.success(adminGymService.closeGym(adminId, gymId, request));
    }

    @PutMapping("/{gymId}/grades")
    public ApiResponse<List<GymGradeResponse>> replaceGrades(@AuthenticationPrincipal Long adminId,
                                                             @PathVariable Long gymId,
                                                             @Valid @RequestBody AdminGymGradeReplaceRequest request) {
        return ApiResponse.success(adminGymService.replaceGrades(adminId, gymId, request));
    }
}
