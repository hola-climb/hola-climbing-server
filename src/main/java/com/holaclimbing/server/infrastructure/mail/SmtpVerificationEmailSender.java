package com.holaclimbing.server.infrastructure.mail;

import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

public class SmtpVerificationEmailSender implements VerificationEmailSender {

    private final JavaMailSender mailSender;
    private final String from;
    private final String frontendBaseUrl;

    public SmtpVerificationEmailSender(JavaMailSender mailSender, String from, String frontendBaseUrl) {
        this.mailSender = mailSender;
        this.from = from;
        this.frontendBaseUrl = trimTrailingSlash(frontendBaseUrl);
    }

    @Override
    public void send(String toEmail, String token) {
        sendMail(toEmail, "Hola 이메일 인증", "아래 링크를 열어 이메일 인증을 완료해 주세요.\n\n"
                + frontendBaseUrl + "/verify-email?token=" + token);
    }

    @Override
    public void sendPasswordReset(String toEmail, String token) {
        sendMail(toEmail, "Hola 비밀번호 재설정", "아래 링크를 열어 비밀번호를 재설정해 주세요.\n\n"
                + frontendBaseUrl + "/reset-password?token=" + token);
    }

    private void sendMail(String toEmail, String subject, String text) {
        SimpleMailMessage message = new SimpleMailMessage();
        if (from != null && !from.isBlank()) {
            message.setFrom(from);
        }
        message.setTo(toEmail);
        message.setSubject(subject);
        message.setText(text);
        mailSender.send(message);
    }

    private String trimTrailingSlash(String value) {
        if (value == null || value.isBlank()) {
            return "";
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
