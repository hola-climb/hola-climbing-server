package com.holaclimbing.server.domain.video;

import com.holaclimbing.server.common.response.ApiResponse;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.video.dto.request.CreateCommentRequest;
import com.holaclimbing.server.domain.video.dto.request.CreateVideoRequest;
import com.holaclimbing.server.domain.video.dto.request.UpdateVideoRequest;
import com.holaclimbing.server.domain.video.dto.request.UploadUrlRequest;
import com.holaclimbing.server.domain.video.dto.response.CommentResponse;
import com.holaclimbing.server.domain.video.dto.response.LikeResponse;
import com.holaclimbing.server.domain.video.dto.response.UploadUrlResponse;
import com.holaclimbing.server.domain.video.dto.response.VideoDetailResponse;
import com.holaclimbing.server.domain.video.dto.response.VideoStatusResponse;
import com.holaclimbing.server.domain.video.dto.response.VideoSummaryResponse;
import com.holaclimbing.server.domain.video.service.CommentService;
import com.holaclimbing.server.domain.video.service.VideoService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
 * 영상 피드·CRUD·좋아요·댓글 API.
 * 조회(GET)는 공개, 등록·수정·삭제·좋아요·댓글작성은 SecurityConfig에서 인증을 요구한다.
 */
@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
@Validated
public class VideoController {

    private final VideoService videoService;
    private final CommentService commentService;

    @PostMapping("/upload-url")
    public ApiResponse<UploadUrlResponse> createUploadUrl(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody UploadUrlRequest request) {
        return ApiResponse.success(videoService.createUploadUrl(userId, request));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<VideoDetailResponse>> createVideo(
            @AuthenticationPrincipal Long userId,
            @Valid @RequestBody CreateVideoRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(videoService.createVideo(userId, request)));
    }

    @GetMapping
    public ApiResponse<PageResponse<VideoSummaryResponse>> getFeed(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Positive int size) {
        return ApiResponse.success(videoService.getFeed(userId, page, size));
    }

    @GetMapping("/{videoId}")
    public ApiResponse<VideoDetailResponse> getVideoDetail(@PathVariable Long videoId,
                                                           @AuthenticationPrincipal Long viewerId) {
        return ApiResponse.success(videoService.getVideoDetail(videoId, viewerId));
    }

    @PatchMapping("/{videoId}")
    public ApiResponse<VideoDetailResponse> updateVideo(@AuthenticationPrincipal Long userId,
                                                        @PathVariable Long videoId,
                                                        @Valid @RequestBody UpdateVideoRequest request) {
        return ApiResponse.success(videoService.updateVideo(userId, videoId, request));
    }

    @DeleteMapping("/{videoId}")
    public ApiResponse<Void> deleteVideo(@AuthenticationPrincipal Long userId,
                                         @PathVariable Long videoId) {
        videoService.deleteVideo(userId, videoId);
        return ApiResponse.success();
    }

    @GetMapping("/{videoId}/status")
    public ApiResponse<VideoStatusResponse> getStatus(@PathVariable Long videoId) {
        return ApiResponse.success(videoService.getStatus(videoId));
    }

    @PostMapping("/{videoId}/like")
    public ApiResponse<LikeResponse> likeVideo(@AuthenticationPrincipal Long userId,
                                               @PathVariable Long videoId) {
        return ApiResponse.success(videoService.likeVideo(userId, videoId));
    }

    @DeleteMapping("/{videoId}/like")
    public ApiResponse<LikeResponse> unlikeVideo(@AuthenticationPrincipal Long userId,
                                                 @PathVariable Long videoId) {
        return ApiResponse.success(videoService.unlikeVideo(userId, videoId));
    }

    @PostMapping("/{videoId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
            @AuthenticationPrincipal Long userId,
            @PathVariable Long videoId,
            @Valid @RequestBody CreateCommentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(commentService.addComment(userId, videoId, request)));
    }

    @GetMapping("/{videoId}/comments")
    public ApiResponse<PageResponse<CommentResponse>> getComments(
            @PathVariable Long videoId,
            @RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Positive int size) {
        return ApiResponse.success(commentService.getComments(videoId, page, size));
    }
}
