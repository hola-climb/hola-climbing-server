package com.holaclimbing.server.domain.video.service;

import com.holaclimbing.server.common.exception.BusinessException;
import com.holaclimbing.server.common.exception.error.ErrorCode;
import com.holaclimbing.server.domain.video.domain.Video;
import org.springframework.stereotype.Component;

@Component
public class VideoAccessPolicy {

    public void requireViewable(Video video, Long viewerId) {
        if (!video.isPublic() && (viewerId == null || !video.getUserId().equals(viewerId))) {
            throw new BusinessException(ErrorCode.VIDEO_NOT_ACCESSIBLE);
        }
    }
}
