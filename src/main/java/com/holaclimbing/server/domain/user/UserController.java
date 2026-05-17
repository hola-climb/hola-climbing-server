package com.holaclimbing.server.domain.user;

import com.holaclimbing.server.common.response.ApiResponse;
import com.holaclimbing.server.domain.user.dto.request.LoginRequest;
import com.holaclimbing.server.domain.user.dto.request.RefreshRequest;
import com.holaclimbing.server.domain.user.dto.request.ResendVerificationRequest;
import com.holaclimbing.server.domain.user.dto.request.SignupRequest;
import com.holaclimbing.server.domain.user.dto.response.AvailabilityResponse;
import com.holaclimbing.server.domain.user.dto.response.SignupResponse;
import com.holaclimbing.server.domain.user.dto.response.TokenResponse;
import com.holaclimbing.server.domain.user.service.UserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
public class UserController {

    private final UserService userService;

    @PostMapping("/signup")
    public ResponseEntity<ApiResponse<SignupResponse>> signup(@Valid @RequestBody SignupRequest request) {
        SignupResponse response = userService.signup(request);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(response, "인증 메일을 발송했습니다. 메일 확인 후 로그인해 주세요."));
    }

    @PostMapping("/login")
    public ApiResponse<TokenResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.success(userService.login(request));
    }

    @PostMapping("/refresh")
    public ApiResponse<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiResponse.success(userService.refresh(request.refreshToken()));
    }

    @GetMapping("/verify-email")
    public ApiResponse<Void> verifyEmail(@RequestParam @NotBlank String token) {
        userService.verifyEmail(token);
        return ApiResponse.success();
    }

    @PostMapping("/resend-verification")
    public ApiResponse<Void> resendVerification(@Valid @RequestBody ResendVerificationRequest request) {
        userService.resendVerification(request.email());
        return ApiResponse.success();
    }

    @GetMapping("/email-check")
    public ApiResponse<AvailabilityResponse> checkEmail(@RequestParam @NotBlank @Email String email) {
        return ApiResponse.success(new AvailabilityResponse(userService.isEmailAvailable(email)));
    }

    @GetMapping("/nickname-check")
    public ApiResponse<AvailabilityResponse> checkNickname(@RequestParam @NotBlank String nickname) {
        return ApiResponse.success(new AvailabilityResponse(userService.isNicknameAvailable(nickname)));
    }
}
