package com.holaclimbing.server.domain.admin.mapper;

import com.holaclimbing.server.domain.admin.domain.AdminAuditLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminAuditLogMapper {

    void insert(AdminAuditLog log);

    List<AdminAuditLog> search(@Param("targetType") String targetType,
                               @Param("targetId") Long targetId,
                               @Param("adminId") Long adminId,
                               @Param("size") int size,
                               @Param("offset") int offset);

    long countSearch(@Param("targetType") String targetType,
                     @Param("targetId") Long targetId,
                     @Param("adminId") Long adminId);
}
