package com.holaclimbing.server.domain.gym.dto;

/**
 * 하루치 운영시간. open/close는 "HH:mm" 형식 문자열.
 * 휴무일은 business_hours 맵에서 해당 요일 값을 null로 둔다.
 */
public record DayHours(
        String open,
        String close
) {
}
