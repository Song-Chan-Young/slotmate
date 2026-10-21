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

    /** 예약 1건 최대 인원 (확장 설계 v0.1 3.1). 운동 수업은 1 */
    @Column(name = "max_party_size", nullable = false)
    private short maxPartySize;

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

    public static Program create(ProgramType programType, String name, String description,
                                 short defaultDurationMinutes, int defaultCapacity, short maxPartySize) {
        Program program = new Program();
        program.programType = programType;
        program.name = name;
        program.description = description;
        program.defaultDurationMinutes = defaultDurationMinutes;
        program.defaultCapacity = defaultCapacity;
        program.maxPartySize = maxPartySize;
        program.active = true;
        return program;
    }

    /** null 인 값은 그대로 둠 */
    public void update(String name, String description, Short defaultDurationMinutes, Integer defaultCapacity,
                       Short maxPartySize) {
        if (name != null) this.name = name;
        if (description != null) this.description = description;
        if (defaultDurationMinutes != null) this.defaultDurationMinutes = defaultDurationMinutes;
        if (defaultCapacity != null) this.defaultCapacity = defaultCapacity;
        if (maxPartySize != null) this.maxPartySize = maxPartySize;
    }

    public void changeActive(boolean active) {
        this.active = active;
    }

    public Long getId() { return id; }
    public ProgramType getProgramType() { return programType; }
    public String getName() { return name; }
    public String getDescription() { return description; }
    public short getDefaultDurationMinutes() { return defaultDurationMinutes; }
    public int getDefaultCapacity() { return defaultCapacity; }
    public short getMaxPartySize() { return maxPartySize; }
    public boolean isActive() { return active; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}
