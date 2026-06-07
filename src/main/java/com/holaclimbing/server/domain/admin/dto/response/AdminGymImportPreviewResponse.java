package com.holaclimbing.server.domain.admin.dto.response;

import java.util.List;

public record AdminGymImportPreviewResponse(
        int totalCount,
        int validCount,
        int invalidCount,
        List<AdminGymImportInvalidRowResponse> invalidRows
) {
}
