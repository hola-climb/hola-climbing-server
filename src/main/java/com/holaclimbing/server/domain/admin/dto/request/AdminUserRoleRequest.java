package com.holaclimbing.server.domain.admin.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AdminUserRoleRequest(
        @NotBlank String role,
        @NotBlank @Size(max = 500) String reason
) {
}
