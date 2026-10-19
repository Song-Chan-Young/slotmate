package com.slotmate.reservation;

import com.slotmate.reservation.ReservationDtos.ReserveResponse;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** API 명세 7.1 */
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
}
