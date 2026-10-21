package com.slotmate.program;

import com.slotmate.session.PublicSessionDtos.SessionItem;

import java.util.List;

/** API 명세 6.1·6.2 회원용 프로그램 */
public final class PublicProgramDtos {

    private PublicProgramDtos() {
    }

    public record ProgramItem(Long programId, String name, ProgramCategory category, ProgramType type,
                              String description, int defaultDurationMinutes, int defaultCapacity, int maxPartySize) {
        static ProgramItem from(Program p) {
            return new ProgramItem(p.getId(), p.getName(), p.getProgramType().category(), p.getProgramType(),
                    p.getDescription(), p.getDefaultDurationMinutes(), p.getDefaultCapacity(), p.getMaxPartySize());
        }
    }

    public record ProgramDetail(Long programId, String name, ProgramCategory category, ProgramType type,
                                String description, int defaultDurationMinutes, int defaultCapacity,
                                int maxPartySize, List<SessionItem> sessions) {
    }
}
