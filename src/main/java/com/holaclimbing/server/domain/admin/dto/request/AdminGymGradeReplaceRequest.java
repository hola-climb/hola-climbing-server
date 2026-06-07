package com.holaclimbing.server.domain.admin.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;

import java.util.List;

public record AdminGymGradeReplaceRequest(
        @NotEmpty List<@Valid AdminGymGradeRequest> grades,
        @Size(max = 500) String reason
) {
}
