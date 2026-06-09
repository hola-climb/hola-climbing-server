package com.holaclimbing.server.domain.admin.bootstrap;

import com.holaclimbing.server.domain.user.domain.User;
import com.holaclimbing.server.domain.user.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AdminBootstrapRunnerTest {

    private final UserMapper userMapper = mock(UserMapper.class);

    @Test
    @DisplayName("관리자 부트스트랩 - 비활성화되어 있으면 아무 작업도 하지 않는다")
    void run_disabled_doesNothing() throws Exception {
        var runner = new AdminBootstrapRunner(new AdminBootstrapProperties(false, "admin@hola.com"), userMapper);

        runner.run(null);

        verifyNoInteractions(userMapper);
    }

    @Test
    @DisplayName("관리자 부트스트랩 - 활성 인증 회원을 관리자로 승격한다")
    void run_enabledPromotesVerifiedActiveUser() throws Exception {
        var user = User.builder()
                .id(1L)
                .email("admin@hola.com")
                .emailVerified(true)
                .role("USER")
                .status("ACTIVE")
                .build();
        when(userMapper.findByEmail("admin@hola.com")).thenReturn(user);
        var runner = new AdminBootstrapRunner(new AdminBootstrapProperties(true, "admin@hola.com"), userMapper);

        runner.run(null);

        verify(userMapper).updateRole(1L, "ADMIN");
    }

    @Test
    @DisplayName("관리자 부트스트랩 - 이메일 미인증 회원은 승격하지 않는다")
    void run_unverifiedUser_doesNotPromote() throws Exception {
        var user = User.builder()
                .id(1L)
                .email("admin@hola.com")
                .emailVerified(false)
                .role("USER")
                .status("ACTIVE")
                .build();
        when(userMapper.findByEmail("admin@hola.com")).thenReturn(user);
        var runner = new AdminBootstrapRunner(new AdminBootstrapProperties(true, "admin@hola.com"), userMapper);

        runner.run(null);

        verify(userMapper, never()).updateRole(1L, "ADMIN");
    }

    @Test
    @DisplayName("관리자 부트스트랩 - 비활성 회원은 승격하지 않는다")
    void run_inactiveUser_doesNotPromote() throws Exception {
        var user = User.builder()
                .id(1L)
                .email("admin@hola.com")
                .emailVerified(true)
                .role("USER")
                .status("SUSPENDED")
                .build();
        when(userMapper.findByEmail("admin@hola.com")).thenReturn(user);
        var runner = new AdminBootstrapRunner(new AdminBootstrapProperties(true, "admin@hola.com"), userMapper);

        runner.run(null);

        verify(userMapper, never()).updateRole(1L, "ADMIN");
    }

    @Test
    @DisplayName("관리자 부트스트랩 - 이미 관리자면 중복 업데이트하지 않는다")
    void run_alreadyAdmin_doesNotUpdateAgain() throws Exception {
        var user = User.builder()
                .id(1L)
                .email("admin@hola.com")
                .emailVerified(true)
                .role("ADMIN")
                .status("ACTIVE")
                .build();
        when(userMapper.findByEmail("admin@hola.com")).thenReturn(user);
        var runner = new AdminBootstrapRunner(new AdminBootstrapProperties(true, "admin@hola.com"), userMapper);

        runner.run(null);

        verify(userMapper, never()).updateRole(1L, "ADMIN");
    }
}
