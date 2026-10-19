package com.slotmate.reservation;

import com.slotmate.user.User;
import jakarta.persistence.*;

import java.time.LocalDateTime;

/** reservation_histories — 예약 상태 변경 이력 (DB 설계서 4.6). 상태 변경과 같은 트랜잭션에서 추가 */
@Entity
@Table(name = "reservation_histories")
public class ReservationHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reservation_id", nullable = false)
    private Reservation reservation;

    @Column(name = "attempt_no", nullable = false)
    private int attemptNo;

    /** 최초 생성 이벤트는 null */
    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 20)
    private ReservationStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 20)
    private ReservationStatus toStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 30)
    private ReservationEventType eventType;

    /** 자동 처리(승격 등)면 null */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Column(length = 500)
    private String memo;

    @Column(name = "changed_at", nullable = false)
    private LocalDateTime changedAt;

    protected ReservationHistory() {
    }

    public Long getId() { return id; }
    public Reservation getReservation() { return reservation; }
    public int getAttemptNo() { return attemptNo; }
    public ReservationStatus getFromStatus() { return fromStatus; }
    public ReservationStatus getToStatus() { return toStatus; }
    public ReservationEventType getEventType() { return eventType; }
    public User getActor() { return actor; }
    public String getMemo() { return memo; }
    public LocalDateTime getChangedAt() { return changedAt; }
}
