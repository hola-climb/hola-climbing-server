package com.holaclimbing.server.domain.admin.service;

import com.holaclimbing.server.domain.admin.dto.response.AdminDashboardResponse;
import com.holaclimbing.server.domain.admin.mapper.AdminDashboardMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminDashboardServiceImpl implements AdminDashboardService {

    private final AdminDashboardMapper adminDashboardMapper;

    @Override
    @Transactional(readOnly = true)
    public AdminDashboardResponse getDashboard() {
        return new AdminDashboardResponse(
                adminDashboardMapper.countPendingGyms(),
                adminDashboardMapper.countPendingReports(),
                adminDashboardMapper.countFailedAnalysisVideos(),
                adminDashboardMapper.countNewUsersToday());
    }
}
