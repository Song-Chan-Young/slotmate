package com.slotmate.notification;

import com.slotmate.common.api.PageResponse;
import com.slotmate.common.error.BusinessException;
import com.slotmate.common.error.ErrorCode;
import com.slotmate.common.time.SeoulTime;
import com.slotmate.notification.NotificationDtos.NotificationItem;
import com.slotmate.reservation.Reservation;
import com.slotmate.session.ClassSession;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/** API 명세 8절 · DB 설계서 6.1·6.2 — 예약 상태가 바뀐 트랜잭션 안에서 인앱 알림 생성 */
@Service
public class NotificationService {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final NotificationRepository notificationRepository;

    public NotificationService(NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    /** 최신순 */
    @Transactional(readOnly = true)
    public PageResponse<NotificationItem> searchMine(Long userId, boolean unreadOnly, int page, int size) {
        PageRequest pageable = PageRequest.of(page, size, Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id")));
        return PageResponse.of(notificationRepository.searchMine(userId, unreadOnly, pageable), NotificationItem::of);
    }

    /** 남의 알림도 없는 알림과 같은 404 (명세 8.2 존재 여부 비노출) */
    @Transactional
    public NotificationItem markRead(Long notificationId, Long userId) {
        Notification notification = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        notification.markRead(SeoulTime.now());
        return NotificationItem.of(notification);
    }

    public void bookingConfirmed(Reservation reservation) {
        save(reservation, NotificationType.BOOKING_CONFIRMED, "예약이 확정되었습니다", when(reservation.getSession()));
    }

    public void waitlisted(Reservation reservation, long position) {
        save(reservation, NotificationType.WAITLISTED, "대기 신청이 완료되었습니다",
                when(reservation.getSession()) + " · 현재 대기 " + position + "번째");
    }

    public void cancelled(Reservation reservation, boolean wasWaiting) {
        save(reservation, NotificationType.CANCELLED, wasWaiting ? "대기가 취소되었습니다" : "예약이 취소되었습니다",
                when(reservation.getSession()));
    }

    public void promoted(Reservation reservation) {
        save(reservation, NotificationType.PROMOTED, "대기하던 수업이 예약 확정되었습니다",
                when(reservation.getSession()) + " · 앞선 예약이 취소되어 자동으로 확정되었습니다.");
    }

    private void save(Reservation reservation, NotificationType type, String title, String message) {
        notificationRepository.save(Notification.create(reservation.getUser(), reservation, type, title, message));
    }

    /** 예: 10월 22일 (목) 07:00 매트 필라테스 */
    private static String when(ClassSession session) {
        var start = session.getStartAt();
        return "%d월 %d일 (%s) %s %s".formatted(start.getMonthValue(), start.getDayOfMonth(),
                start.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.KOREAN), start.format(TIME),
                session.getProgram().getName());
    }
}
