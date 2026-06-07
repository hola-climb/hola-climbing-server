package com.holaclimbing.server.domain.admin.dto.request;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

public record AdminGymImportRequest(
        @NotEmpty List<AdminGymImportRow> rows
) {
}
