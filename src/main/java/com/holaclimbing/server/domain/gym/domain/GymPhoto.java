package com.holaclimbing.server.domain.gym.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 암장 사진 엔티티. gym_photos 테이블 매핑.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GymPhoto {

    private Long id;
    private Long gymId;
    private Long uploadedBy;
    private String gcsPath;
    private String caption;
    private int displayOrder;
    private LocalDateTime createdAt;
}
