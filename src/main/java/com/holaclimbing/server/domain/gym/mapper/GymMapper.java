package com.holaclimbing.server.domain.gym.mapper;

import com.holaclimbing.server.domain.gym.domain.Gym;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface GymMapper {

    /** 활성 암장 단건 조회 (status='active', soft-delete 제외). 없으면 null. */
    Gym findById(Long id);

    /** status 무관 단건 조회 (soft-delete만 제외). 본인 pending 암장 수정 권한 검사용. */
    Gym findByIdIncludingPending(Long id);

    /** 이름(부분일치)·지역으로 암장 검색 (이름순). */
    List<Gym> search(@Param("keyword") String keyword,
                      @Param("region") String region,
                      @Param("size") int size,
                      @Param("offset") int offset);

    /** search 결과 총 개수. */
    long countSearch(@Param("keyword") String keyword, @Param("region") String region);

    /** 좌표 기준 반경(km) 내 암장을 가까운 순으로 조회. */
    List<Gym> findNearby(@Param("lat") double lat,
                         @Param("lng") double lng,
                         @Param("radiusKm") double radiusKm,
                         @Param("size") int size);

    /** 암장 등록. 생성된 PK는 gym.id로 채워진다. */
    void insertGym(Gym gym);

    /** 암장 요일별 운영시간(business_hours jsonb) 갱신. */
    int updateBusinessHours(@Param("gymId") Long gymId,
                            @Param("businessHours") String businessHours);
}
