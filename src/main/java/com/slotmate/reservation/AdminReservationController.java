package com.slotmate.reservation;

import com.slotmate.common.api.PageResponse;
import com.slotmate.reservation.ReservationDtos.SessionReservationItem;
import jakarta.validation.constraints.Min;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** API 명세 9.5 */
@RestController
public class AdminReservationController {

    private final ReservationService reservationService;

    public AdminReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @GetMapping("/api/v1/admin/sessions/{sessionId}/reservations")
    public PageResponse<SessionReservationItem> search(@PathVariable Long sessionId,
                                                       @RequestParam(required = false) ReservationStatus status,
                                                       @RequestParam(defaultValue = "0") @Min(0) int page,
                                                       @RequestParam(defaultValue = "20") @Min(1) int size) {
        return reservationService.searchBySession(sessionId, status, page, size);
    }
}
