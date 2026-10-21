package com.slotmate.program;

import com.slotmate.common.api.PageResponse;
import com.slotmate.common.error.BusinessException;
import com.slotmate.common.error.ErrorCode;
import com.slotmate.program.ProgramDtos.CreateRequest;
import com.slotmate.program.ProgramDtos.ProgramResponse;
import com.slotmate.program.ProgramDtos.UpdateRequest;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminProgramService {

    private final ProgramRepository programRepository;

    public AdminProgramService(ProgramRepository programRepository) {
        this.programRepository = programRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProgramResponse> search(ProgramType type, String keyword, int page, int size) {
        String trimmed = keyword == null || keyword.isBlank() ? null : keyword.trim();
        PageRequest pageable = PageRequest.of(page, size, Sort.by("id"));
        return PageResponse.of(programRepository.search(type, trimmed, pageable), ProgramResponse::from);
    }

    @Transactional
    public ProgramResponse create(CreateRequest request) {
        Program program = Program.create(request.type(), request.name(), request.description(),
                request.defaultDurationMinutes().shortValue(), request.defaultCapacity(), request.maxPartySizeOrDefault());
        return ProgramResponse.from(programRepository.save(program));
    }

    @Transactional
    public ProgramResponse update(Long programId, UpdateRequest request) {
        Program program = find(programId);
        Short duration = request.defaultDurationMinutes() == null ? null : request.defaultDurationMinutes().shortValue();
        Short maxPartySize = request.maxPartySize() == null ? null : request.maxPartySize().shortValue();
        program.update(request.name(), request.description(), duration, request.defaultCapacity(), maxPartySize);
        return ProgramResponse.from(program);
    }

    @Transactional
    public ProgramResponse changeActive(Long programId, boolean active) {
        Program program = find(programId);
        program.changeActive(active);
        return ProgramResponse.from(program);
    }

    private Program find(Long programId) {
        return programRepository.findById(programId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }
}
