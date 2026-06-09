package com.holaclimbing.server.infrastructure.mail;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.mail")
public record MailProperties(
        String mode,
        String from
) {

    public String normalizedMode() {
        if (mode == null || mode.isBlank()) {
            return "";
        }
        return mode.trim().toLowerCase();
    }
}
