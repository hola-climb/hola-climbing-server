package com.holaclimbing.server.domain.video.service;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.gym.mapper.GymMapper;
import com.holaclimbing.server.domain.notification.service.NotificationService;
import com.holaclimbing.server.domain.video.VideoUploadProperties;
import com.holaclimbing.server.domain.video.domain.Video;
import com.holaclimbing.server.domain.video.dto.request.CreateVideoRequest;
import com.holaclimbing.server.domain.video.dto.request.UpdateVideoRequest;
import com.holaclimbing.server.domain.video.dto.request.UploadUrlRequest;
import com.holaclimbing.server.domain.video.dto.response.LikeResponse;
import com.holaclimbing.server.domain.video.dto.response.UploadUrlResponse;
import com.holaclimbing.server.domain.video.dto.response.VideoDetailResponse;
import com.holaclimbing.server.domain.video.dto.response.VideoStatusResponse;
import com.holaclimbing.server.domain.video.dto.response.VideoSummaryResponse;
import com.holaclimbing.server.domain.video.mapper.LikeMapper;
import com.holaclimbing.server.domain.video.mapper.VideoMapper;
import com.holaclimbing.server.infrastructure.ai.AnalysisDispatcher;
import com.holaclimbing.server.infrastructure.gcs.GcsProperties;
import com.holaclimbing.server.infrastructure.gcs.GcsStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VideoServiceImpl implements VideoService {

    /** 영상 등록 시 초기 상태 — AI 분석 대기. */
    private static final String STATUS_PENDING = "pending";

    private final VideoMapper videoMapper;
    private final LikeMapper likeMapper;
    private final GymMapper gymMapper;
    private final NotificationService notificationService;
    private final GcsStorageService gcsStorageService;
    private final GcsProperties gcsProperties;
    private final VideoUploadProperties uploadProperties;
    private final AnalysisDispatcher analysisDispatcher;

    @Override
    public UploadUrlResponse createUploadUrl(Long userId, UploadUrlRequest request) {
        String extension = extractExtension(request.fileName());
        if (!uploadProperties.allowedExtensions().contains(extension)) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_VIDEO_FORMAT);
        }
        if (request.fileSize() > uploadProperties.maxFileSizeBytes()) {
            throw new BusinessException(ErrorCode.VIDEO_TOO_LARGE);
        }
        String objectPath = "%s/%d/%s.%s".formatted(
                gcsProperties.uploadPrefix(), userId, UUID.randomUUID(), extension);
        String uploadUrl = gcsStorageService.createUploadUrl(objectPath, request.mimeType());
        long expiresIn = gcsProperties.signedUrlMinutes() * 60L;
        return new UploadUrlResponse(uploadUrl, objectPath, expiresIn);
    }

    @Override
    @Transactional
    public VideoDetailResponse createVideo(Long userId, CreateVideoRequest request) {
        if (request.gymId() != null && gymMapper.findById(request.gymId()) == null) {
            throw new BusinessException(ErrorCode.GYM_NOT_FOUND);
        }
        if (request.durationSeconds() != null
                && request.durationSeconds() > uploadProperties.maxDurationSeconds()) {
            throw new BusinessException(ErrorCode.VIDEO_TOO_LONG);
        }
        Video video = Video.builder()
                .userId(userId)
                .gymId(request.gymId())
                .title(request.title())
                .description(request.description())
                .grade(request.grade())
                .gcsPath(request.objectPath())
                .thumbnailPath(request.thumbnailPath())
                .durationSeconds(request.durationSeconds())
                .status(STATUS_PENDING)
                .isPublic(request.isPublic() == null || request.isPublic())
                .build();
        videoMapper.insert(video);
        // 등록 즉시 AI 워커에 분석 요청 (비동기 — 실패해도 영상은 pending으로 남는다).
        analysisDispatcher.dispatch(video.getId(), video.getGcsPath());
        return toDetail(videoMapper.findById(video.getId()), false);
    }

    @Override
    public PageResponse<VideoSummaryResponse> getFeed(Long uploaderId, int page, int size) {
        long total = videoMapper.countFeed(uploaderId);
        List<VideoSummaryResponse> content = videoMapper.findFeed(uploaderId, size, page * size)
                .stream()
                .map(v -> VideoSummaryResponse.from(v, gcsStorageService.createReadUrl(v.getGcsPath())))
                .toList();
        return PageResponse.of(content, page, size, total);
    }

    @Override
    @Transactional
    public VideoDetailResponse getVideoDetail(Long videoId, Long viewerId) {
        Video video = findActiveVideo(videoId);
        if (!video.isPublic() && !video.getUserId().equals(viewerId)) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_ACCESSIBLE);
        }
        videoMapper.incrementViewCount(videoId);
        boolean isLiked = viewerId != null && likeMapper.exists(viewerId, videoId);
        return toDetail(videoMapper.findById(videoId), isLiked);
    }

    @Override
    @Transactional
    public VideoDetailResponse updateVideo(Long userId, Long videoId, UpdateVideoRequest request) {
        Video video = findActiveVideo(videoId);
        requireOwner(video, userId);
        videoMapper.updateVideo(videoId, request.title(), request.description(),
                request.grade(), request.isPublic());
        boolean isLiked = likeMapper.exists(userId, videoId);
        return toDetail(videoMapper.findById(videoId), isLiked);
    }

    @Override
    @Transactional
    public void deleteVideo(Long userId, Long videoId) {
        Video video = findActiveVideo(videoId);
        requireOwner(video, userId);
        videoMapper.softDelete(videoId);
    }

    @Override
    public VideoStatusResponse getStatus(Long videoId) {
        Video video = findActiveVideo(videoId);
        return new VideoStatusResponse(videoId, video.getStatus());
    }

    @Override
    @Transactional
    public LikeResponse likeVideo(Long userId, Long videoId) {
        Video video = findActiveVideo(videoId);
        if (likeMapper.exists(userId, videoId)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 좋아요한 영상입니다.");
        }
        likeMapper.insert(userId, videoId);
        videoMapper.incrementLikeCount(videoId);
        notificationService.notifyLike(video.getUserId(), userId, videoId);
        return new LikeResponse(true, videoMapper.findById(videoId).getLikeCount());
    }

    @Override
    @Transactional
    public LikeResponse unlikeVideo(Long userId, Long videoId) {
        Video video = findActiveVideo(videoId);
        if (likeMapper.delete(userId, videoId) > 0) {
            videoMapper.decrementLikeCount(videoId);
        }
        return new LikeResponse(false, videoMapper.findById(videoId).getLikeCount());
    }

    private VideoDetailResponse toDetail(Video video, boolean isLiked) {
        return VideoDetailResponse.of(video, isLiked, gcsStorageService.createReadUrl(video.getGcsPath()));
    }

    private String extractExtension(String fileName) {
        int dot = fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            throw new BusinessException(ErrorCode.UNSUPPORTED_VIDEO_FORMAT);
        }
        return fileName.substring(dot + 1).toLowerCase();
    }

    private Video findActiveVideo(Long videoId) {
        Video video = videoMapper.findById(videoId);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        return video;
    }

    private void requireOwner(Video video, Long userId) {
        if (!video.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
    }
}
