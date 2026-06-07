package com.holaclimbing.server.domain.admin.dto.response;

import java.util.List;

public record AdminGymImportInvalidRowResponse(
        int rowIndex,
        String externalKey,
        List<String> errors
) {
}
