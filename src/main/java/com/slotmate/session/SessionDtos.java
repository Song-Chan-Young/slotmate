package com.slotmate.session;

import com.slotmate.common.time.SeoulTime;
import com.slotmate.instructor.Instructor;
import com.slotmate.program.ProgramType;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.OffsetDateTime;

/** API 명세 9.2~9.4 관리자 회차. location 길이 상한은 class_sessions 컬럼 기준 */
public final class SessionDtos {

    private SessionDtos() {
    }

    public record CreateRequest(
            @NotNull Long programId,
            Long instructorId,
            @NotNull OffsetDateTime startAt,
            @NotNull OffsetDateTime endAt,
            @NotNull @Min(1) Integer capacity,
            @Size(max = 150) String location,
            String description,
            SessionStatus initialStatus) {
    }

    /** 보낸 항목만 수정 */
    public record UpdateRequest(
            Long instructorId,
            OffsetDateTime startAt,
            OffsetDateTime endAt,
            @Min(1) Integer capacity,
            @Size(max = 150) String location) {
    }

    public record StatusRequest(@NotNull SessionStatus status) {
    }

    public record SessionResponse(Long sessionId, Long programId, String programName, ProgramType programType,
                                  Long instructorId, String instructorName,
                                  OffsetDateTime startAt, OffsetDateTime endAt,
                                  int capacity, long confirmedCount, long waitingCount,
                                  String location, String description, SessionStatus sessionStatus) {
        static SessionResponse of(ClassSession s, long confirmedCount, long waitingCount) {
            Instructor instructor = s.getInstructor();
            return new SessionResponse(s.getId(), s.getProgram().getId(), s.getProgram().getName(),
                    s.getProgram().getProgramType(),
                    instructor == null ? null : instructor.getId(), instructor == null ? null : instructor.getName(),
                    SeoulTime.toOffset(s.getStartAt()), SeoulTime.toOffset(s.getEndAt()),
                    s.getCapacity(), confirmedCount, waitingCount,
                    s.getLocation(), s.getDescription(), s.getSessionStatus());
        }
    }
}
