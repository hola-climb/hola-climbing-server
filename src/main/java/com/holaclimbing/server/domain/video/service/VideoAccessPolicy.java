package com.holaclimbing.server.domain.video.service;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.domain.user.mapper.UserBlockMapper;
import com.holaclimbing.server.domain.video.domain.Video;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class VideoAccessPolicy {

    private final UserBlockMapper userBlockMapper;

    public void requireViewable(Video video, Long viewerId) {
        if (!video.isPublic() && (viewerId == null || !video.getUserId().equals(viewerId))) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_ACCESSIBLE);
        }
        if (viewerId != null
                && !video.getUserId().equals(viewerId)
                && (userBlockMapper.exists(viewerId, video.getUserId())
                || userBlockMapper.exists(video.getUserId(), viewerId))) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_ACCESSIBLE);
        }
    }
}
