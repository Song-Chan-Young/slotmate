package com.slotmate.program;

import com.slotmate.common.api.PageResponse;
import com.slotmate.program.ProgramDtos.ActiveRequest;
import com.slotmate.program.ProgramDtos.CreateRequest;
import com.slotmate.program.ProgramDtos.ProgramResponse;
import com.slotmate.program.ProgramDtos.UpdateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API 명세 9.1 */
@RestController
@RequestMapping("/api/v1/admin/programs")
public class AdminProgramController {

    private final AdminProgramService adminProgramService;

    public AdminProgramController(AdminProgramService adminProgramService) {
        this.adminProgramService = adminProgramService;
    }

    @GetMapping
    public PageResponse<ProgramResponse> search(@RequestParam(required = false) ProgramType type,
                                                @RequestParam(required = false) String keyword,
                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "20") @Min(1) int size) {
        return adminProgramService.search(type, keyword, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProgramResponse create(@Valid @RequestBody CreateRequest request) {
        return adminProgramService.create(request);
    }

    @PatchMapping("/{programId}")
    public ProgramResponse update(@PathVariable Long programId, @Valid @RequestBody UpdateRequest request) {
        return adminProgramService.update(programId, request);
    }

    @PatchMapping("/{programId}/active")
    public ProgramResponse changeActive(@PathVariable Long programId, @Valid @RequestBody ActiveRequest request) {
        return adminProgramService.changeActive(programId, request.active());
    }
}
