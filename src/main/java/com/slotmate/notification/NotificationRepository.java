package com.slotmate.notification;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    /** 8.1 — unreadOnly 면 읽지 않은 것만. 관련 예약·회차를 함께 읽음 */
    @Query(value = """
            select n from Notification n
            left join fetch n.reservation r
            left join fetch r.session
            where n.user.id = :userId and (:unreadOnly = false or n.read = false)
            """,
            countQuery = """
            select count(n) from Notification n
            where n.user.id = :userId and (:unreadOnly = false or n.read = false)
            """)
    Page<Notification> searchMine(@Param("userId") Long userId, @Param("unreadOnly") boolean unreadOnly,
                                  Pageable pageable);

    Optional<Notification> findByIdAndUserId(Long id, Long userId);
}
