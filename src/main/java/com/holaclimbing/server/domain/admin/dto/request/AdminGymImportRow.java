package com.holaclimbing.server.domain.admin.dto.request;

import com.holaclimbing.server.domain.gym.dto.DayHours;

import java.util.List;
import java.util.Map;

public record AdminGymImportRow(
        String externalKey,
        String name,
        String address,
        Double lat,
        Double lng,
        String regionCode,
        String phone,
        String website,
        String description,
        Map<String, DayHours> businessHours,
        List<AdminGymGradeRequest> grades
) {
}
