package com.holaclimbing.server.domain.gym.mapper;

import com.holaclimbing.server.domain.gym.domain.GymReview;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GymReviewMapper {

    /** 리뷰 저장. 생성된 PK는 review.id로 채워진다. */
    void insert(GymReview review);

    /** 리뷰 단건 조회. 없으면 null. */
    GymReview findById(Long id);

    /** 해당 사용자가 이 암장에 이미 리뷰를 남겼는지. */
    boolean existsByGymAndUser(@Param("gymId") Long gymId, @Param("userId") Long userId);

    /** 암장 리뷰 목록 (최신순). */
    List<GymReview> findByGymId(@Param("gymId") Long gymId,
                                @Param("size") int size,
                                @Param("offset") int offset);

    /** 암장 리뷰 총 개수. */
    long countByGymId(Long gymId);

    /** 리뷰 수정. */
    int update(@Param("id") Long id, @Param("rating") int rating, @Param("content") String content);

    /** 리뷰 삭제. */
    int delete(Long id);

    /** gyms.rating_avg / rating_count를 gym_reviews 기준으로 재집계. */
    void recalcGymRating(Long gymId);
}
