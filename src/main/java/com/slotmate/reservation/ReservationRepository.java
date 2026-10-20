package com.slotmate.reservation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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

    /** 취소 시 회차 잠금을 먼저 잡기 위해 회차 ID 만 조회 */
    @Query("select r.session.id from Reservation r where r.id = :reservationId")
    Optional<Long> findSessionIdById(@Param("reservationId") Long reservationId);

    /** 승격 대상 — 가장 오래 기다린 대기 (명세 10절 6) */
    Optional<Reservation> findFirstBySessionIdAndReservationStatusOrderByRequestedAtAscIdAsc(
            Long sessionId, ReservationStatus reservationStatus);

    /** 7.2 내 예약. status·from·to 는 null 이면 조건 없음 */
    @Query(value = """
            select r from Reservation r
            join fetch r.session s
            join fetch s.program
            left join fetch s.instructor
            where r.user.id = :userId
              and (:status is null or r.reservationStatus = :status)
              and (:from is null or s.startAt >= :from)
              and (:to is null or s.startAt < :to)
            """,
            countQuery = """
            select count(r) from Reservation r join r.session s
            where r.user.id = :userId
              and (:status is null or r.reservationStatus = :status)
              and (:from is null or s.startAt >= :from)
              and (:to is null or s.startAt < :to)
            """)
    Page<Reservation> searchMine(@Param("userId") Long userId, @Param("status") ReservationStatus status,
                                 @Param("from") LocalDateTime from, @Param("to") LocalDateTime to,
                                 Pageable pageable);

    /** 9.5 회차별 예약자. status 는 null 이면 조건 없음 */
    @Query(value = """
            select r from Reservation r
            join fetch r.user
            where r.session.id = :sessionId
              and (:status is null or r.reservationStatus = :status)
            """,
            countQuery = """
            select count(r) from Reservation r
            where r.session.id = :sessionId
              and (:status is null or r.reservationStatus = :status)
            """)
    Page<Reservation> searchBySession(@Param("sessionId") Long sessionId, @Param("status") ReservationStatus status,
                                      Pageable pageable);

    /** 회차별·상태별 건수. 결과 행 = [sessionId, status, count] */
    @Query("""
            select r.session.id, r.reservationStatus, count(r) from Reservation r
            where r.session.id in :sessionIds
            group by r.session.id, r.reservationStatus
            """)
    List<Object[]> countGroupedBySessionAndStatus(@Param("sessionIds") Collection<Long> sessionIds);
}
