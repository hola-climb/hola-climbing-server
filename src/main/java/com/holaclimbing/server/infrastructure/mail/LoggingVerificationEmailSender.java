package com.holaclimbing.server.infrastructure.mail;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class LoggingVerificationEmailSender implements VerificationEmailSender {

    private final String frontendBaseUrl;

    public LoggingVerificationEmailSender(String frontendBaseUrl) {
        this.frontendBaseUrl = trimTrailingSlash(frontendBaseUrl);
    }

    @Override
    public void send(String toEmail, String token) {
        log.info("[이메일 인증] 수신자={} / 개발용 메일 발송 생략", maskEmail(toEmail));
        log.debug("[이메일 인증] token 원문은 로그에 노출하지 않습니다. frontendBaseUrl={}", frontendBaseUrl);
    }

    @Override
    public void sendPasswordReset(String toEmail, String token) {
        log.info("[비밀번호 재설정] 수신자={} / 개발용 메일 발송 생략", maskEmail(toEmail));
        log.debug("[비밀번호 재설정] token 원문은 로그에 노출하지 않습니다. frontendBaseUrl={}", frontendBaseUrl);
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String maskEmail(String email) {
        if (email == null || email.isBlank()) {
            return "***";
        }
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
