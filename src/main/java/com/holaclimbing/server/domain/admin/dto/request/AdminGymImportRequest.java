package com.holaclimbing.server.domain.admin.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AdminGymImportRequest(
        @NotEmpty @Size(max = 500) List<@Valid AdminGymImportRow> rows
) {
}
