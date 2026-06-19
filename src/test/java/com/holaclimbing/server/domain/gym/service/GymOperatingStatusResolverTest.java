package com.holaclimbing.server.domain.gym.service;

import com.fasterxml.jackson.databind.json.JsonMapper;
import com.holaclimbing.server.domain.gym.dto.DayHours;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class GymOperatingStatusResolverTest {

    @Test
    void isOpenNow_returnsTrueWhenCurrentTimeIsWithinTodayHours() {
        GymOperatingStatusResolver resolver = resolverAtKst("2026-06-19T13:00:00Z");

        boolean open = resolver.isOpenNow(Map.of("fri", new DayHours("21:00", "23:00")));

        assertThat(open).isTrue();
    }

    @Test
    void isOpenNow_returnsFalseWhenTodayHoursAreMissing() {
        GymOperatingStatusResolver resolver = resolverAtKst("2026-06-19T13:00:00Z");

        boolean open = resolver.isOpenNow(Map.of("thu", new DayHours("21:00", "23:00")));

        assertThat(open).isFalse();
    }

    @Test
    void isOpenNow_supportsOvernightHoursFromPreviousDay() {
        GymOperatingStatusResolver resolver = resolverAtKst("2026-06-18T16:30:00Z");

        boolean open = resolver.isOpenNow(Map.of("thu", new DayHours("22:00", "02:00")));

        assertThat(open).isTrue();
    }

    @Test
    void parseBusinessHours_returnsEmptyMapForInvalidJson() {
        GymOperatingStatusResolver resolver = resolverAtKst("2026-06-19T13:00:00Z");

        Map<String, DayHours> businessHours = resolver.parseBusinessHours("{not-json");

        assertThat(businessHours).isEmpty();
    }

    private GymOperatingStatusResolver resolverAtKst(String instant) {
        return new GymOperatingStatusResolver(
                JsonMapper.builder().build(),
                Clock.fixed(Instant.parse(instant), ZoneId.of("Asia/Seoul")));
    }
}
