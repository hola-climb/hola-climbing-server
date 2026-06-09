package com.holaclimbing.server.infrastructure.mail;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;

@ExtendWith(OutputCaptureExtension.class)
class LoggingVerificationEmailSenderTest {

    @Test
    @DisplayName("로그 메일 발송기는 이메일 인증 토큰 원문을 로그에 남기지 않는다")
    void send_doesNotLogVerificationToken(CapturedOutput output) {
        var sender = new LoggingVerificationEmailSender("http://localhost:5173");

        sender.send("climber@hola.com", "email-secret-token");

        assertThat(output).doesNotContain("email-secret-token");
        assertThat(output).doesNotContain("token=");
        assertThat(output).contains("c***@hola.com");
    }

    @Test
    @DisplayName("로그 메일 발송기는 비밀번호 재설정 토큰 원문을 로그에 남기지 않는다")
    void sendPasswordReset_doesNotLogResetToken(CapturedOutput output) {
        var sender = new LoggingVerificationEmailSender("http://localhost:5173");

        sender.sendPasswordReset("climber@hola.com", "reset-secret-token");

        assertThat(output).doesNotContain("reset-secret-token");
        assertThat(output).doesNotContain("token=");
        assertThat(output).contains("c***@hola.com");
    }
}
