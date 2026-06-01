package com.holaclimbing.server.domain.video;

import com.holaclimbing.server.common.response.ApiResponse;
import com.holaclimbing.server.common.response.CursorPageResponse;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.video.dto.request.CreateCommentRequest;
import com.holaclimbing.server.domain.video.dto.request.CreateVideoRequest;
import com.holaclimbing.server.domain.video.dto.request.UpdateVideoRequest;
import com.holaclimbing.server.domain.video.dto.request.UploadUrlRequest;
import com.holaclimbing.server.domain.video.dto.response.CommentResponse;
import com.holaclimbing.server.domain.video.dto.response.LikeResponse;
import com.holaclimbing.server.domain.video.dto.response.ShareLinkResponse;
import com.holaclimbing.server.domain.video.dto.response.UploadUrlResponse;
import com.holaclimbing.server.domain.video.dto.response.VideoDetailResponse;
import com.holaclimbing.server.domain.video.dto.response.VideoStatusResponse;
import com.holaclimbing.server.domain.video.dto.response.VideoSummaryResponse;
import com.holaclimbing.server.domain.video.service.CommentService;
import com.holaclimbing.server.domain.video.service.VideoService;
import com.holaclimbing.server.infrastructure.ai.AnalysisStatusStore;
import com.holaclimbing.server.infrastructure.ai.VideoAnalysisSseService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
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
@Slf4j
@RestController
@RequestMapping("/api/videos")
@RequiredArgsConstructor
@Validated
public class VideoController {

    private final VideoService videoService;
    private final CommentService commentService;
    private final VideoAnalysisSseService analysisSseService;
    private final AnalysisStatusStore analysisStatusStore;

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
    public ApiResponse<CursorPageResponse<VideoSummaryResponse>> getFeed(
            @RequestParam(required = false) Long userId,
            @RequestParam(required = false) String cursor,
            @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
        return ApiResponse.success(videoService.getFeed(userId, cursor, size));
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

    /**
     * 분석 진행률 SSE 스트림. 연결 즉시 저장된 최신 상태(QUEUED/PROCESSING/COMPLETED/FAILED)를
     * 1회 replay하고, 이후 Python 워커가 발행하는 진행 이벤트를 그대로 푸시한다.
     */
    @GetMapping(value = "/{videoId}/analysis/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamAnalysisProgress(@PathVariable Long videoId) {
        SseEmitter emitter = analysisSseService.connect(videoId);
        analysisStatusStore.find(videoId).ifPresent(progress -> {
            try {
                emitter.send(SseEmitter.event().name("progress").data(progress));
            } catch (IOException e) {
                log.debug("SSE 초기 replay 실패 — videoId={}", videoId);
            }
        });
        return emitter;
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

    /** 영상 공유 링크 발급 (F-02-08). 공개 영상은 누구나, 비공개는 소유자만. */
    @PostMapping("/{videoId}/share")
    public ApiResponse<ShareLinkResponse> shareVideo(@AuthenticationPrincipal Long userId,
                                                     @PathVariable Long videoId) {
        return ApiResponse.success(videoService.createShareLink(userId, videoId));
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
            @RequestParam(defaultValue = "20") @Positive @Max(100) int size) {
        return ApiResponse.success(commentService.getComments(videoId, page, size));
    }
}
