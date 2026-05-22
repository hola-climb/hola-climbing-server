package com.holaclimbing.server.domain.gym.dto.request;

import com.holaclimbing.server.domain.gym.dto.DayHours;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.Map;

/**
 * 암장 등록 제안 요청. 사용자가 제안하면 status='pending' 상태로 등록된다.
 * businessHours는 요일(mon~sun)별 운영시간 (휴무일은 null), 선택값.
 */
public record CreateGymRequest(
        @NotBlank @Size(max = 100) String name,
        @Size(max = 200) String address,
        Double lat,
        Double lng,
        @Size(max = 30) String phone,
        @Size(max = 300) String website,
        String description,
        Map<String, DayHours> businessHours,
        @Size(max = 20) String regionCode
) {
}
