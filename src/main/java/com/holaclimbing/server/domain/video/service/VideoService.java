package com.holaclimbing.server.domain.video.service;

import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.video.dto.request.CreateVideoRequest;
import com.holaclimbing.server.domain.video.dto.request.UpdateVideoRequest;
import com.holaclimbing.server.domain.video.dto.request.UploadUrlRequest;
import com.holaclimbing.server.domain.video.dto.response.LikeResponse;
import com.holaclimbing.server.domain.video.dto.response.UploadUrlResponse;
import com.holaclimbing.server.domain.video.dto.response.VideoDetailResponse;
import com.holaclimbing.server.domain.video.dto.response.VideoStatusResponse;
import com.holaclimbing.server.domain.video.dto.response.VideoSummaryResponse;

public interface VideoService {

    /** 업로드용 GCS Signed URL 발급. 클라이언트는 이 URL로 영상을 직접 PUT 업로드한다. */
    UploadUrlResponse createUploadUrl(Long userId, UploadUrlRequest request);

    /** 영상 등록 (메타데이터). */
    VideoDetailResponse createVideo(Long userId, CreateVideoRequest request);

    /** 공개 피드 조회. uploaderId가 있으면 해당 업로더로 필터. */
    PageResponse<VideoSummaryResponse> getFeed(Long uploaderId, int page, int size);

    /** 영상 상세 조회. 비공개 영상은 소유자만 접근 가능하며, 조회 시 조회수가 증가한다. */
    VideoDetailResponse getVideoDetail(Long videoId, Long viewerId);

    /** 영상 분석 진행 상태 조회. */
    VideoStatusResponse getStatus(Long videoId);

    /** 영상 부분 수정 (소유자만). */
    VideoDetailResponse updateVideo(Long userId, Long videoId, UpdateVideoRequest request);

    /** 영상 삭제 (소유자만, soft-delete). */
    void deleteVideo(Long userId, Long videoId);

    /** 영상 좋아요. */
    LikeResponse likeVideo(Long userId, Long videoId);

    /** 영상 좋아요 취소. */
    LikeResponse unlikeVideo(Long userId, Long videoId);
}
