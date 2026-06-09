package com.holaclimbing.server.domain.admin.bootstrap;

import com.holaclimbing.server.domain.user.domain.User;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrapRunner implements ApplicationRunner {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String STATUS_ACTIVE = "ACTIVE";

    private final AdminBootstrapProperties properties;
    private final UserMapper userMapper;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (!properties.enabled()) {
            return;
        }
        if (!StringUtils.hasText(properties.email())) {
            log.warn("[AdminBootstrap] enabled=true but email is blank");
            return;
        }

        String email = properties.email().trim();
        User user = userMapper.findByEmail(email);
        if (user == null) {
            log.warn("[AdminBootstrap] user not found: email={}", maskEmail(email));
            return;
        }
        if (!user.isEmailVerified()) {
            log.warn("[AdminBootstrap] user is not email-verified: userId={}, email={}",
                    user.getId(), maskEmail(email));
            return;
        }
        if (!STATUS_ACTIVE.equals(user.getStatus())) {
            log.warn("[AdminBootstrap] user is not active: userId={}, status={}",
                    user.getId(), user.getStatus());
            return;
        }
        if (ROLE_ADMIN.equals(user.getRole())) {
            log.info("[AdminBootstrap] user is already admin: userId={}, email={}",
                    user.getId(), maskEmail(email));
            return;
        }

        userMapper.updateRole(user.getId(), ROLE_ADMIN);
        log.info("[AdminBootstrap] promoted bootstrap admin: userId={}, email={}",
                user.getId(), maskEmail(email));
    }

    private String maskEmail(String email) {
        int atIndex = email.indexOf('@');
        if (atIndex <= 0) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(atIndex);
    }
}
