package com.slotmate.reservation;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

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

    /** 여러 예약의 대기 순번을 쿼리 한 번으로 (countWaitingUpTo 와 같은 기준). 결과 행 = [reservationId, 순번] */
    @Query("""
            select r.id, (select count(w) from Reservation w
                          where w.session = r.session
                            and w.reservationStatus = com.slotmate.reservation.ReservationStatus.WAITING
                            and (w.requestedAt < r.requestedAt or (w.requestedAt = r.requestedAt and w.id <= r.id)))
            from Reservation r where r.id in :reservationIds
            """)
    List<Object[]> countWaitingPositions(@Param("reservationIds") Collection<Long> reservationIds);

    /** 목록 화면용 — WAITING 예약만 골라 예약 ID → 대기 순번. 목록 건수만큼 쿼리가 나가지 않게 함 */
    default Map<Long, Long> waitingPositions(Collection<Reservation> reservations) {
        List<Long> ids = reservations.stream()
                .filter(r -> r.getReservationStatus() == ReservationStatus.WAITING)
                .map(Reservation::getId).toList();
        if (ids.isEmpty()) return Map.of();
        return countWaitingPositions(ids).stream().collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    /** 취소 시 회차 잠금을 먼저 잡기 위해 회차 ID 만 조회 */
    @Query("select r.session.id from Reservation r where r.id = :reservationId")
    Optional<Long> findSessionIdById(@Param("reservationId") Long reservationId);

    // 아래 셋은 취소 트랜잭션용 잠금 조회. 회차 잠금 전에 한 조회(findSessionIdById)로 REPEATABLE READ 스냅샷이
    // 고정되므로, 잠금을 기다린 뒤 일반 조회를 하면 앞 트랜잭션이 바꾼 값을 못 봄 → 잠금 조회는 최신 커밋 값을 읽음

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select r from Reservation r where r.id = :reservationId")
    Optional<Reservation> findByIdForUpdate(@Param("reservationId") Long reservationId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    List<Reservation> findBySessionIdAndReservationStatus(Long sessionId, ReservationStatus reservationStatus);

    /** 승격 대상 — 가장 오래 기다린 대기 (명세 10절 6) */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
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
