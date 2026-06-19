package com.holaclimbing.server.domain.gym.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 암장별 난이도 마스터. gym_grades 테이블 매핑.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GymGrade {

    private Long id;
    private Long gymId;
    private String label;
    private int difficultyOrder;
    private boolean active;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
