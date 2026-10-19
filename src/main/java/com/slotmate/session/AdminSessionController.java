package com.slotmate.session;

import com.slotmate.common.api.PageResponse;
import com.slotmate.program.ProgramType;
import com.slotmate.session.SessionDtos.CreateRequest;
import com.slotmate.session.SessionDtos.SessionResponse;
import com.slotmate.session.SessionDtos.StatusRequest;
import com.slotmate.session.SessionDtos.UpdateRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** API 명세 9.2~9.4 */
@RestController
@RequestMapping("/api/v1/admin/sessions")
public class AdminSessionController {

    private final AdminSessionService adminSessionService;

    public AdminSessionController(AdminSessionService adminSessionService) {
        this.adminSessionService = adminSessionService;
    }

    @GetMapping
    public PageResponse<SessionResponse> search(@RequestParam LocalDate from,
                                                @RequestParam LocalDate to,
                                                @RequestParam(required = false) ProgramType type,
                                                @RequestParam(required = false) SessionStatus status,
                                                @RequestParam(defaultValue = "0") @Min(0) int page,
                                                @RequestParam(defaultValue = "20") @Min(1) int size) {
        return adminSessionService.search(from, to, type, status, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public SessionResponse create(@Valid @RequestBody CreateRequest request, @AuthenticationPrincipal Jwt jwt) {
        return adminSessionService.create(request, Long.valueOf(jwt.getSubject()));
    }

    @PatchMapping("/{sessionId}")
    public SessionResponse update(@PathVariable Long sessionId, @Valid @RequestBody UpdateRequest request) {
        return adminSessionService.update(sessionId, request);
    }

    @PatchMapping("/{sessionId}/status")
    public SessionResponse changeStatus(@PathVariable Long sessionId, @Valid @RequestBody StatusRequest request) {
        return adminSessionService.changeStatus(sessionId, request.status());
    }
}
