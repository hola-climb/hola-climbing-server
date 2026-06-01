package com.holaclimbing.server.domain.user;

import com.holaclimbing.server.common.response.ApiResponse;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.user.dto.request.UpdateProfileRequest;
import com.holaclimbing.server.domain.user.dto.request.WithdrawRequest;
import com.holaclimbing.server.domain.user.dto.response.MyProfileResponse;
import com.holaclimbing.server.domain.user.dto.response.UserProfileResponse;
import com.holaclimbing.server.domain.user.dto.response.UserSummaryResponse;
import com.holaclimbing.server.domain.user.service.UserProfileService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 회원 프로필 / 팔로우 / 차단 API.
 * /api/users/me 와 follow·block 변경 API는 SecurityConfig에서 인증을 요구한다.
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
@Validated
public class UserProfileController {

    private final UserProfileService userProfileService;

    @GetMapping("/me")
    public ApiResponse<MyProfileResponse> getMyProfile(@AuthenticationPrincipal Long userId) {
        return ApiResponse.success(userProfileService.getMyProfile(userId));
    }

    @PatchMapping("/me")
    public ApiResponse<MyProfileResponse> updateMyProfile(@AuthenticationPrincipal Long userId,
                                                          @Valid @RequestBody UpdateProfileRequest request) {
        return ApiResponse.success(userProfileService.updateMyProfile(userId, request));
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> withdraw(@AuthenticationPrincipal Long userId,
                                      @Valid @RequestBody WithdrawRequest request) {
        userProfileService.withdraw(userId, request.password());
        return ApiResponse.success();
    }

    @GetMapping("/me/blocks")
    public ApiResponse<PageResponse<UserSummaryResponse>> getBlockedUsers(
            @AuthenticationPrincipal Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
        return ApiResponse.success(userProfileService.getBlockedUsers(userId, page, size));
    }

    @GetMapping("/{userId}")
    public ApiResponse<UserProfileResponse> getUserProfile(@PathVariable Long userId,
                                                           @AuthenticationPrincipal Long viewerId) {
        return ApiResponse.success(userProfileService.getUserProfile(userId, viewerId));
    }

    @GetMapping("/{userId}/followers")
    public ApiResponse<PageResponse<UserSummaryResponse>> getFollowers(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
        return ApiResponse.success(userProfileService.getFollowers(userId, page, size));
    }

    @GetMapping("/{userId}/following")
    public ApiResponse<PageResponse<UserSummaryResponse>> getFollowing(
            @PathVariable Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
        return ApiResponse.success(userProfileService.getFollowing(userId, page, size));
    }

    @PostMapping("/{userId}/follow")
    public ApiResponse<Void> follow(@AuthenticationPrincipal Long followerId,
                                    @PathVariable Long userId) {
        userProfileService.follow(followerId, userId);
        return ApiResponse.success();
    }

    @DeleteMapping("/{userId}/follow")
    public ApiResponse<Void> unfollow(@AuthenticationPrincipal Long followerId,
                                      @PathVariable Long userId) {
        userProfileService.unfollow(followerId, userId);
        return ApiResponse.success();
    }

    @PostMapping("/{userId}/block")
    public ApiResponse<Void> block(@AuthenticationPrincipal Long blockerId,
                                   @PathVariable Long userId) {
        userProfileService.block(blockerId, userId);
        return ApiResponse.success();
    }

    @DeleteMapping("/{userId}/block")
    public ApiResponse<Void> unblock(@AuthenticationPrincipal Long blockerId,
                                     @PathVariable Long userId) {
        userProfileService.unblock(blockerId, userId);
        return ApiResponse.success();
    }
}
