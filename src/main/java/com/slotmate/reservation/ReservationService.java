package com.slotmate.reservation;

import com.slotmate.common.error.BusinessException;
import com.slotmate.common.error.ErrorCode;
import com.slotmate.common.time.SeoulTime;
import com.slotmate.reservation.ReservationDtos.ReserveResponse;
import com.slotmate.session.ClassSession;
import com.slotmate.session.ClassSessionRepository;
import com.slotmate.session.SessionStatus;
import com.slotmate.user.User;
import com.slotmate.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class ReservationService {

    private final ClassSessionRepository sessionRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationHistoryRepository historyRepository;
    private final UserRepository userRepository;

    public ReservationService(ClassSessionRepository sessionRepository, ReservationRepository reservationRepository,
                              ReservationHistoryRepository historyRepository, UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.reservationRepository = reservationRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
    }

    /** API 명세 7.1 · DB 설계서 6.1 — 회차 행을 잠근 뒤 정원 확인 */
    @Transactional
    public ReserveResponse reserve(Long sessionId, Long userId) {
        ClassSession session = sessionRepository.findByIdForUpdate(sessionId)
                .filter(s -> s.getSessionStatus() != SessionStatus.DRAFT)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        LocalDateTime now = SeoulTime.now();
        if (session.getSessionStatus() != SessionStatus.OPEN || !session.getStartAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.SESSION_NOT_OPEN);
        }

        Optional<Reservation> existing = reservationRepository.findBySessionIdAndUserId(sessionId, userId);
        if (existing.isPresent() && existing.get().isActive()) {
            throw new BusinessException(ErrorCode.DUPLICATE_ACTIVE_RESERVATION);
        }

        long confirmed = reservationRepository.countBySessionIdAndReservationStatus(sessionId, ReservationStatus.CONFIRMED);
        ReservationStatus status = confirmed < session.getCapacity() ? ReservationStatus.CONFIRMED : ReservationStatus.WAITING;
        User user = userRepository.getReferenceById(userId);

        Reservation reservation;
        ReservationStatus fromStatus;
        ReservationEventType eventType;
        if (existing.isPresent()) {
            reservation = existing.get();
            fromStatus = reservation.getReservationStatus();
            reservation.rebook(status, now);
            eventType = ReservationEventType.REBOOKED;
        } else {
            reservation = Reservation.create(session, user, status, now);
            fromStatus = null;
            eventType = status == ReservationStatus.CONFIRMED ? ReservationEventType.BOOKED : ReservationEventType.WAITLISTED;
        }
        try {
            reservationRepository.saveAndFlush(reservation);
        } catch (DataIntegrityViolationException e) {
            // UNIQUE(session_id, user_id) — 명세 10절 4
            throw new BusinessException(ErrorCode.DUPLICATE_ACTIVE_RESERVATION);
        }
        historyRepository.save(ReservationHistory.of(reservation, fromStatus, eventType, user, now));

        if (status == ReservationStatus.CONFIRMED) {
            return new ReserveResponse(reservation.getId(), sessionId, status, null, "예약이 확정되었습니다.");
        }
        long position = reservationRepository.countWaitingUpTo(sessionId, reservation.getRequestedAt(), reservation.getId());
        return new ReserveResponse(reservation.getId(), sessionId, status, position, "대기 신청이 완료되었습니다.");
    }
}
