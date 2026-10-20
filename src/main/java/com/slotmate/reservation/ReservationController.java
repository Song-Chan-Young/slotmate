package com.slotmate.reservation;

import com.slotmate.common.api.PageResponse;
import com.slotmate.reservation.ReservationDtos.CancelResponse;
import com.slotmate.reservation.ReservationDtos.MyReservationItem;
import com.slotmate.reservation.ReservationDtos.ReserveResponse;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/** API 명세 7.1~7.3 */
@RestController
public class ReservationController {

    private final ReservationService reservationService;

    public ReservationController(ReservationService reservationService) {
        this.reservationService = reservationService;
    }

    @PostMapping("/api/v1/sessions/{sessionId}/reservations")
    @ResponseStatus(HttpStatus.CREATED)
    public ReserveResponse reserve(@PathVariable Long sessionId, @AuthenticationPrincipal Jwt jwt) {
        return reservationService.reserve(sessionId, Long.valueOf(jwt.getSubject()));
    }

    @GetMapping("/api/v1/my/reservations")
    public PageResponse<MyReservationItem> myReservations(@RequestParam(required = false) ReservationStatus status,
                                                          @RequestParam(required = false) LocalDate from,
                                                          @RequestParam(required = false) LocalDate to,
                                                          @RequestParam(defaultValue = "0") @Min(0) int page,
                                                          @RequestParam(defaultValue = "20") @Min(1) int size,
                                                          @AuthenticationPrincipal Jwt jwt) {
        return reservationService.searchMine(Long.valueOf(jwt.getSubject()), status, from, to, page, size);
    }

    @DeleteMapping("/api/v1/reservations/{reservationId}")
    public CancelResponse cancel(@PathVariable Long reservationId, @AuthenticationPrincipal Jwt jwt) {
        return reservationService.cancel(reservationId, Long.valueOf(jwt.getSubject()));
    }
}
