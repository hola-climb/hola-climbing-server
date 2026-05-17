package com.holaclimbing.server.domain.user.mapper;

import com.holaclimbing.server.domain.user.domain.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface UserMapper {

    /** 신규 회원 저장. 생성된 PK는 user.id로 채워진다. */
    void insert(User user);

    /** 활성 회원 단건 조회 (soft-delete 제외). 없으면 null. */
    User findById(Long id);

    /** 로그인용 이메일 조회 (soft-delete 제외). 없으면 null. */
    User findByEmail(String email);

    /** 이메일 인증 토큰으로 조회 (soft-delete 제외). 없으면 null. */
    User findByEmailVerificationToken(String token);

    /** 중복 검사용 — soft-delete된 행도 포함 (email UNIQUE 제약이 전체 행에 걸리므로). */
    boolean existsByEmail(String email);

    /** 중복 검사용 — soft-delete된 행도 포함 (nickname UNIQUE 제약이 전체 행에 걸리므로). */
    boolean existsByNickname(String nickname);

    /** 이메일 인증 완료 처리 — verified=true, 토큰 제거. */
    int markEmailVerified(Long id);

    /** 이메일 인증 토큰 재발급(갱신). */
    int updateEmailVerificationToken(@Param("id") Long id, @Param("token") String token);

    /** 마지막 로그인 시각 갱신. */
    int updateLastLoginAt(Long id);

    /** 프로필 부분 수정 — null이 아닌 필드만 갱신. */
    int updateProfile(@Param("id") Long id,
                      @Param("nickname") String nickname,
                      @Param("profileImage") String profileImage,
                      @Param("bio") String bio);
}
