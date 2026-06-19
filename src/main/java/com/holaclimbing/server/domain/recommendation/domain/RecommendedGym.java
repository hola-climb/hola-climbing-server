package com.holaclimbing.server.domain.recommendation.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RecommendedGym {

    private Long id;
    private String name;
    private String address;
    private String thumbnailUrl;
    private String businessHours;
    private String regionCode;
    private BigDecimal ratingAvg;
    private int ratingCount;
    private Boolean favorite;
    private Double distanceKm;
    private Double rankingDistance;
}
