package com.holaclimbing.server.domain.stats.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.domain.stats.domain.DynamicSegmentCounts;
import com.holaclimbing.server.domain.stats.domain.GymRankingRow;
import com.holaclimbing.server.domain.stats.domain.Stats;
import com.holaclimbing.server.domain.stats.dto.GymRankingCursor;
import com.holaclimbing.server.domain.stats.dto.GymRankingCursorCodec;
import com.holaclimbing.server.domain.stats.dto.response.GymRankingResponse;
import com.holaclimbing.server.domain.stats.dto.response.TechniqueStatsResponse;
import com.holaclimbing.server.domain.stats.dto.response.UserStatsResponse;
import com.holaclimbing.server.domain.stats.mapper.StatsMapper;
import com.holaclimbing.server.domain.user.domain.User;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class StatsServiceImpl implements StatsService {

    private static final TypeReference<Map<String, Integer>> TECHNIQUE_COUNTS_TYPE =
            new TypeReference<>() {
            };
    private static final int MAX_RANKING_LIMIT = 50;
    private static final String RANKING_SORT = "mostVisited";

    private final StatsMapper statsMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    @Override
    public UserStatsResponse getUserStats(Long userId) {
        requireActiveUser(userId);
        // 분석 세그먼트 dynamic/static 집계는 user_stats 행이 없어도 의미가 있으므로 항상 조회.
        DynamicSegmentCounts dynamicCounts = statsMapper.findDynamicSegmentCountsByUserId(userId);
        Stats stats = statsMapper.findByUserId(userId);
        if (stats == null) {
            return UserStatsResponse.empty(userId, dynamicCounts);
        }
        return UserStatsResponse.of(stats,
                parseTechniqueCounts(stats.getTechniqueCounts()),
                dynamicCounts);
    }

    @Override
    public TechniqueStatsResponse getTechniqueStats(Long userId) {
        requireActiveUser(userId);
        Stats stats = statsMapper.findByUserId(userId);
        Map<String, Integer> counts = stats == null
                ? Map.of() : parseTechniqueCounts(stats.getTechniqueCounts());
        String mostUsed = counts.entrySet().stream()
                .max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        String leastUsed = counts.entrySet().stream()
                .min(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
        return new TechniqueStatsResponse(counts, mostUsed, leastUsed);
    }

    @Override
    public GymRankingResponse getMyGymRankings(Long userId, YearMonth month, String cursor, int limit) {
        requireActiveUser(userId);
        int pageSize = Math.min(limit, MAX_RANKING_LIMIT);
        GymRankingCursor decodedCursor = GymRankingCursorCodec.decode(cursor);
        LocalDate from = month == null ? null : month.atDay(1);
        LocalDate to = month == null ? null : month.atEndOfMonth();

        List<GymRankingRow> rows = statsMapper.findGymRankings(
                userId,
                from,
                to,
                decodedCursor == null ? null : decodedCursor.visitCount(),
                decodedCursor == null ? null : decodedCursor.latestVisitDate(),
                decodedCursor == null ? null : decodedCursor.gymId(),
                pageSize + 1);
        boolean hasNext = rows.size() > pageSize;
        List<GymRankingRow> pageRows = hasNext ? rows.subList(0, pageSize) : rows;
        int rankOffset = decodedCursor == null ? 0 : decodedCursor.rank();
        List<GymRankingResponse.Item> content = toRankingItems(pageRows, rankOffset);
        String nextCursor = hasNext && !content.isEmpty()
                ? encodeNextCursor(pageRows.get(pageRows.size() - 1), content.get(content.size() - 1).rank())
                : null;
        return new GymRankingResponse(
                month == null ? null : month.toString(),
                month == null ? "all" : "monthly",
                RANKING_SORT,
                content,
                nextCursor,
                hasNext);
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

    private List<GymRankingResponse.Item> toRankingItems(List<GymRankingRow> rows, int rankOffset) {
        return java.util.stream.IntStream.range(0, rows.size())
                .mapToObj(index -> {
                    GymRankingRow row = rows.get(index);
                    return new GymRankingResponse.Item(
                            rankOffset + index + 1,
                            row.getGymId(),
                            row.getGymName(),
                            row.getVisitCount(),
                            row.getLatestVisitDate());
                })
                .toList();
    }

    private String encodeNextCursor(GymRankingRow row, int rank) {
        return GymRankingCursorCodec.encode(new GymRankingCursor(
                row.getVisitCount(),
                row.getLatestVisitDate(),
                row.getGymId(),
                rank));
    }

    private void requireActiveUser(Long userId) {
        User user = userMapper.findById(userId);
        if (user == null || !"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }
}
