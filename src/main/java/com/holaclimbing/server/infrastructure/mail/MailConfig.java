package com.holaclimbing.server.infrastructure.mail;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;

@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {

    @Bean
    MailModeGuard mailModeGuard(Environment environment, MailProperties properties) {
        return new MailModeGuard(environment, properties);
    }

    @Bean
    @ConditionalOnProperty(name = "app.mail.mode", havingValue = "log")
    VerificationEmailSender loggingVerificationEmailSender(
            @Value("${app.frontend-base-url:http://localhost:5173}") String frontendBaseUrl) {
        return new LoggingVerificationEmailSender(frontendBaseUrl);
    }

    @Bean
    @ConditionalOnProperty(name = "app.mail.mode", havingValue = "smtp")
    VerificationEmailSender smtpVerificationEmailSender(JavaMailSender mailSender,
                                                        MailProperties properties,
                                                        @Value("${app.frontend-base-url:http://localhost:5173}")
                                                        String frontendBaseUrl) {
        return new SmtpVerificationEmailSender(mailSender, properties.from(), frontendBaseUrl);
    }

    static class MailModeGuard {

        private final Environment environment;
        private final MailProperties properties;

        MailModeGuard(Environment environment, MailProperties properties) {
            this.environment = environment;
            this.properties = properties;
        }

        @PostConstruct
        void validate() {
            if (environment.matchesProfiles("prod") && !"smtp".equals(properties.normalizedMode())) {
                throw new IllegalStateException("APP_MAIL_MODE=smtp must be configured in prod profile.");
            }
        }
    }
}
