package com.slotmate.user;

import com.slotmate.common.api.PageResponse;
import com.slotmate.common.time.SeoulTime;
import jakarta.validation.constraints.Min;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

/** 화면 설계서 A05 회원 관리 — 조회만 (명세 13.8). 비밀번호 해시는 반환 안 함 */
@RestController
public class AdminMemberController {

    private final UserRepository userRepository;

    public AdminMemberController(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public record MemberItem(Long userId, String displayName, String email, AccountStatus accountStatus,
                             OffsetDateTime createdAt) {
    }

    /** 가입 최신순 */
    @GetMapping("/api/v1/admin/members")
    @Transactional(readOnly = true)
    public PageResponse<MemberItem> search(@RequestParam(required = false) String keyword,
                                           @RequestParam(required = false) AccountStatus status,
                                           @RequestParam(defaultValue = "0") @Min(0) int page,
                                           @RequestParam(defaultValue = "20") @Min(1) int size) {
        String trimmed = keyword == null || keyword.isBlank() ? null : keyword.trim();
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.of(userRepository.searchMembers(trimmed, status, pageable),
                u -> new MemberItem(u.getId(), u.getDisplayName(), u.getEmail(), u.getAccountStatus(),
                        SeoulTime.toOffset(u.getCreatedAt())));
    }
}
