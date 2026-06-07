package com.holaclimbing.server.domain.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record AdminGymGradeRequest(
        @NotBlank @Size(max = 50) String label,
        @NotNull Integer difficultyOrder
) {
}
