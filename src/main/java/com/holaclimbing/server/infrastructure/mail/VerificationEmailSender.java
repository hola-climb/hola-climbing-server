package com.holaclimbing.server.infrastructure.mail;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * 이메일 인증 메일 발송.
 *
 * 현재는 실제 SMTP 발송 대신 인증 링크를 로그로 출력하는 개발용 구현이다.
 * 운영 전환 시 spring-boot-starter-mail 추가 후 JavaMailSender 호출로 교체할 것.
 */
@Slf4j
@Component
public class VerificationEmailSender {

    private final String baseUrl;

    public VerificationEmailSender(@Value("${app.base-url:http://localhost:8080}") String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public void send(String toEmail, String token) {
        String verifyLink = baseUrl + "/api/users/verify-email?token=" + token;
        log.info("[이메일 인증] 수신자={} / 인증 링크={}", toEmail, verifyLink);
    }
}
