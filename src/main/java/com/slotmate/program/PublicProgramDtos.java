package com.slotmate.program;

import com.slotmate.session.PublicSessionDtos.SessionItem;

import java.util.List;

/** API 명세 6.1·6.2 회원용 프로그램 */
public final class PublicProgramDtos {

    private PublicProgramDtos() {
    }

    public record ProgramItem(Long programId, String name, ProgramType type, String description,
                              int defaultDurationMinutes, int defaultCapacity) {
        static ProgramItem from(Program p) {
            return new ProgramItem(p.getId(), p.getName(), p.getProgramType(), p.getDescription(),
                    p.getDefaultDurationMinutes(), p.getDefaultCapacity());
        }
    }

    public record ProgramDetail(Long programId, String name, ProgramType type, String description,
                                int defaultDurationMinutes, int defaultCapacity, List<SessionItem> sessions) {
    }
}
