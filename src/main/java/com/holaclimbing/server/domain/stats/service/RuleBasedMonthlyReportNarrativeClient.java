package com.holaclimbing.server.domain.stats.service;

import com.holaclimbing.server.domain.stats.domain.MonthlyReportAggregate;
import com.holaclimbing.server.domain.stats.domain.MonthlyReportNarrative;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
@ConditionalOnMissingBean(value = MonthlyReportNarrativeClient.class, ignored = RuleBasedMonthlyReportNarrativeClient.class)
public class RuleBasedMonthlyReportNarrativeClient implements MonthlyReportNarrativeClient {

    @Override
    public MonthlyReportNarrative generate(MonthlyReportAggregate aggregate,
                                           Map<String, Integer> techniqueCounts,
                                           List<String> underusedTechniques) {
        String headline = headline(aggregate);
        String summary = summary(aggregate, techniqueCounts, underusedTechniques);

        List<String> highlights = new ArrayList<>();
        highlights.add("기록한 세션과 완등 수를 기준으로 월간 흐름을 정리했어요.");
        if (!techniqueCounts.isEmpty()) {
            highlights.add("영상 분석으로 자주 나온 기술과 덜 나온 기술을 함께 살펴봤어요.");
        }
        if (!underusedTechniques.isEmpty()) {
            highlights.add(labels(underusedTechniques) + "처럼 덜 나온 기술을 다음 달 문제 선택 기준으로 삼을 수 있어요.");
        }

        return new MonthlyReportNarrative(headline, summary, highlights);
    }

    private String headline(MonthlyReportAggregate aggregate) {
        if (aggregate.getSessions() >= 4) {
            return "꾸준히 등반 리듬을 쌓은 달";
        }
        if (aggregate.getProblemsSolved() >= 10) {
            return "등반 볼륨이 또렷하게 남은 달";
        }
        return "기록 습관을 이어간 달";
    }

    private String summary(MonthlyReportAggregate aggregate,
                           Map<String, Integer> techniqueCounts,
                           List<String> underusedTechniques) {
        if (techniqueCounts.isEmpty()) {
            return "이번 달 기록을 기준으로 등반 볼륨을 정리했어요. 다음 달에도 세션과 완등 기록을 꾸준히 남겨보세요.";
        }
        if (underusedTechniques.isEmpty()) {
            return "기록과 영상 분석이 함께 쌓여 등반 스타일을 더 안정적으로 돌아볼 수 있었어요.";
        }
        return labels(underusedTechniques) + " 같은 덜 나온 기술을 함께 보면 다음 달 기술 다양성을 넓히기 좋아요.";
    }

    private String labels(List<String> techniques) {
        return techniques.stream()
                .map(this::label)
                .reduce((left, right) -> left + ", " + right)
                .orElse("기술");
    }

    private String label(String technique) {
        return switch (technique) {
            case "high_step" -> "하이스텝";
            case "flagging" -> "플래깅";
            case "toe_hook" -> "토훅";
            case "heel_hook" -> "힐훅";
            case "lock_off" -> "락오프";
            case "dyno" -> "다이노";
            case "coordination" -> "코디네이션";
            default -> technique;
        };
    }
}
