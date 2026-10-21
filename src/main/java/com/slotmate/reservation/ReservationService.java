package com.slotmate.reservation;

import com.slotmate.common.api.PageResponse;
import com.slotmate.common.error.BusinessException;
import com.slotmate.common.error.ErrorCode;
import com.slotmate.common.time.SeoulTime;
import com.slotmate.notification.NotificationService;
import com.slotmate.reservation.ReservationDtos.CancelResponse;
import com.slotmate.reservation.ReservationDtos.MyReservationItem;
import com.slotmate.reservation.ReservationDtos.ReserveResponse;
import com.slotmate.reservation.ReservationDtos.SessionReservationItem;
import com.slotmate.session.ClassSession;
import com.slotmate.session.ClassSessionRepository;
import com.slotmate.session.SessionStatus;
import com.slotmate.user.User;
import com.slotmate.user.UserRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;

@Service
public class ReservationService {

    private final ClassSessionRepository sessionRepository;
    private final ReservationRepository reservationRepository;
    private final ReservationHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    public ReservationService(ClassSessionRepository sessionRepository, ReservationRepository reservationRepository,
                              ReservationHistoryRepository historyRepository, UserRepository userRepository,
                              NotificationService notificationService) {
        this.sessionRepository = sessionRepository;
        this.reservationRepository = reservationRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.notificationService = notificationService;
    }

    /** API 명세 7.1 · DB 설계서 6.1 — 회차 행을 잠근 뒤 자리 확인. 인원이 다 들어가야 확정, 아니면 팀 전체 대기 (확장 설계 v0.1 3.2) */
    @Transactional
    public ReserveResponse reserve(Long sessionId, Long userId, int partySize) {
        ClassSession session = sessionRepository.findByIdForUpdate(sessionId)
                .filter(s -> s.getSessionStatus() != SessionStatus.DRAFT)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        LocalDateTime now = SeoulTime.now();
        if (session.getSessionStatus() != SessionStatus.OPEN || !session.getStartAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.SESSION_NOT_OPEN);
        }
        if (partySize < 1 || partySize > session.getProgram().getMaxPartySize() || partySize > session.getCapacity()) {
            throw new BusinessException(ErrorCode.INVALID_PARTY_SIZE);
        }

        Optional<Reservation> existing = reservationRepository.findBySessionIdAndUserId(sessionId, userId);
        if (existing.isPresent() && existing.get().isActive()) {
            throw new BusinessException(ErrorCode.DUPLICATE_ACTIVE_RESERVATION);
        }

        long confirmedSeats = reservationRepository.sumPartySize(sessionId, ReservationStatus.CONFIRMED);
        ReservationStatus status = confirmedSeats + partySize <= session.getCapacity()
                ? ReservationStatus.CONFIRMED : ReservationStatus.WAITING;
        User user = userRepository.getReferenceById(userId);

        Reservation reservation;
        ReservationStatus fromStatus;
        ReservationEventType eventType;
        if (existing.isPresent()) {
            reservation = existing.get();
            fromStatus = reservation.getReservationStatus();
            reservation.rebook(partySize, status, now);
            eventType = ReservationEventType.REBOOKED;
        } else {
            reservation = Reservation.create(session, user, partySize, status, now);
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
            notificationService.bookingConfirmed(reservation);
            return new ReserveResponse(reservation.getId(), sessionId, status, partySize, null, "예약이 확정되었습니다.");
        }
        long position = reservationRepository.countWaitingUpTo(sessionId, reservation.getRequestedAt(), reservation.getId());
        notificationService.waitlisted(reservation, position);
        return new ReserveResponse(reservation.getId(), sessionId, status, partySize, position, "대기 신청이 완료되었습니다.");
    }

    /** API 명세 7.2 — 회차 시작 시각 내림차순. from·to 날짜 모두 포함 */
    @Transactional(readOnly = true)
    public PageResponse<MyReservationItem> searchMine(Long userId, ReservationStatus status,
                                                      LocalDate from, LocalDate to, int page, int size) {
        if (from != null && to != null && from.isAfter(to)) {
            throw BusinessException.invalidField("to", "종료 날짜는 시작 날짜보다 빠를 수 없습니다.");
        }
        PageRequest pageable = PageRequest.of(page, size,
                Sort.by(Sort.Order.desc("session.startAt"), Sort.Order.desc("id")));
        Page<Reservation> reservations = reservationRepository.searchMine(userId, status,
                from == null ? null : from.atStartOfDay(), to == null ? null : to.plusDays(1).atStartOfDay(), pageable);
        Map<Long, Long> positions = reservationRepository.waitingPositions(reservations.getContent());
        return PageResponse.of(reservations, r -> MyReservationItem.of(r, positions.get(r.getId())));
    }

    /**
     * API 명세 7.3 · 10절 5 — 회차 행을 잠근 뒤 취소. 확정 취소로 자리가 나고 회차가 OPEN 이면
     * 오래 기다린 순으로 남은 자리에 들어가는 팀을 같은 트랜잭션에서 확정 (확장 설계 v0.1 3.3, first-fit)
     */
    @Transactional
    public CancelResponse cancel(Long reservationId, Long userId) {
        Long sessionId = reservationRepository.findSessionIdById(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        ClassSession session = sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Reservation reservation = reservationRepository.findByIdForUpdate(reservationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!reservation.getUser().getId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        LocalDateTime now = SeoulTime.now();
        if (!reservation.isActive() || !session.getStartAt().isAfter(now)) {
            throw new BusinessException(ErrorCode.RESERVATION_NOT_CANCELLABLE);
        }

        ReservationStatus fromStatus = reservation.getReservationStatus();
        reservation.cancel(now);
        historyRepository.save(ReservationHistory.of(reservation, fromStatus, ReservationEventType.CANCELLED,
                reservation.getUser(), now));
        notificationService.cancelled(reservation, fromStatus == ReservationStatus.WAITING);

        if (fromStatus == ReservationStatus.CONFIRMED && session.getSessionStatus() == SessionStatus.OPEN) {
            reservationRepository.flush();
            long freeSeats = session.getCapacity() - reservationRepository
                    .findBySessionIdAndReservationStatus(sessionId, ReservationStatus.CONFIRMED).stream()
                    .mapToLong(Reservation::getPartySize).sum();
            for (Reservation waiting : reservationRepository.findBySessionIdAndReservationStatusOrderByRequestedAtAscIdAsc(
                    sessionId, ReservationStatus.WAITING)) {
                if (freeSeats <= 0) break;
                if (waiting.getPartySize() > freeSeats) continue; // 안 들어가는 팀은 순번을 유지한 채 건너뜀
                waiting.promote(now);
                freeSeats -= waiting.getPartySize();
                historyRepository.save(ReservationHistory.of(waiting, ReservationStatus.WAITING,
                        ReservationEventType.PROMOTED, null, now));
                notificationService.promoted(waiting);
            }
        }
        String message = fromStatus == ReservationStatus.CONFIRMED ? "예약이 취소되었습니다." : "대기가 취소되었습니다.";
        return new CancelResponse(reservation.getId(), reservation.getReservationStatus(), message);
    }

    /** API 명세 9.5 — 신청 시각 오름차순 (대기는 그대로 순번 순) */
    @Transactional(readOnly = true)
    public PageResponse<SessionReservationItem> searchBySession(Long sessionId, ReservationStatus status,
                                                                int page, int size) {
        if (!sessionRepository.existsById(sessionId)) {
            throw new BusinessException(ErrorCode.RESOURCE_NOT_FOUND);
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by("requestedAt", "id"));
        Page<Reservation> reservations = reservationRepository.searchBySession(sessionId, status, pageable);
        Map<Long, Long> positions = reservationRepository.waitingPositions(reservations.getContent());
        return PageResponse.of(reservations, r -> SessionReservationItem.of(r, positions.get(r.getId())));
    }
}
