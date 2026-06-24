package com.holaclimbing.server.domain.stats.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.domain.analysis.domain.AnalysisTechniqueCatalog;
import com.holaclimbing.server.domain.stats.MonthlyReportProperties;
import com.holaclimbing.server.domain.stats.domain.MonthlyReport;
import com.holaclimbing.server.domain.stats.domain.MonthlyReportAggregate;
import com.holaclimbing.server.domain.stats.domain.MonthlyReportNarrative;
import com.holaclimbing.server.domain.stats.domain.MonthlyReportRecommendedGym;
import com.holaclimbing.server.domain.stats.domain.MonthlyReportSource;
import com.holaclimbing.server.domain.stats.domain.MonthlyReportStatus;
import com.holaclimbing.server.domain.stats.dto.response.MonthlyReportAvailablePeriodsResponse;
import com.holaclimbing.server.domain.stats.dto.response.MonthlyReportResponse;
import com.holaclimbing.server.domain.stats.mapper.MonthlyReportMapper;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class MonthlyReportServiceImpl implements MonthlyReportService {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");
    private static final TypeReference<Map<String, Integer>> COUNTS_TYPE = new TypeReference<>() {
    };
    private static final TypeReference<List<String>> STRING_LIST_TYPE = new TypeReference<>() {
    };
    private static final Set<String> CANONICAL_TECHNIQUE_SET =
            Set.copyOf(AnalysisTechniqueCatalog.CANONICAL_TECHNIQUES);

    private final MonthlyReportMapper monthlyReportMapper;
    private final UserMapper userMapper;
    private final MonthlyReportProperties properties;
    private final MonthlyReportNarrativeClient narrativeClient;
    private final ObjectMapper objectMapper;

    @Override
    public MonthlyReportResponse getMonthlyReport(Long userId, YearMonth month, Long gymId) {
        requireUser(userId);
        MonthlyReport existing = monthlyReportMapper.findReport(userId, month.toString(), gymId);
        if (existing != null) {
            return toStoredResponse(existing);
        }
        if (!properties.generateOnMiss()) {
            return generatingResponse(month);
        }
        return generateMonthlyReport(userId, month, gymId);
    }

    @Override
    @Transactional
    public MonthlyReportResponse generateMonthlyReport(Long userId, YearMonth month, Long gymId) {
        requireUser(userId);
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();

        MonthlyReportAggregate logAggregate = aggregateOrEmpty(monthlyReportMapper.findLogAggregate(userId, from, to));
        MonthlyReportAggregate videoAggregate =
                aggregateOrEmpty(monthlyReportMapper.findVideoFallbackAggregate(userId, from, to));
        MonthlyReportAggregate styleAggregate =
                aggregateOrEmpty(monthlyReportMapper.findDynamicStaticAggregate(userId, from, to));

        MonthlyReportAggregate sourceAggregate = selectSourceAggregate(logAggregate, videoAggregate);
        Map<String, Integer> techniqueCounts =
                parseTechniqueCounts(monthlyReportMapper.findTechniqueCountsJson(userId, from, to));
        List<String> underusedTechniques = findUnderusedTechniques(techniqueCounts);

        boolean ready = styleAggregate.getVideos() >= properties.minVideos()
                && sourceAggregate.getProblemsSolved() >= properties.minProblems();

        MonthlyReportResponse.Grade grade = gymId == null ? null : findGrade(userId, gymId, month);
        MonthlyReportNarrative narrative = null;
        List<MonthlyReportRecommendedGym> recommendedGyms = List.of();
        if (ready) {
            narrative = narrativeClient.generate(sourceAggregate, techniqueCounts, underusedTechniques);
            if (!underusedTechniques.isEmpty()) {
                recommendedGyms = monthlyReportMapper.findRecommendedGymsByTechniques(underusedTechniques, 3);
            }
        }

        MonthlyReportResponse response = buildResponse(
                month,
                sourceAggregate,
                styleAggregate,
                techniqueCounts,
                underusedTechniques,
                recommendedGyms,
                grade,
                narrative,
                ready
        );
        saveReport(userId, gymId, response);
        return response;
    }

    @Override
    public MonthlyReportAvailablePeriodsResponse getAvailablePeriods(Long userId) {
        requireUser(userId);
        return new MonthlyReportAvailablePeriodsResponse(monthlyReportMapper.findAvailablePeriods(userId));
    }

    private void requireUser(Long userId) {
        if (userMapper.findById(userId) == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
    }

    private MonthlyReportAggregate aggregateOrEmpty(MonthlyReportAggregate aggregate) {
        return aggregate == null ? MonthlyReportAggregate.builder().build() : aggregate;
    }

    private MonthlyReportAggregate selectSourceAggregate(MonthlyReportAggregate logAggregate,
                                                         MonthlyReportAggregate videoAggregate) {
        if (logAggregate.hasSessions()) {
            return logAggregate.withSource(MonthlyReportSource.LOG);
        }
        if (videoAggregate.getVideos() > 0) {
            return videoAggregate.withSource(MonthlyReportSource.VIDEO_FALLBACK);
        }
        return MonthlyReportAggregate.builder()
                .source(MonthlyReportSource.NONE)
                .build();
    }

    private Map<String, Integer> parseTechniqueCounts(String json) {
        try {
            Map<String, Integer> rawCounts = objectMapper.readValue(
                    json == null || json.isBlank() ? "{}" : json,
                    COUNTS_TYPE
            );
            Map<String, Integer> normalized = new LinkedHashMap<>();
            for (String canonical : AnalysisTechniqueCatalog.CANONICAL_TECHNIQUES) {
                normalized.put(canonical, 0);
            }
            for (Map.Entry<String, Integer> entry : rawCounts.entrySet()) {
                String canonical = AnalysisTechniqueCatalog.normalizeTechnique(entry.getKey());
                if (canonical == null || !CANONICAL_TECHNIQUE_SET.contains(canonical)) {
                    continue;
                }
                int count = entry.getValue() == null ? 0 : entry.getValue();
                normalized.merge(canonical, count, Integer::sum);
            }
            normalized.entrySet().removeIf(entry -> entry.getValue() <= 0);
            return normalized;
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.MONTHLY_REPORT_GENERATION_FAILED);
        }
    }

    private List<String> findUnderusedTechniques(Map<String, Integer> techniqueCounts) {
        return AnalysisTechniqueCatalog.CANONICAL_TECHNIQUES.stream()
                .sorted(Comparator.comparingInt(technique -> techniqueCounts.getOrDefault(technique, 0)))
                .limit(2)
                .toList();
    }

    private MonthlyReportResponse.Grade findGrade(Long userId, Long gymId, YearMonth month) {
        MonthlyReportAggregate current = findMonthGrade(userId, gymId, month);
        if (current == null || current.getMaxGrade() == null) {
            return null;
        }
        MonthlyReportAggregate previous = findMonthGrade(userId, gymId, month.minusMonths(1));
        return new MonthlyReportResponse.Grade(
                current.getGradeGymId(),
                current.getGradeGymName(),
                current.getMaxGrade(),
                previous == null ? null : previous.getMaxGrade()
        );
    }

    private MonthlyReportAggregate findMonthGrade(Long userId, Long gymId, YearMonth month) {
        LocalDate from = month.atDay(1);
        LocalDate to = month.atEndOfMonth();
        MonthlyReportAggregate grade = monthlyReportMapper.findGradeFromLogs(userId, gymId, from, to);
        if (grade == null || grade.getMaxGrade() == null) {
            grade = monthlyReportMapper.findGradeFromVideos(userId, gymId, from, to);
        }
        return grade;
    }

    private MonthlyReportResponse buildResponse(YearMonth month,
                                                MonthlyReportAggregate totals,
                                                MonthlyReportAggregate style,
                                                Map<String, Integer> techniqueCounts,
                                                List<String> underusedTechniques,
                                                List<MonthlyReportRecommendedGym> gyms,
                                                MonthlyReportResponse.Grade grade,
                                                MonthlyReportNarrative narrative,
                                                boolean ready) {
        int styleTotal = style.getDynamicCount() + style.getStaticCount();
        MonthlyReportResponse.Metrics metrics = new MonthlyReportResponse.Metrics(
                totals.getSessions(),
                style.getVideos(),
                totals.getProblemsSolved(),
                totals.getGymsVisited(),
                totals.getPrimaryGymId(),
                totals.getPrimaryGymName(),
                style.getDynamicCount(),
                style.getStaticCount(),
                ratio(style.getDynamicCount(), styleTotal),
                ratio(style.getStaticCount(), styleTotal),
                techniqueCounts
        );

        if (!ready) {
            return new MonthlyReportResponse(
                    month.toString(),
                    MonthlyReportStatus.INSUFFICIENT_DATA.value(),
                    totals.getSource().value(),
                    now(),
                    metrics,
                    grade,
                    null,
                    null,
                    List.of(),
                    null,
                    new MonthlyReportResponse.Requirement(properties.minVideos(), properties.minProblems())
            );
        }

        MonthlyReportResponse.Tip tip = new MonthlyReportResponse.Tip(
                "underusedTechnique",
                underusedTechniques,
                "이번 달에 적게 나온 기술을 다음 달 문제 선택 기준으로 삼아보면 좋아요."
        );
        MonthlyReportResponse.Goal goal = new MonthlyReportResponse.Goal(
                "부족했던 기술 문제 5개 기록하기",
                "specificTechnique",
                5,
                underusedTechniques,
                "완등 여부와 별개로 기록을 남기면 다음 리포트에서 변화가 더 또렷해져요."
        );
        List<MonthlyReportResponse.RecommendedGym> recommended = gyms.stream()
                .map(gym -> new MonthlyReportResponse.RecommendedGym(
                        gym.getGymId(),
                        gym.getName(),
                        parseTechniqueList(gym.getMatchedTechniques()),
                        gym.getMatchingVideoCount(),
                        "최근 공개 영상에서 부족했던 기술이 자주 나온 암장이에요."
                ))
                .toList();
        MonthlyReportResponse.Narrative responseNarrative = new MonthlyReportResponse.Narrative(
                narrative.getHeadline(),
                narrative.getSummary(),
                narrative.getHighlights()
        );

        return new MonthlyReportResponse(
                month.toString(),
                MonthlyReportStatus.READY.value(),
                totals.getSource().value(),
                now(),
                metrics,
                grade,
                tip,
                goal,
                recommended,
                responseNarrative,
                null
        );
    }

    private Double ratio(int numerator, int denominator) {
        if (denominator <= 0) {
            return null;
        }
        return BigDecimal.valueOf(numerator)
                .divide(BigDecimal.valueOf(denominator), 2, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private List<String> parseTechniqueList(String json) {
        try {
            List<String> techniques = objectMapper.readValue(
                    json == null || json.isBlank() ? "[]" : json,
                    STRING_LIST_TYPE
            );
            Set<String> normalized = techniques.stream()
                    .map(AnalysisTechniqueCatalog::normalizeTechnique)
                    .filter(technique -> technique != null && CANONICAL_TECHNIQUE_SET.contains(technique))
                    .collect(java.util.stream.Collectors.toSet());
            return AnalysisTechniqueCatalog.CANONICAL_TECHNIQUES.stream()
                    .filter(normalized::contains)
                    .toList();
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.MONTHLY_REPORT_GENERATION_FAILED);
        }
    }

    private MonthlyReportResponse toStoredResponse(MonthlyReport report) {
        try {
            return new MonthlyReportResponse(
                    report.getPeriod(),
                    report.getStatus(),
                    report.getSource(),
                    report.getGeneratedAt(),
                    objectMapper.readValue(report.getMetrics(), MonthlyReportResponse.Metrics.class),
                    readNullable(report.getGrade(), MonthlyReportResponse.Grade.class),
                    readNullable(report.getTip(), MonthlyReportResponse.Tip.class),
                    readNullable(report.getNextMonthGoal(), MonthlyReportResponse.Goal.class),
                    readList(report.getRecommendedGyms()),
                    readNullable(report.getNarrative(), MonthlyReportResponse.Narrative.class),
                    readNullable(report.getRequirement(), MonthlyReportResponse.Requirement.class)
            );
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.MONTHLY_REPORT_GENERATION_FAILED);
        }
    }

    private <T> T readNullable(String json, Class<T> type) throws Exception {
        if (json == null || json.isBlank()) {
            return null;
        }
        return objectMapper.readValue(json, type);
    }

    private List<MonthlyReportResponse.RecommendedGym> readList(String json) throws Exception {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        return objectMapper.readValue(
                json,
                objectMapper.getTypeFactory()
                        .constructCollectionType(List.class, MonthlyReportResponse.RecommendedGym.class)
        );
    }

    private void saveReport(Long userId, Long gymId, MonthlyReportResponse response) {
        try {
            monthlyReportMapper.upsertReport(MonthlyReport.builder()
                    .userId(userId)
                    .period(response.period())
                    .selectedGymId(gymId)
                    .status(response.status())
                    .source(response.source())
                    .metrics(objectMapper.writeValueAsString(response.metrics()))
                    .grade(writeNullable(response.grade()))
                    .tip(writeNullable(response.tip()))
                    .nextMonthGoal(writeNullable(response.nextMonthGoal()))
                    .recommendedGyms(objectMapper.writeValueAsString(response.recommendedGyms()))
                    .narrative(writeNullable(response.narrative()))
                    .requirement(writeNullable(response.requirement()))
                    .model(modelMode())
                    .promptVersion(properties.promptVersion())
                    .generatedAt(response.generatedAt())
                    .build());
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.MONTHLY_REPORT_GENERATION_FAILED);
        }
    }

    private String writeNullable(Object value) throws Exception {
        return value == null ? null : objectMapper.writeValueAsString(value);
    }

    private String modelMode() {
        return properties.llm() == null ? "rule" : properties.llm().mode();
    }

    private MonthlyReportResponse generatingResponse(YearMonth month) {
        return new MonthlyReportResponse(
                month.toString(),
                MonthlyReportStatus.GENERATING.value(),
                MonthlyReportSource.NONE.value(),
                null,
                null,
                null,
                null,
                null,
                List.of(),
                null,
                null
        );
    }

    private OffsetDateTime now() {
        return OffsetDateTime.now(KST);
    }
}
