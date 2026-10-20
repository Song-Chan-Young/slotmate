package com.slotmate.user;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** A05 회원 관리 — 회원(MEMBER)만. keyword 는 이름 또는 이메일, status·keyword 는 null 이면 조건 없음 */
    @Query("""
            select u from User u
            where u.role = com.slotmate.user.Role.MEMBER
              and (:status is null or u.accountStatus = :status)
              and (:keyword is null or u.displayName like concat('%', :keyword, '%')
                   or u.email like concat('%', :keyword, '%'))
            """)
    Page<User> searchMembers(@Param("keyword") String keyword, @Param("status") AccountStatus status,
                             Pageable pageable);
}
