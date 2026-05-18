package com.holaclimbing.server.domain.notification.mapper;

import com.holaclimbing.server.domain.notification.domain.Notification;
import com.holaclimbing.server.domain.notification.domain.NotificationSettings;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface NotificationMapper {

    /** 알림 저장. 생성된 PK는 notification.id로 채워진다. */
    void insert(Notification notification);

    /** 수신자의 알림 목록 (최신순). unreadOnly이면 미읽음만. */
    List<Notification> findByRecipient(@Param("recipientId") Long recipientId,
                                       @Param("unreadOnly") boolean unreadOnly,
                                       @Param("size") int size,
                                       @Param("offset") int offset);

    /** findByRecipient 결과 총 개수. */
    long countByRecipient(@Param("recipientId") Long recipientId,
                          @Param("unreadOnly") boolean unreadOnly);

    /** 수신자의 미읽음 알림 개수. */
    long countUnread(Long recipientId);

    /** 단건 읽음 처리 — 본인 알림만. 갱신된 행 수 반환. */
    int markRead(@Param("id") Long id, @Param("recipientId") Long recipientId);

    /** 수신자의 모든 미읽음 알림을 읽음 처리. */
    int markAllRead(Long recipientId);

    /** 단건 삭제 — 본인 알림만. 삭제된 행 수 반환. */
    int delete(@Param("id") Long id, @Param("recipientId") Long recipientId);

    /** 알림 설정 조회. 없으면 null. */
    NotificationSettings findSettings(Long userId);

    /** 알림 설정 upsert. */
    void upsertSettings(@Param("userId") Long userId,
                        @Param("notifyComment") boolean notifyComment,
                        @Param("notifyReply") boolean notifyReply,
                        @Param("notifyLike") boolean notifyLike,
                        @Param("notifyFollow") boolean notifyFollow,
                        @Param("notifyChat") boolean notifyChat,
                        @Param("notifySystem") boolean notifySystem);
}
