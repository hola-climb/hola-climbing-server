package com.holaclimbing.server.domain.video.mapper;

import com.holaclimbing.server.domain.video.domain.Comment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CommentMapper {

    /** 댓글 저장. 생성된 PK는 comment.id로 채워진다. */
    void insert(Comment comment);

    /** 댓글 단건 조회 (soft-delete 제외). 없으면 null. */
    Comment findById(Long id);

    /** 영상의 댓글 목록 (오래된 순). */
    List<Comment> findByVideoId(@Param("videoId") Long videoId,
                                @Param("size") int size,
                                @Param("offset") int offset);

    /** 영상의 댓글 개수. */
    long countByVideoId(Long videoId);

    /** 댓글 내용 수정. */
    int update(@Param("id") Long id, @Param("content") String content);

    /** 댓글 soft-delete. */
    int softDelete(Long id);
}
