package com.slotmate.session;

import com.slotmate.common.api.PageResponse;
import com.slotmate.program.ProgramCategory;
import com.slotmate.program.ProgramType;
import com.slotmate.session.PublicSessionDtos.SessionDetail;
import com.slotmate.session.PublicSessionDtos.SessionItem;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** API 명세 6.3·6.4. 인증 불필요, 토큰이 있으면 본인 예약 상태 포함 */
@RestController
@RequestMapping("/api/v1/sessions")
public class PublicSessionController {

    private final SessionQueryService sessionQueryService;

    public PublicSessionController(SessionQueryService sessionQueryService) {
        this.sessionQueryService = sessionQueryService;
    }

    @GetMapping
    public PageResponse<SessionItem> list(@RequestParam LocalDate date,
                                          @RequestParam(required = false) ProgramCategory category,
                                          @RequestParam(required = false) ProgramType type,
                                          @RequestParam(required = false) String keyword,
                                          @RequestParam(defaultValue = "0") @Min(0) int page,
                                          @RequestParam(defaultValue = "20") @Min(1) int size,
                                          @AuthenticationPrincipal Jwt jwt) {
        return sessionQueryService.listByDate(date, category, type, keyword, page, size, userIdOf(jwt));
    }

    @GetMapping("/{sessionId}")
    public SessionDetail detail(@PathVariable Long sessionId, @AuthenticationPrincipal Jwt jwt) {
        return sessionQueryService.detail(sessionId, userIdOf(jwt));
    }

    static Long userIdOf(Jwt jwt) {
        return jwt == null ? null : Long.valueOf(jwt.getSubject());
    }
}
