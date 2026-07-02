package com.holaclimbing.server.domain.video.service;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.common.response.PageResponse;
import com.holaclimbing.server.domain.notification.service.NotificationService;
import com.holaclimbing.server.domain.video.domain.Comment;
import com.holaclimbing.server.domain.video.domain.Video;
import com.holaclimbing.server.domain.video.dto.request.CreateCommentRequest;
import com.holaclimbing.server.domain.video.dto.request.UpdateCommentRequest;
import com.holaclimbing.server.domain.video.dto.response.CommentResponse;
import com.holaclimbing.server.domain.video.mapper.CommentMapper;
import com.holaclimbing.server.domain.video.mapper.VideoMapper;
import com.holaclimbing.server.infrastructure.gcs.GcsStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CommentServiceImpl implements CommentService {

    private final CommentMapper commentMapper;
    private final VideoMapper videoMapper;
    private final NotificationService notificationService;
    private final VideoAccessPolicy videoAccessPolicy;
    private final GcsStorageService gcsStorageService;

    @Override
    @Transactional
    public CommentResponse addComment(Long userId, Long videoId, CreateCommentRequest request) {
        Video video = videoMapper.findVisibleById(videoId);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        videoAccessPolicy.requireViewable(video, userId);
        Comment parent = null;
        if (request.parentId() != null) {
            parent = commentMapper.findById(request.parentId());
            if (parent == null || !parent.getVideoId().equals(videoId)) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "부모 댓글이 올바르지 않습니다.");
            }
        }
        Comment comment = Comment.builder()
                .userId(userId)
                .videoId(videoId)
                .parentId(request.parentId())
                .content(request.content())
                .build();
        commentMapper.insert(comment);
        videoMapper.incrementCommentCount(videoId);

        if (parent != null) {
            notificationService.notifyReply(parent.getUserId(), userId, comment.getId());
        } else {
            notificationService.notifyComment(video.getUserId(), userId, videoId);
        }
        return toResponse(commentMapper.findById(comment.getId()));
    }

    @Override
    public PageResponse<CommentResponse> getComments(Long videoId, int page, int size, Long viewerId) {
        Video video = videoMapper.findVisibleById(videoId);
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        videoAccessPolicy.requireViewable(video, viewerId);
        long total = commentMapper.countByVideoId(videoId, viewerId);
        List<CommentResponse> content = commentMapper.findByVideoId(videoId, size, page * size, viewerId)
                .stream().map(this::toResponse).toList();
        return PageResponse.of(content, page, size, total);
    }

    @Override
    @Transactional
    public CommentResponse updateComment(Long userId, Long commentId, UpdateCommentRequest request) {
        Comment comment = commentMapper.findById(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND, "댓글을 찾을 수 없습니다.");
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        requireVideoViewable(comment, userId);
        commentMapper.update(commentId, request.content());
        return toResponse(commentMapper.findById(commentId));
    }

    @Override
    @Transactional
    public void deleteComment(Long userId, Long commentId) {
        Comment comment = commentMapper.findById(commentId);
        if (comment == null) {
            throw new BusinessException(ErrorCode.NOT_FOUND);
        }
        if (!comment.getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        requireVideoViewable(comment, userId);
        commentMapper.softDelete(commentId);
        videoMapper.decrementCommentCount(comment.getVideoId());
    }

    private void requireVideoViewable(Comment comment, Long userId) {
        Video video = videoMapper.findVisibleById(comment.getVideoId());
        if (video == null) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_FOUND);
        }
        videoAccessPolicy.requireViewable(video, userId);
    }

    private CommentResponse toResponse(Comment comment) {
        return CommentResponse.from(comment, resolveProfileImage(comment.getProfileImage()));
    }

    private String resolveProfileImage(String storedProfileImage) {
        if (storedProfileImage == null || storedProfileImage.isBlank()) {
            return null;
        }
        if (storedProfileImage.startsWith("http://") || storedProfileImage.startsWith("https://")) {
            return storedProfileImage;
        }
        return gcsStorageService.createReadUrl(storedProfileImage);
    }
}
