package com.holaclimbing.server.domain.admin.mapper;

import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AdminDashboardMapper {

    long countPendingGyms();

    long countPendingReports();

    long countFailedAnalysisVideos();

    long countNewUsersToday();
}
