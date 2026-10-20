package com.slotmate.notification;

import com.slotmate.common.time.SeoulTime;
import com.slotmate.reservation.Reservation;

import java.time.OffsetDateTime;

/** API 명세 8절 */
public final class NotificationDtos {

    private NotificationDtos() {
    }

    /** sessionId 는 화면에서 관련 수업으로 이동할 때 씀 */
    public record NotificationItem(Long notificationId, NotificationType notificationType, String title,
                                   String message, boolean read, OffsetDateTime createdAt, OffsetDateTime readAt,
                                   Long reservationId, Long sessionId) {
        static NotificationItem of(Notification n) {
            Reservation r = n.getReservation();
            return new NotificationItem(n.getId(), n.getNotificationType(), n.getTitle(), n.getMessage(), n.isRead(),
                    SeoulTime.toOffset(n.getCreatedAt()), SeoulTime.toOffset(n.getReadAt()),
                    r == null ? null : r.getId(), r == null ? null : r.getSession().getId());
        }
    }
}
