package com.slotmate.reservation;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {

    long countBySessionIdAndReservationStatus(Long sessionId, ReservationStatus reservationStatus);

    Optional<Reservation> findBySessionIdAndUserId(Long sessionId, Long userId);

    List<Reservation> findBySessionIdInAndUserId(Collection<Long> sessionIds, Long userId);

    /** 대기 순번 — 같은 회차 WAITING 중 (requestedAt, id) 가 이 예약 이하인 건수 (DB 설계서 7절) */
    @Query("""
            select count(r) from Reservation r
            where r.session.id = :sessionId and r.reservationStatus = com.slotmate.reservation.ReservationStatus.WAITING
              and (r.requestedAt < :requestedAt or (r.requestedAt = :requestedAt and r.id <= :reservationId))
            """)
    long countWaitingUpTo(@Param("sessionId") Long sessionId, @Param("requestedAt") LocalDateTime requestedAt,
                          @Param("reservationId") Long reservationId);

    /** 회차별·상태별 건수. 결과 행 = [sessionId, status, count] */
    @Query("""
            select r.session.id, r.reservationStatus, count(r) from Reservation r
            where r.session.id in :sessionIds
            group by r.session.id, r.reservationStatus
            """)
    List<Object[]> countGroupedBySessionAndStatus(@Param("sessionIds") Collection<Long> sessionIds);
}
