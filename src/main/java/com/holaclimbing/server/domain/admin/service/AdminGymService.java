package com.holaclimbing.server.domain.admin.service;

import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.admin.dto.request.AdminGymGradeReplaceRequest;
import com.holaclimbing.server.domain.admin.dto.request.AdminGymImportRequest;
import com.holaclimbing.server.domain.admin.dto.request.AdminGymUpsertRequest;
import com.holaclimbing.server.domain.admin.dto.request.AdminReasonRequest;
import com.holaclimbing.server.domain.admin.dto.response.AdminGymImportApplyResponse;
import com.holaclimbing.server.domain.admin.dto.response.AdminGymImportPreviewResponse;
import com.holaclimbing.server.domain.admin.dto.response.AdminGymSearchResponse;
import com.holaclimbing.server.domain.gym.dto.response.GymDetailResponse;
import com.holaclimbing.server.domain.gym.dto.response.GymGradeResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface AdminGymService {

    PageResponse<AdminGymSearchResponse> search(String status, String keyword, String regionCode, int page, int size);

    GymDetailResponse getGym(Long gymId);

    GymDetailResponse createGym(Long adminId, AdminGymUpsertRequest request);

    GymDetailResponse updateGym(Long adminId, Long gymId, AdminGymUpsertRequest request);

    GymDetailResponse uploadProfileImage(Long adminId, Long gymId, MultipartFile image);

    GymDetailResponse approveGym(Long adminId, Long gymId, AdminReasonRequest request);

    GymDetailResponse rejectGym(Long adminId, Long gymId, AdminReasonRequest request);

    GymDetailResponse closeGym(Long adminId, Long gymId, AdminReasonRequest request);

    List<GymGradeResponse> replaceGrades(Long adminId, Long gymId, AdminGymGradeReplaceRequest request);

    AdminGymImportPreviewResponse previewImport(AdminGymImportRequest request);

    AdminGymImportApplyResponse importGyms(Long adminId, AdminGymImportRequest request);
}
