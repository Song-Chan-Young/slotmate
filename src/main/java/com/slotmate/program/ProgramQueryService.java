package com.slotmate.program;

import com.slotmate.common.api.PageResponse;
import com.slotmate.common.error.BusinessException;
import com.slotmate.common.error.ErrorCode;
import com.slotmate.program.PublicProgramDtos.ProgramDetail;
import com.slotmate.program.PublicProgramDtos.ProgramItem;
import com.slotmate.session.SessionQueryService;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/** 회원용 프로그램 조회 (API 명세 6.1·6.2). 활성 프로그램만 */
@Service
@Transactional(readOnly = true)
public class ProgramQueryService {

    private final ProgramRepository programRepository;
    private final SessionQueryService sessionQueryService;

    public ProgramQueryService(ProgramRepository programRepository, SessionQueryService sessionQueryService) {
        this.programRepository = programRepository;
        this.sessionQueryService = sessionQueryService;
    }

    public PageResponse<ProgramItem> list(ProgramType type, String keyword, int page, int size) {
        String trimmed = keyword == null || keyword.isBlank() ? null : keyword.trim();
        PageRequest pageable = PageRequest.of(page, size, Sort.by("id"));
        return PageResponse.of(programRepository.searchActive(type, trimmed, pageable), ProgramItem::from);
    }

    /** 비활성 프로그램은 404 */
    public ProgramDetail detail(Long programId, LocalDate from, LocalDate to, Long userId) {
        Program p = programRepository.findById(programId).filter(Program::isActive)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return new ProgramDetail(p.getId(), p.getName(), p.getProgramType(), p.getDescription(),
                p.getDefaultDurationMinutes(), p.getDefaultCapacity(),
                sessionQueryService.upcomingForProgram(programId, from, to, userId));
    }
}
