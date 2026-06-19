package com.holaclimbing.server.domain.report.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

/**
 * 신고 엔티티. reports 테이블 매핑.
 * target_type/target_id는 FK 없는 다형 참조 — 영상·댓글·회원·암장·채팅메시지를 가리킨다.
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Report {

    private Long id;
    private Long reporterId;
    private String targetType;
    private Long targetId;
    private String category;
    private String reason;
    private String status;
    private Long reviewedBy;
    private OffsetDateTime reviewedAt;
    private OffsetDateTime createdAt;
}
