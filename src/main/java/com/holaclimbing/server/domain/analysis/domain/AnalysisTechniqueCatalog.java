package com.holaclimbing.server.domain.analysis.domain;

import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class AnalysisTechniqueCatalog {

    public static final List<String> CANONICAL_TECHNIQUES = List.of(
            "high_step",
            "flagging",
            "toe_hook",
            "heel_hook",
            "lock_off",
            "dyno",
            "coordination"
    );

    private AnalysisTechniqueCatalog() {
    }

    public static String normalizeTechnique(String technique) {
        if (technique == null) {
            return null;
        }
        String normalized = technique.trim().toLowerCase().replace('-', '_').replace(' ', '_');
        if (normalized.isBlank()) {
            return null;
        }
        if ("highstep".equals(normalized)) {
            return "high_step";
        }
        return normalized;
    }

    public static List<String> normalizeTechniques(List<String> techniques) {
        if (techniques == null || techniques.isEmpty()) {
            return List.of();
        }
        Set<String> distinct = new LinkedHashSet<>();
        for (String technique : techniques) {
            String normalized = normalizeTechnique(technique);
            if (normalized != null) {
                distinct.add(normalized);
            }
        }
        return distinct.stream()
                .sorted(Comparator
                        .comparingInt(AnalysisTechniqueCatalog::canonicalOrder)
                        .thenComparing(Comparator.naturalOrder()))
                .toList();
    }

    private static int canonicalOrder(String technique) {
        int index = CANONICAL_TECHNIQUES.indexOf(technique);
        return index >= 0 ? index : CANONICAL_TECHNIQUES.size();
    }
}
