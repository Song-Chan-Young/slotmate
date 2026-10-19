package com.slotmate.session;

import com.slotmate.instructor.Instructor;
import com.slotmate.program.Program;
import com.slotmate.user.User;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** class_sessions — 실제 날짜·시간별 수업 회차 (DB 설계서 4.4). 예약 대상은 회차(BR-001) */
@Entity
@Table(name = "class_sessions")
public class ClassSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "session_id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private Program program;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instructor_id")
    private Instructor instructor;

    @Column(name = "start_at", nullable = false)
    private LocalDateTime startAt;

    @Column(name = "end_at", nullable = false)
    private LocalDateTime endAt;

    @Column(nullable = false)
    private int capacity;

    @Column(length = 150)
    private String location;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "session_status", nullable = false, length = 20)
    private SessionStatus sessionStatus;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ClassSession() {
    }

    public static ClassSession create(Program program, Instructor instructor, LocalDateTime startAt,
                                      LocalDateTime endAt, int capacity, String location, String description,
                                      SessionStatus sessionStatus, User createdBy) {
        ClassSession session = new ClassSession();
        session.program = program;
        session.instructor = instructor;
        session.startAt = startAt;
        session.endAt = endAt;
        session.capacity = capacity;
        session.location = location;
        session.description = description;
        session.sessionStatus = sessionStatus;
        session.createdBy = createdBy;
        return session;
    }

    /** null 인 값은 그대로 둠 */
    public void update(Instructor instructor, LocalDateTime startAt, LocalDateTime endAt,
                       Integer capacity, String location) {
        if (instructor != null) this.instructor = instructor;
        if (startAt != null) this.startAt = startAt;
        if (endAt != null) this.endAt = endAt;
        if (capacity != null) this.capacity = capacity;
        if (location != null) this.location = location;
    }

    public void changeStatus(SessionStatus sessionStatus) {
        this.sessionStatus = sessionStatus;
    }

    public Long getId() { return id; }
    public Program getProgram() { return program; }
    public Instructor getInstructor() { return instructor; }
    public LocalDateTime getStartAt() { return startAt; }
    public LocalDateTime getEndAt() { return endAt; }
    public int getCapacity() { return capacity; }
    public String getLocation() { return location; }
    public String getDescription() { return description; }
    public SessionStatus getSessionStatus() { return sessionStatus; }
    public User getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
