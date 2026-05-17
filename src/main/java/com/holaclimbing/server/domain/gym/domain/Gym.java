package com.holaclimbing.server.domain.gym.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 암장 엔티티. gyms 테이블 매핑.
 * style_embedding(vector), business_hours(jsonb) 컬럼은 별도 타입 핸들러가 필요하여 제외.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Gym {

    private Long id;
    private String name;
    private String address;
    private Double lat;
    private Double lng;
    private String description;
    private String phone;
    private String website;
    private String thumbnailUrl;
    private String regionCode;
    private BigDecimal ratingAvg;
    private int ratingCount;
    private String status;
    private Long createdBy;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private LocalDateTime deletedAt;
}
