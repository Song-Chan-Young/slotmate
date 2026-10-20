package com.slotmate.notification;

import com.slotmate.common.api.PageResponse;
import com.slotmate.notification.NotificationDtos.NotificationItem;
import jakarta.validation.constraints.Min;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API 명세 8.1·8.2 */
@RestController
@RequestMapping("/api/v1/my/notifications")
public class NotificationController {

    private final NotificationService notificationService;

    public NotificationController(NotificationService notificationService) {
        this.notificationService = notificationService;
    }

    @GetMapping
    public PageResponse<NotificationItem> list(@RequestParam(defaultValue = "false") boolean unreadOnly,
                                               @RequestParam(defaultValue = "0") @Min(0) int page,
                                               @RequestParam(defaultValue = "20") @Min(1) int size,
                                               @AuthenticationPrincipal Jwt jwt) {
        return notificationService.searchMine(Long.valueOf(jwt.getSubject()), unreadOnly, page, size);
    }

    @PatchMapping("/{notificationId}/read")
    public NotificationItem markRead(@PathVariable Long notificationId, @AuthenticationPrincipal Jwt jwt) {
        return notificationService.markRead(notificationId, Long.valueOf(jwt.getSubject()));
    }
}
