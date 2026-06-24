package com.holaclimbing.server.domain.stats.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.holaclimbing.server.domain.stats.MonthlyReportProperties;
import com.holaclimbing.server.domain.stats.domain.MonthlyReportAggregate;
import com.holaclimbing.server.domain.stats.domain.MonthlyReportNarrative;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.monthly-report.llm", name = "mode", havingValue = "openai")
public class OpenAiMonthlyReportNarrativeClient implements MonthlyReportNarrativeClient {

    private final RestClient.Builder restClientBuilder;
    private final MonthlyReportProperties properties;
    private final ObjectMapper objectMapper;

    @Override
    public MonthlyReportNarrative generate(MonthlyReportAggregate aggregate,
                                           Map<String, Integer> techniqueCounts,
                                           List<String> underusedTechniques) {
        try {
            RestClient client = openAiRestClient();
            String body = client.post()
                    .uri("/chat/completions")
                    .headers(headers -> headers.setBearerAuth(properties.llm().apiKey()))
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody(aggregate, techniqueCounts, underusedTechniques))
                    .retrieve()
                    .body(String.class);

            JsonNode response = objectMapper.readTree(body);
            JsonNode content = response.at("/choices/0/message/content");
            if (!content.isTextual() || content.asText().isBlank()) {
                throw new IllegalStateException("monthly report LLM response missing message content");
            }

            JsonNode narrative = objectMapper.readTree(content.asText());
            return new MonthlyReportNarrative(
                    requiredText(narrative, "headline"),
                    requiredText(narrative, "summary"),
                    parseHighlights(narrative.path("highlights"), aggregate, techniqueCounts, underusedTechniques)
            );
        } catch (Exception e) {
            log.warn("monthly report LLM narrative generation failed: {}", e.toString());
            return fallback(aggregate, techniqueCounts, underusedTechniques);
        }
    }

    private RestClient openAiRestClient() {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = timeoutMillis();
        requestFactory.setConnectTimeout(timeoutMillis);
        requestFactory.setReadTimeout(timeoutMillis);
        return restClientBuilder.clone()
                .baseUrl(properties.llm().baseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    private int timeoutMillis() {
        int seconds = properties.llm().timeoutSeconds();
        long millis = Math.max(1L, seconds) * 1000L;
        return (int) Math.min(millis, Integer.MAX_VALUE);
    }

    private Map<String, Object> requestBody(MonthlyReportAggregate aggregate,
                                            Map<String, Integer> techniqueCounts,
                                            List<String> underusedTechniques) throws Exception {
        return Map.of(
                "model", properties.llm().model(),
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt()),
                        Map.of("role", "user", "content", objectMapper.writeValueAsString(Map.of(
                                "metrics", aggregate.toPromptMap(),
                                "techniqueCounts", techniqueCounts,
                                "underusedTechniques", underusedTechniques
                        )))
                ),
                "response_format", Map.of("type", "json_object")
        );
    }

    private String requiredText(JsonNode parent, String fieldName) {
        JsonNode value = parent.path(fieldName);
        if (!value.isTextual() || value.asText().isBlank()) {
            throw new IllegalStateException("monthly report LLM response missing " + fieldName);
        }
        return value.asText();
    }

    private List<String> parseHighlights(JsonNode highlights,
                                         MonthlyReportAggregate aggregate,
                                         Map<String, Integer> techniqueCounts,
                                         List<String> underusedTechniques) {
        if (!highlights.isArray()) {
            return fallback(aggregate, techniqueCounts, underusedTechniques).getHighlights();
        }

        List<String> parsed = new ArrayList<>();
        for (JsonNode highlight : highlights) {
            String text = highlight.asText("");
            if (!text.isBlank()) {
                parsed.add(text);
            }
        }
        if (parsed.isEmpty()) {
            return fallback(aggregate, techniqueCounts, underusedTechniques).getHighlights();
        }
        return parsed;
    }

    private MonthlyReportNarrative fallback(MonthlyReportAggregate aggregate,
                                            Map<String, Integer> techniqueCounts,
                                            List<String> underusedTechniques) {
        return new RuleBasedMonthlyReportNarrativeClient()
                .generate(aggregate, techniqueCounts, underusedTechniques);
    }

    private String systemPrompt() {
        return """
                너는 클라이밍 앱의 월간 리포트 문장을 작성하는 한국어 리포트 writer다.
                서버가 제공한 JSON만 근거로 사용하고, JSON에 없는 사실은 만들지 않는다.
                숫자, 횟수, 퍼센트, 난이도 값은 새로 쓰거나 추정하지 않는다.
                부상 위험이 있는 공격적인 무브 처방을 하지 않는다.
                의학적 조언이나 사용자의 신체 상태에 대한 판단을 하지 않는다.
                기술 다양성, 등반 볼륨, 기록 습관에 관한 문장만 작성한다.
                출력은 headline, summary, highlights 키만 가진 JSON 객체여야 한다.
                highlights는 한국어 문자열 배열이어야 한다.
                """;
    }
}
