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

    public Long getId() { return id; }
    public ClassSession getSession() { return session; }
    public User getUser() { return user; }
    public ReservationStatus getReservationStatus() { return reservationStatus; }
    public int getAttemptNo() { return attemptNo; }
    public LocalDateTime getRequestedAt() { return requestedAt; }
    public LocalDateTime getConfirmedAt() { return confirmedAt; }
    public LocalDateTime getCancelledAt() { return cancelledAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
