package com.holaclimbing.server.domain.admin.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.admin.dto.request.AdminGymGradeReplaceRequest;
import com.holaclimbing.server.domain.admin.dto.request.AdminGymGradeRequest;
import com.holaclimbing.server.domain.admin.dto.request.AdminGymImportRequest;
import com.holaclimbing.server.domain.admin.dto.request.AdminGymImportRow;
import com.holaclimbing.server.domain.admin.dto.request.AdminGymUpsertRequest;
import com.holaclimbing.server.domain.admin.dto.request.AdminReasonRequest;
import com.holaclimbing.server.domain.admin.dto.response.AdminGymImportApplyResponse;
import com.holaclimbing.server.domain.admin.dto.response.AdminGymImportInvalidRowResponse;
import com.holaclimbing.server.domain.admin.dto.response.AdminGymImportPreviewResponse;
import com.holaclimbing.server.domain.admin.dto.response.AdminGymSearchResponse;
import com.holaclimbing.server.domain.admin.mapper.AdminGymMapper;
import com.holaclimbing.server.domain.gym.domain.Gym;
import com.holaclimbing.server.domain.gym.dto.DayHours;
import com.holaclimbing.server.domain.gym.dto.response.GymDetailResponse;
import com.holaclimbing.server.domain.gym.dto.response.GymGradeResponse;
import com.holaclimbing.server.domain.gym.dto.response.GymPhotoResponse;
import com.holaclimbing.server.domain.gym.mapper.GymGradeMapper;
import com.holaclimbing.server.domain.gym.mapper.GymMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class AdminGymServiceImpl implements AdminGymService {

    private static final String STATUS_ACTIVE = "active";
    private static final String STATUS_CLOSED = "closed";
    private static final TypeReference<Map<String, DayHours>> BUSINESS_HOURS_TYPE =
            new TypeReference<>() {
            };

    private final AdminGymMapper adminGymMapper;
    private final GymMapper gymMapper;
    private final GymGradeMapper gymGradeMapper;
    private final AdminAuditService adminAuditService;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional(readOnly = true)
    public PageResponse<AdminGymSearchResponse> search(String status, String keyword, String regionCode,
                                                       int page, int size) {
        long total = adminGymMapper.countSearch(status, keyword, regionCode);
        var content = adminGymMapper.search(status, keyword, regionCode, size, page * size).stream()
                .map(AdminGymSearchResponse::from)
                .toList();
        return PageResponse.of(content, page, size, total);
    }

    @Override
    @Transactional(readOnly = true)
    public GymDetailResponse getGym(Long gymId) {
        return toDetail(requireGymAnyStatus(gymId));
    }

    @Override
    @Transactional
    public GymDetailResponse createGym(Long adminId, AdminGymUpsertRequest request) {
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
                .status(STATUS_ACTIVE)
                .createdBy(adminId)
                .build();
        gymMapper.insertGym(gym);
        Gym after = requireGymAnyStatus(gym.getId());
        adminAuditService.record(adminId, "GYM_CREATE", "gym", gym.getId(), null, null, after);
        return toDetail(after);
    }

    @Override
    @Transactional
    public GymDetailResponse updateGym(Long adminId, Long gymId, AdminGymUpsertRequest request) {
        Gym before = requireGymAnyStatus(gymId);
        adminGymMapper.updateGym(gymId, request.name(), request.address(), request.lat(), request.lng(),
                request.phone(), request.website(), request.description(), writeBusinessHours(request.businessHours()),
                request.regionCode());
        Gym after = requireGymAnyStatus(gymId);
        adminAuditService.record(adminId, "GYM_UPDATE", "gym", gymId, null, before, after);
        return toDetail(after);
    }

    @Override
    @Transactional
    public GymDetailResponse approveGym(Long adminId, Long gymId, AdminReasonRequest request) {
        return changeStatus(adminId, gymId, STATUS_ACTIVE, "GYM_APPROVE", request.reason());
    }

    @Override
    @Transactional
    public GymDetailResponse rejectGym(Long adminId, Long gymId, AdminReasonRequest request) {
        return changeStatus(adminId, gymId, STATUS_CLOSED, "GYM_REJECT", request.reason());
    }

    @Override
    @Transactional
    public GymDetailResponse closeGym(Long adminId, Long gymId, AdminReasonRequest request) {
        return changeStatus(adminId, gymId, STATUS_CLOSED, "GYM_CLOSE", request.reason());
    }

    @Override
    @Transactional
    public List<GymGradeResponse> replaceGrades(Long adminId, Long gymId, AdminGymGradeReplaceRequest request) {
        requireGymAnyStatus(gymId);
        var before = gymGradeMapper.findActiveByGymId(gymId);
        replaceGradesInternal(gymId, request.grades());
        var after = gymGradeMapper.findActiveByGymId(gymId);
        adminAuditService.record(adminId, "GYM_GRADES_REPLACE", "gym", gymId,
                request.reason(), before, after);
        return after.stream().map(GymGradeResponse::from).toList();
    }

    @Override
    @Transactional(readOnly = true)
    public AdminGymImportPreviewResponse previewImport(AdminGymImportRequest request) {
        List<AdminGymImportInvalidRowResponse> invalidRows = validateImport(request);
        int total = request.rows().size();
        return new AdminGymImportPreviewResponse(total, total - invalidRows.size(), invalidRows.size(), invalidRows);
    }

    @Override
    @Transactional
    public AdminGymImportApplyResponse importGyms(Long adminId, AdminGymImportRequest request) {
        List<AdminGymImportInvalidRowResponse> invalidRows = validateImport(request);
        if (!invalidRows.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "유효하지 않은 암장 행이 있습니다.");
        }

        for (AdminGymImportRow row : request.rows()) {
            Gym gym = Gym.builder()
                    .name(row.name())
                    .address(row.address())
                    .lat(row.lat())
                    .lng(row.lng())
                    .description(row.description())
                    .phone(row.phone())
                    .website(row.website())
                    .businessHours(writeBusinessHours(row.businessHours()))
                    .regionCode(row.regionCode())
                    .status(STATUS_ACTIVE)
                    .createdBy(adminId)
                    .build();
            gymMapper.insertGym(gym);
            replaceGradesInternal(gym.getId(), row.grades());
            Gym after = requireGymAnyStatus(gym.getId());
            adminAuditService.record(adminId, "GYM_IMPORT", "gym", gym.getId(),
                    "externalKey=" + row.externalKey(), null, after);
        }
        return new AdminGymImportApplyResponse(request.rows().size());
    }

    private GymDetailResponse changeStatus(Long adminId, Long gymId, String status, String action, String reason) {
        Gym before = requireGymAnyStatus(gymId);
        adminGymMapper.updateStatus(gymId, status);
        Gym after = requireGymAnyStatus(gymId);
        adminAuditService.record(adminId, action, "gym", gymId, reason, before, after);
        return toDetail(after);
    }

    private Gym requireGymAnyStatus(Long gymId) {
        Gym gym = adminGymMapper.findByIdAnyStatus(gymId);
        if (gym == null) {
            throw new BusinessException(ErrorCode.GYM_NOT_FOUND);
        }
        return gym;
    }

    private void replaceGradesInternal(Long gymId, List<AdminGymGradeRequest> grades) {
        adminGymMapper.deactivateGrades(gymId);
        for (AdminGymGradeRequest grade : grades) {
            adminGymMapper.insertGrade(gymId, grade.label(), grade.difficultyOrder());
        }
    }

    private List<AdminGymImportInvalidRowResponse> validateImport(AdminGymImportRequest request) {
        List<AdminGymImportInvalidRowResponse> invalidRows = new ArrayList<>();
        List<AdminGymImportRow> rows = request.rows();
        for (int i = 0; i < rows.size(); i++) {
            AdminGymImportRow row = rows.get(i);
            List<String> errors = validateRow(row);
            if (!errors.isEmpty()) {
                invalidRows.add(new AdminGymImportInvalidRowResponse(i, row == null ? null : row.externalKey(), errors));
            }
        }
        return invalidRows;
    }

    private List<String> validateRow(AdminGymImportRow row) {
        List<String> errors = new ArrayList<>();
        if (row == null) {
            errors.add("row is required");
            return errors;
        }
        if (row.externalKey() == null || row.externalKey().isBlank()) {
            errors.add("externalKey is required");
        }
        if (row.name() == null || row.name().isBlank()) {
            errors.add("name is required");
        }
        if (row.lat() != null && (row.lat() < -90 || row.lat() > 90)) {
            errors.add("lat must be -90..90");
        }
        if (row.lng() != null && (row.lng() < -180 || row.lng() > 180)) {
            errors.add("lng must be -180..180");
        }
        if (row.grades() == null || row.grades().isEmpty()) {
            errors.add("at least one grade is required");
        } else {
            for (int i = 0; i < row.grades().size(); i++) {
                AdminGymGradeRequest grade = row.grades().get(i);
                if (grade == null) {
                    errors.add("grades[" + i + "] is required");
                    continue;
                }
                if (grade.label() == null || grade.label().isBlank()) {
                    errors.add("grades[" + i + "].label is required");
                }
                if (grade.difficultyOrder() == null) {
                    errors.add("grades[" + i + "].difficultyOrder is required");
                }
            }
        }
        return errors;
    }

    private GymDetailResponse toDetail(Gym gym) {
        List<GymPhotoResponse> photos = gymMapper.findPhotosByGymId(gym.getId())
                .stream()
                .map(GymPhotoResponse::from)
                .toList();
        return GymDetailResponse.of(gym, parseBusinessHours(gym.getBusinessHours()), photos);
    }

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

    private Map<String, DayHours> parseBusinessHours(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, BUSINESS_HOURS_TYPE);
        } catch (Exception e) {
            log.warn("business_hours 파싱 실패: {}", e.getMessage());
            return Map.of();
        }
    }
}
