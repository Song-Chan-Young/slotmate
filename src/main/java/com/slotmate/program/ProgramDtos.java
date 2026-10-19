package com.slotmate.program;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** API 명세 9.1 관리자 프로그램. 길이·범위 상한은 programs 컬럼 기준 */
public final class ProgramDtos {

    private ProgramDtos() {
    }

    public record CreateRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull ProgramType type,
            String description,
            @NotNull @Min(1) @Max(Short.MAX_VALUE) Integer defaultDurationMinutes,
            @NotNull @Min(1) Integer defaultCapacity) {
    }

    /** 보낸 항목만 수정 */
    public record UpdateRequest(
            @Size(min = 1, max = 100) String name,
            String description,
            @Min(1) @Max(Short.MAX_VALUE) Integer defaultDurationMinutes,
            @Min(1) Integer defaultCapacity) {
    }

    public record ActiveRequest(@NotNull Boolean active) {
    }

    public record ProgramResponse(Long programId, String name, ProgramType type, String description,
                                  int defaultDurationMinutes, int defaultCapacity, boolean active) {
        static ProgramResponse from(Program p) {
            return new ProgramResponse(p.getId(), p.getName(), p.getProgramType(), p.getDescription(),
                    p.getDefaultDurationMinutes(), p.getDefaultCapacity(), p.isActive());
        }
    }
}
