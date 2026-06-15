package com.holaclimbing.server.domain.admin.mapper;

import com.holaclimbing.server.domain.gym.domain.Gym;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface AdminGymMapper {

    List<Gym> search(@Param("status") String status,
                     @Param("keyword") String keyword,
                     @Param("regionCode") String regionCode,
                     @Param("size") int size,
                     @Param("offset") int offset);

    long countSearch(@Param("status") String status,
                     @Param("keyword") String keyword,
                     @Param("regionCode") String regionCode);

    Gym findByIdAnyStatus(Long gymId);

    int updateStatus(@Param("gymId") Long gymId, @Param("status") String status);

    int updateGym(@Param("gymId") Long gymId,
                  @Param("name") String name,
                  @Param("address") String address,
                  @Param("lat") Double lat,
                  @Param("lng") Double lng,
                  @Param("phone") String phone,
                  @Param("website") String website,
                  @Param("description") String description,
                  @Param("businessHours") String businessHours,
                  @Param("regionCode") String regionCode);

    int updateThumbnailUrl(@Param("gymId") Long gymId,
                           @Param("thumbnailUrl") String thumbnailUrl);

    int deactivateGrades(Long gymId);

    void insertGrade(@Param("gymId") Long gymId,
                     @Param("label") String label,
                     @Param("difficultyOrder") int difficultyOrder);
}
