package com.holaclimbing.server.domain.video.service;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.common.response.CursorCodec;
import com.holaclimbing.server.common.response.CursorPageResponse;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.common.upload.ImageUploadValidator;
import com.holaclimbing.server.common.upload.ImageUploadValidator.ImageUpload;
import com.holaclimbing.server.domain.gym.domain.GymGrade;
import com.holaclimbing.server.domain.gym.mapper.GymGradeMapper;
import com.holaclimbing.server.domain.gym.mapper.GymMapper;
import com.holaclimbing.server.domain.notification.service.NotificationService;
import com.holaclimbing.server.domain.video.VideoUploadProperties;
import com.holaclimbing.server.domain.video.domain.Video;
import com.holaclimbing.server.domain.video.dto.request.CreateVideoRequest;
import com.holaclimbing.server.domain.video.dto.request.UpdateVideoRequest;
import com.holaclimbing.server.domain.video.dto.request.UploadUrlRequest;
import com.holaclimbing.server.domain.video.dto.response.LikeResponse;
import com.holaclimbing.server.domain.video.dto.response.ShareLinkResponse;
import com.holaclimbing.server.domain.video.dto.response.ThumbnailUploadResponse;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class VideoServiceImpl implements VideoService {

    /** 영상 등록 시 초기 상태 — AI 분석 대기. */
    private static final String STATUS_PENDING = "pending";
    private static final long MAX_THUMBNAIL_IMAGE_BYTES = 5L * 1024 * 1024;
    private static final String THUMBNAIL_PREFIX = "videos/thumbnails";

    private final VideoMapper videoMapper;
    private final LikeMapper likeMapper;
    private final GymMapper gymMapper;
    private final GymGradeMapper gymGradeMapper;
    private final NotificationService notificationService;
    private final GcsStorageService gcsStorageService;
    private final GcsProperties gcsProperties;
    private final VideoUploadProperties uploadProperties;
    private final AnalysisDispatcher analysisDispatcher;
    private final VideoAccessPolicy videoAccessPolicy;

    @Value("${app.frontend-base-url}")
    private String frontendBaseUrl;

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
    public ThumbnailUploadResponse uploadThumbnail(Long userId, MultipartFile image) {
        ImageUpload upload = ImageUploadValidator.validate(image, "썸네일 이미지", MAX_THUMBNAIL_IMAGE_BYTES);
        String thumbnailPath = "%s/%d/%s.%s".formatted(
                THUMBNAIL_PREFIX, userId, UUID.randomUUID(), upload.extension());
        try {
            gcsStorageService.uploadBytes(thumbnailPath, upload.contentType(), image.getBytes());
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.GCS_UPLOAD_FAILED);
        }
        return new ThumbnailUploadResponse(thumbnailPath, gcsStorageService.createReadUrl(thumbnailPath));
    }

    @Override
    @Transactional
    public VideoDetailResponse createVideo(Long userId, CreateVideoRequest request) {
        if (gymMapper.findById(request.gymId()) == null) {
            throw new BusinessException(ErrorCode.GYM_NOT_FOUND);
        }
        GymGrade gymGrade = gymGradeMapper.findActiveByGymAndId(request.gymId(), request.gymGradeId());
        if (gymGrade == null) {
            throw new BusinessException(ErrorCode.INVALID_GYM_GRADE);
        }
        if (request.durationSeconds() != null
                && request.durationSeconds() > uploadProperties.maxDurationSeconds()) {
            throw new BusinessException(ErrorCode.VIDEO_TOO_LONG);
        }
        // objectPath는 createUploadUrl()이 발급한 자기 소유 경로(videos/uploads/{userId}/...)여야 한다.
        // 그렇지 않으면 다른 사용자의 GCS 객체를 자기 영상으로 등록할 수 있다.
        requireOwnedObjectPath(userId, request.objectPath());
        String thumbnailPath = normalizeOwnedThumbnailPath(userId, request.thumbnailPath());
        Video video = Video.builder()
                .userId(userId)
                .gymId(request.gymId())
                .gymGradeId(gymGrade.getId())
                .title(request.title())
                .description(request.description())
                .gcsPath(request.objectPath())
                .thumbnailPath(thumbnailPath)
                .durationSeconds(request.durationSeconds())
                .recordedDate(request.recordedDate())
                .status(STATUS_PENDING)
                .isPublic(request.isPublic() == null || request.isPublic())
                .build();
        videoMapper.insert(video);
        // 등록 즉시 AI 워커에 분석 요청 (비동기 — 실패해도 영상은 pending으로 남는다).
        analysisDispatcher.dispatch(video.getId(), video.getGcsPath());
        return toDetail(videoMapper.findById(video.getId()), false);
    }

    @Override
    public CursorPageResponse<VideoSummaryResponse> getFeed(Long uploaderId, String cursor, LocalDate recordedDate,
                                                            int size, Long viewerId) {
        Long cursorId = CursorCodec.decode(cursor);
        // hasNext 판정을 위해 한 건 더 가져온다.
        List<Video> rows = videoMapper.findFeedByCursor(uploaderId, cursorId, recordedDate, size + 1, viewerId);
        boolean hasNext = rows.size() > size;
        List<Video> pageRows = hasNext ? rows.subList(0, size) : rows;
        List<VideoSummaryResponse> content = pageRows.stream()
                .map(v -> VideoSummaryResponse.from(v,
                        gcsStorageService.createReadUrl(v.getGcsPath()),
                        gcsStorageService.createReadUrl(v.getThumbnailPath())))
                .toList();
        String nextCursor = hasNext ? CursorCodec.encode(pageRows.get(pageRows.size() - 1).getId()) : null;
        return CursorPageResponse.of(content, nextCursor, hasNext);
    }

    @Override
    public PageResponse<VideoSummaryResponse> getGymVideos(Long gymId, int page, int size, Long viewerId) {
        long total = videoMapper.countByGym(gymId, viewerId);
        List<VideoSummaryResponse> content = videoMapper.findByGym(gymId, size, page * size, viewerId)
                .stream()
                .map(v -> VideoSummaryResponse.from(v,
                        gcsStorageService.createReadUrl(v.getGcsPath()),
                        gcsStorageService.createReadUrl(v.getThumbnailPath())))
                .toList();
        return PageResponse.of(content, page, size, total);
    }

    @Override
    public VideoDetailResponse getVideoDetail(Long videoId, Long viewerId) {
        Video video = findActiveVideo(videoId);
        videoAccessPolicy.requireViewable(video, viewerId);
        // 의도적으로 비-트랜잭션. view counter는 eventual하게 증가해도 무방하고,
        // 단일 read에 UPDATE를 묶어 PK 락 보유 시간을 늘리지 않는다.
        videoMapper.incrementViewCount(videoId);
        boolean isLiked = viewerId != null && likeMapper.exists(viewerId, videoId);
        return toDetail(videoMapper.findById(videoId), isLiked);
    }

    @Override
    @Transactional
    public VideoDetailResponse updateVideo(Long userId, Long videoId, UpdateVideoRequest request) {
        Video video = findActiveVideo(videoId);
        requireOwner(video, userId);
        videoMapper.updateVideo(videoId, request.title(), request.description(), request.isPublic());
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
    public VideoStatusResponse getStatus(Long videoId, Long viewerId) {
        Video video = findActiveVideo(videoId);
        videoAccessPolicy.requireViewable(video, viewerId);
        return new VideoStatusResponse(videoId, video.getStatus());
    }

    @Override
    @Transactional
    public LikeResponse likeVideo(Long userId, Long videoId) {
        Video video = findActiveVideo(videoId);
        videoAccessPolicy.requireViewable(video, userId);
        // ON CONFLICT DO NOTHING으로 안전화. inserted == 0이면 이미 좋아요 상태였음.
        int inserted = likeMapper.insert(userId, videoId);
        if (inserted == 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "이미 좋아요한 영상입니다.");
        }
        videoMapper.incrementLikeCount(videoId);
        notificationService.notifyLike(video.getUserId(), userId, videoId);
        return new LikeResponse(true, videoMapper.findById(videoId).getLikeCount());
    }

    @Override
    @Transactional
    public LikeResponse unlikeVideo(Long userId, Long videoId) {
        Video video = findActiveVideo(videoId);
        videoAccessPolicy.requireViewable(video, userId);
        if (likeMapper.delete(userId, videoId) > 0) {
            videoMapper.decrementLikeCount(videoId);
        }
        return new LikeResponse(false, videoMapper.findById(videoId).getLikeCount());
    }

    @Override
    public ShareLinkResponse createShareLink(Long viewerId, Long videoId) {
        Video video = findActiveVideo(videoId);
        videoAccessPolicy.requireViewable(video, viewerId);
        String shareUrl = frontendBaseUrl + "/videos/" + videoId;
        return new ShareLinkResponse(shareUrl);
    }

    private VideoDetailResponse toDetail(Video video, boolean isLiked) {
        return VideoDetailResponse.of(video, isLiked,
                gcsStorageService.createReadUrl(video.getGcsPath()),
                gcsStorageService.createReadUrl(video.getThumbnailPath()));
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

    /**
     * 클라이언트가 보낸 objectPath가 본인의 업로드 prefix({@code uploadPrefix}/{userId}/) 아래인지 검증.
     * createUploadUrl()이 항상 이 패턴으로 발급하므로 매칭 실패 = 다른 사용자 경로 도용 시도.
     */
    private void requireOwnedObjectPath(Long userId, String objectPath) {
        if (objectPath == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "영상 경로가 비어 있습니다.");
        }
        String expectedPrefix = gcsProperties.uploadPrefix() + "/" + userId + "/";
        if (!objectPath.startsWith(expectedPrefix)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인이 업로드한 영상 경로가 아닙니다.");
        }
    }

    private String normalizeOwnedThumbnailPath(Long userId, String thumbnailPath) {
        if (thumbnailPath == null || thumbnailPath.isBlank()) {
            return null;
        }
        String expectedPrefix = THUMBNAIL_PREFIX + "/" + userId + "/";
        if (!thumbnailPath.startsWith(expectedPrefix)) {
            throw new BusinessException(ErrorCode.FORBIDDEN, "본인이 업로드한 썸네일 경로가 아닙니다.");
        }
        return thumbnailPath;
    }
}
