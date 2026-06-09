package com.holaclimbing.server.infrastructure.mail;

public interface VerificationEmailSender {

    void send(String toEmail, String token);

    void sendPasswordReset(String toEmail, String token);
}
