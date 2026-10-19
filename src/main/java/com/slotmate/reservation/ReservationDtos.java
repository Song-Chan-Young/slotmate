package com.slotmate.reservation;

/** API 명세 7.1 예약 또는 대기 신청 응답 */
public final class ReservationDtos {

    private ReservationDtos() {
    }

    public record ReserveResponse(Long reservationId, Long sessionId, ReservationStatus reservationStatus,
                                  Long waitingPosition, String message) {
    }
}
