package com.holaclimbing.server.domain.admin;

import com.holaclimbing.server.common.response.ApiResponse;
import com.holaclimbing.server.domain.admin.dto.response.AdminDashboardResponse;
import com.holaclimbing.server.domain.admin.service.AdminDashboardService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @GetMapping
    public ApiResponse<AdminDashboardResponse> getDashboard() {
        return ApiResponse.success(adminDashboardService.getDashboard());
    }
}
