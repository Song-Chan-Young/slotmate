package com.slotmate.reservation;

import com.slotmate.session.ClassSession;
import com.slotmate.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/**
 * reservations — 회원별 회차 예약의 현재 상태 (DB 설계서 4.5).
 * 회원·회차당 한 행(UNIQUE). 취소 후 재예약은 같은 행을 쓰고 attemptNo 증가.
 * 대기 순번은 저장하지 않음 — WAITING 을 requestedAt, id 순으로 정렬해 계산.
 */
@Entity
@Table(name = "reservations",
        uniqueConstraints = @UniqueConstraint(name = "uq_reservations_session_user",
                columnNames = {"session_id", "user_id"}))
public class Reservation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "reservation_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "session_id", nullable = false)
    private ClassSession session;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(name = "reservation_status", nullable = false, length = 20)
    private ReservationStatus reservationStatus;

    /** 예약 인원 — 정원은 자리 수로 셈 (확장 설계 v0.1 3.1) */
    @Column(name = "party_size", nullable = false)
    private int partySize;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Reservation() {
    }

    /** 첫 신청. status 는 CONFIRMED 또는 WAITING */
    public static Reservation create(ClassSession session, User user, int partySize, ReservationStatus status,
                                     LocalDateTime now) {
        Reservation reservation = new Reservation();
        reservation.session = session;
        reservation.user = user;
        reservation.attemptNo = 1;
        reservation.apply(partySize, status, now);
        return reservation;
    }

    /** 취소된 예약 재신청 — 같은 행을 쓰고 attemptNo 증가 (DB 설계서 3). 인원은 새로 받음 */
    public void rebook(int partySize, ReservationStatus status, LocalDateTime now) {
        this.attemptNo++;
        this.cancelledAt = null;
        apply(partySize, status, now);
    }

    private void apply(int partySize, ReservationStatus status, LocalDateTime now) {
        this.partySize = partySize;
        this.reservationStatus = status;
        this.requestedAt = now;
        this.confirmedAt = status == ReservationStatus.CONFIRMED ? now : null;
    }

    public void cancel(LocalDateTime now) {
        this.reservationStatus = ReservationStatus.CANCELLED;
        this.cancelledAt = now;
    }

    /** 대기 → 확정 자동 승격. requestedAt 은 그대로 */
    public void promote(LocalDateTime now) {
        this.reservationStatus = ReservationStatus.CONFIRMED;
        this.confirmedAt = now;
    }

    public boolean isActive() {
        return reservationStatus == ReservationStatus.CONFIRMED || reservationStatus == ReservationStatus.WAITING;
    }

    public Long getId() { return id; }
    public ClassSession getSession() { return session; }
    public User getUser() { return user; }
    public ReservationStatus getReservationStatus() { return reservationStatus; }
    public int getPartySize() { return partySize; }
    public int getAttemptNo() { return attemptNo; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
