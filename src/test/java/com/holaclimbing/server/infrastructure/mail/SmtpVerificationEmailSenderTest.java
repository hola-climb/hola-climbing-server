package com.holaclimbing.server.infrastructure.mail;

import jakarta.mail.BodyPart;
import jakarta.mail.Multipart;
import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSender;

import java.util.Properties;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SmtpVerificationEmailSenderTest {

    @Test
    @DisplayName("SMTP 이메일 인증 메일은 브랜드 HTML 본문과 plain fallback을 함께 발송한다")
    void send_sendsBrandedHtmlVerificationMail() throws Exception {
        JavaMailSender mailSender = mock(JavaMailSender.class);
        MimeMessage message = new MimeMessage(Session.getInstance(new Properties()));
        when(mailSender.createMimeMessage()).thenReturn(message);
        var sender = new SmtpVerificationEmailSender(mailSender, "hola@example.com", "http://localhost:5173/");

        sender.send("climber@hola.com", "email-secret-token");

        verify(mailSender).send(message);
        message.saveChanges();
        assertThat(message.getSubject()).isEqualTo("Hola 이메일 인증");
        assertThat(message.getFrom()[0].toString()).isEqualTo("hola@example.com");
        assertThat(message.getAllRecipients()[0].toString()).isEqualTo("climber@hola.com");

        String plain = findContent(message.getContent(), "text/plain");
        String html = findContent(message.getContent(), "text/html");
        assertThat(plain).contains("http://localhost:5173/verify-email?token=email-secret-token");
        assertThat(html)
                .contains("HOLA")
                .contains("이메일 인증 완료하기")
                .contains("http://localhost:5173/verify-email?token=email-secret-token")
                .contains("24시간");
    }

    private String findContent(Object content, String mimeType) throws Exception {
        if (content instanceof Multipart multipart) {
            for (int i = 0; i < multipart.getCount(); i++) {
                BodyPart part = multipart.getBodyPart(i);
                Object partContent = part.getContent();
                if (part.isMimeType(mimeType) && partContent instanceof String text) {
                    return text;
                }
                String nested = findContent(partContent, mimeType);
                if (nested != null) {
                    return nested;
                }
            }
            return null;
        }
        return null;
    }
}
