package com.holaclimbing.server.domain.admin.mapper;

import com.holaclimbing.server.domain.report.domain.Report;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminReportMapper {

    Report findById(Long reportId);

    List<Report> search(@Param("status") String status,
                        @Param("targetType") String targetType,
                        @Param("category") String category,
                        @Param("size") int size,
                        @Param("offset") int offset);

    long countSearch(@Param("status") String status,
                     @Param("targetType") String targetType,
                     @Param("category") String category);

    int updateStatus(@Param("reportId") Long reportId,
                     @Param("status") String status,
                     @Param("reviewedBy") Long reviewedBy);

    int updatePendingByTarget(@Param("targetType") String targetType,
                              @Param("targetId") Long targetId,
                              @Param("status") String status,
                              @Param("reviewedBy") Long reviewedBy);
}
