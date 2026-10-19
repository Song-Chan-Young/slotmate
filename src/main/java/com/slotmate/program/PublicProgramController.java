package com.slotmate.program;

import com.slotmate.common.api.PageResponse;
import com.slotmate.program.PublicProgramDtos.ProgramDetail;
import com.slotmate.program.PublicProgramDtos.ProgramItem;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** API 명세 6.1·6.2. 인증 불필요, 토큰이 있으면 회차에 본인 예약 상태 포함 */
@RestController
@RequestMapping("/api/v1/programs")
public class PublicProgramController {

    private final ProgramQueryService programQueryService;

    public PublicProgramController(ProgramQueryService programQueryService) {
        this.programQueryService = programQueryService;
    }

    @GetMapping
    public PageResponse<ProgramItem> list(@RequestParam(required = false) ProgramType type,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(defaultValue = "0") @Min(0) int page,
                                          @RequestParam(defaultValue = "20") @Min(1) int size) {
        return programQueryService.list(type, keyword, page, size);
    }

    @GetMapping("/{programId}")
    public ProgramDetail detail(@PathVariable Long programId,
                                @RequestParam(required = false) LocalDate from,
                                @RequestParam(required = false) LocalDate to,
                                @AuthenticationPrincipal Jwt jwt) {
        return programQueryService.detail(programId, from, to, jwt == null ? null : Long.valueOf(jwt.getSubject()));
    }
}
