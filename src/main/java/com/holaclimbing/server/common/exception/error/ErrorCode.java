package com.holaclimbing.server.common.exception.error;

import lombok.Getter;
import org.springframework.http.HttpStatus;

@Getter
public enum ErrorCode {
    // ===== 공통 (C) =====
    INVALID_INPUT(HttpStatus.BAD_REQUEST, "C001", "입력값이 올바르지 않습니다."),
    UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "C002", "인증이 필요합니다."),
    FORBIDDEN(HttpStatus.FORBIDDEN, "C003", "접근 권한이 없습니다."),
    NOT_FOUND(HttpStatus.NOT_FOUND, "C004", "리소스를 찾을 수 없습니다."),
    METHOD_NOT_ALLOWED(HttpStatus.METHOD_NOT_ALLOWED, "C005", "허용되지 않은 메서드입니다."),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "C999", "서버 오류가 발생했습니다."),

    // ===== 회원 F-01 (U) =====
    USER_NOT_FOUND(HttpStatus.NOT_FOUND, "U001", "사용자를 찾을 수 없습니다."),
    EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "U002", "이미 사용 중인 이메일입니다."),
    PASSWORD_MISMATCH(HttpStatus.UNAUTHORIZED, "U003", "비밀번호가 일치하지 않습니다."),
    EMAIL_NOT_VERIFIED(HttpStatus.FORBIDDEN, "U004", "이메일 인증이 필요합니다."),
    INVALID_TOKEN(HttpStatus.UNAUTHORIZED, "U005", "유효하지 않은 토큰입니다."),
    EXPIRED_TOKEN(HttpStatus.UNAUTHORIZED, "U006", "만료된 토큰입니다."),
    USER_BLOCKED(HttpStatus.FORBIDDEN, "U007", "차단된 사용자입니다."),
    NICKNAME_ALREADY_EXISTS(HttpStatus.CONFLICT, "U008", "이미 사용 중인 닉네임입니다."),
    REQUIRED_TERMS_NOT_AGREED(HttpStatus.BAD_REQUEST, "U010", "필수 약관에 모두 동의해야 합니다."),
    INVALID_RESET_TOKEN(HttpStatus.BAD_REQUEST, "U011", "유효하지 않거나 만료된 토큰입니다."),
    USER_SUSPENDED(HttpStatus.FORBIDDEN, "U012", "정지된 계정입니다."),
    TERMS_NOT_CONFIGURED(HttpStatus.SERVICE_UNAVAILABLE, "U013", "활성 약관 정보가 없습니다. 관리자에게 문의해 주세요."),
    UNSUPPORTED_OAUTH_PROVIDER(HttpStatus.BAD_REQUEST, "U014", "지원하지 않는 소셜 로그인 제공자입니다."),
    OAUTH_AUTHORIZATION_FAILED(HttpStatus.UNAUTHORIZED, "U015", "소셜 로그인 인증에 실패했습니다."),
    INVALID_OAUTH_SIGNUP_TOKEN(HttpStatus.BAD_REQUEST, "U016", "소셜 회원가입 토큰이 유효하지 않거나 만료되었습니다."),
    OAUTH_EMAIL_ALREADY_EXISTS(HttpStatus.CONFLICT, "U017", "해당 이메일로 가입된 계정이 이미 있습니다."),
    INVALID_OAUTH_STATE(HttpStatus.UNAUTHORIZED, "U018", "소셜 로그인 요청 상태가 유효하지 않거나 만료되었습니다."),
    INVALID_OAUTH_RESULT_CODE(HttpStatus.BAD_REQUEST, "U019", "소셜 로그인 결과 코드가 유효하지 않거나 만료되었습니다."),

    // ===== 비디오 F-02 (V) =====
    VIDEO_NOT_FOUND(HttpStatus.NOT_FOUND, "V001", "영상을 찾을 수 없습니다."),
    VIDEO_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "V002", "영상 용량이 너무 큽니다. (최대 200MB)"),
    VIDEO_TOO_LONG(HttpStatus.BAD_REQUEST, "V003", "영상 길이가 너무 깁니다. (최대 60초)"),
    UNSUPPORTED_VIDEO_FORMAT(HttpStatus.BAD_REQUEST, "V004", "지원하지 않는 영상 포맷입니다."),
    ANALYSIS_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "V005", "영상 분석에 실패했습니다."),
    VIDEO_NOT_ACCESSIBLE(HttpStatus.FORBIDDEN, "V006", "비공개 영상에 접근할 수 없습니다."),

    // ===== 암장 F-04 (G) =====
    GYM_NOT_FOUND(HttpStatus.NOT_FOUND, "G001", "암장을 찾을 수 없습니다."),
    GYM_OUT_OF_RANGE(HttpStatus.FORBIDDEN, "G002", "암장 반경 내에 있지 않습니다."),
    ALREADY_REVIEWED(HttpStatus.CONFLICT, "G003", "이미 리뷰를 작성한 암장입니다."),
    REVIEW_NOT_FOUND(HttpStatus.NOT_FOUND, "G004", "리뷰를 찾을 수 없습니다."),
    INVALID_GYM_GRADE(HttpStatus.BAD_REQUEST, "G005", "암장 난이도가 올바르지 않습니다."),

    // ===== 알림 F-08 (N) =====
    NOTIFICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "N001", "알림을 찾을 수 없습니다."),

    // ===== 신고 F-09 (R) =====
    SELF_REPORT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "R001", "자기 자신의 콘텐츠는 신고할 수 없습니다."),
    ALREADY_REPORTED(HttpStatus.CONFLICT, "R002", "이미 신고한 대상입니다."),

    // ===== 통계 / 클라이밍 기록 F-03 (T) =====
    CLIMBING_LOG_NOT_FOUND(HttpStatus.NOT_FOUND, "T001", "클라이밍 기록을 찾을 수 없습니다."),
    MONTHLY_REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "T002", "월간 리포트를 찾을 수 없습니다."),
    INVALID_MONTH(HttpStatus.BAD_REQUEST, "T003", "월 형식이 올바르지 않습니다."),
    MONTHLY_REPORT_GENERATION_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "T004", "월간 리포트 생성에 실패했습니다."),

    // ===== 인프라 / 외부 시스템 (S) =====
    GCS_UPLOAD_FAILED(HttpStatus.INTERNAL_SERVER_ERROR, "S001", "영상 업로드에 실패했습니다."),
    AI_SERVER_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE, "S002", "AI 분석 서버에 연결할 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String defaultMessage;

    ErrorCode(HttpStatus status, String code, String defaultMessage) {
        this.status = status;
        this.code = code;
        this.defaultMessage = defaultMessage;
    }

}
