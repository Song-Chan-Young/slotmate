package com.slotmate.program;

import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.LocalDateTime;

/** programs — 프로그램 종류/소개 (DB 설계서 4.2). 실제 정원·시간은 회차(ClassSession)가 기준 */
@Entity
@Table(name = "programs")
public class Program {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "program_id")
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "program_type", nullable = false, length = 20)
    private ProgramType programType;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(name = "default_duration_minutes", nullable = false)
    private short defaultDurationMinutes;

    @Column(name = "default_capacity", nullable = false)
    private int defaultCapacity;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected Program() {
    }

    public Long getId() { return id; }
    public ProgramType getProgramType() { return programType; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public short getDefaultDurationMinutes() { return defaultDurationMinutes; }
    public int getDefaultCapacity() { return defaultCapacity; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
