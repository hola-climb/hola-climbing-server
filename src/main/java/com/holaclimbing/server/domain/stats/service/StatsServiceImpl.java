package com.holaclimbing.server.domain.stats.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.domain.stats.domain.Stats;
import com.holaclimbing.server.domain.stats.dto.response.TechniqueStatsResponse;
import com.holaclimbing.server.domain.stats.dto.response.UserStatsResponse;
import com.holaclimbing.server.domain.stats.mapper.StatsMapper;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private static final TypeReference<Map<String, Integer>> TECHNIQUE_COUNTS_TYPE =
            new TypeReference<>() {
            };

    private final StatsMapper statsMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    public UserStatsResponse getUserStats(Long userId) {
        if (userMapper.findById(userId) == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        Stats stats = statsMapper.findByUserId(userId);
        if (stats == null) {
            return UserStatsResponse.empty(userId);
        }
        return UserStatsResponse.of(stats, parseTechniqueCounts(stats.getTechniqueCounts()));
    }

    @Override
    public TechniqueStatsResponse getTechniqueStats(Long userId) {
        if (userMapper.findById(userId) == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        Stats stats = statsMapper.findByUserId(userId);
        Map<String, Integer> counts = stats == null
                ? Map.of() : parseTechniqueCounts(stats.getTechniqueCounts());
        String mostUsed = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        String leastUsed = counts.entrySet().stream()
                .min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        return new TechniqueStatsResponse(counts, mostUsed, leastUsed);
    }

    /** JSONB 문자열({"highstep":12,...})을 Map으로 파싱. 비어 있거나 깨졌으면 빈 Map. */
    private Map<String, Integer> parseTechniqueCounts(String json) {
        if (json == null || json.isBlank()) {
            return Map.of();
        }
        try {
            return objectMapper.readValue(json, TECHNIQUE_COUNTS_TYPE);
        } catch (Exception e) {
            log.warn("technique_counts 파싱 실패: {}", e.getMessage());
            return Map.of();
        }
    }
}
