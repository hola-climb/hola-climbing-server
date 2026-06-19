package com.holaclimbing.server.domain.gym.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * 암장 엔티티. gyms 테이블 매핑.
 * style_embedding(vector) 컬럼은 별도 타입 핸들러가 필요하여 제외.
 * businessHours는 business_hours(jsonb)를 raw JSON 문자열로 읽어 서비스에서 파싱한다.
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
    private String businessHours;
    private String regionCode;
    private BigDecimal ratingAvg;
    private int ratingCount;
    private String status;
    private Long createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
}
