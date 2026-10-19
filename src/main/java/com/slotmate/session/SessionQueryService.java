package com.slotmate.session;

import com.slotmate.common.api.PageResponse;
import com.slotmate.common.error.BusinessException;
import com.slotmate.common.error.ErrorCode;
import com.slotmate.common.time.SeoulTime;
import com.slotmate.program.ProgramType;
import com.slotmate.reservation.Reservation;
import com.slotmate.reservation.ReservationRepository;
import com.slotmate.reservation.ReservationStatus;
import com.slotmate.session.PublicSessionDtos.SessionDetail;
import com.slotmate.session.PublicSessionDtos.SessionItem;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** 회원용 회차 조회 (API 명세 6.2~6.4). userId 가 null 이면 비로그인 */
@Service
@Transactional(readOnly = true)
public class SessionQueryService {

    private final ClassSessionRepository sessionRepository;
    private final ReservationRepository reservationRepository;

    public SessionQueryService(ClassSessionRepository sessionRepository, ReservationRepository reservationRepository) {
        this.sessionRepository = sessionRepository;
        this.reservationRepository = reservationRepository;
    }

    /** 6.3 — 시작 시각 오름차순 */
    public PageResponse<SessionItem> listByDate(LocalDate date, ProgramType type, String keyword,
                                                int page, int size, Long userId) {
        String trimmed = keyword == null || keyword.isBlank() ? null : keyword.trim();
        PageRequest pageable = PageRequest.of(page, size, Sort.by("startAt", "id"));
        Page<ClassSession> sessions = sessionRepository.searchPublic(
                date.atStartOfDay(), date.plusDays(1).atStartOfDay(), type, trimmed, pageable);
        Map<Long, SessionItem> items = toItems(sessions.getContent(), userId).stream()
                .collect(Collectors.toMap(SessionItem::sessionId, Function.identity()));
        return PageResponse.of(sessions, s -> items.get(s.getId()));
    }

    /** 6.4 — DRAFT 는 404 */
    public SessionDetail detail(Long sessionId, Long userId) {
        ClassSession session = sessionRepository.findPublicById(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        return SessionDetail.of(session, toItems(List.of(session), userId).getFirst());
    }

    /** 6.2 — 지금 이후 시작하는 공개 회차. from·to 날짜는 선택(둘 다 포함) */
    public List<SessionItem> upcomingForProgram(Long programId, LocalDate from, LocalDate to, Long userId) {
        LocalDateTime now = SeoulTime.now();
        LocalDateTime start = from == null || from.atStartOfDay().isBefore(now) ? now : from.atStartOfDay();
        LocalDateTime end = to == null ? null : to.plusDays(1).atStartOfDay();
        return toItems(sessionRepository.findUpcomingPublic(programId, start, end), userId);
    }

    private List<SessionItem> toItems(List<ClassSession> sessions, Long userId) {
        if (sessions.isEmpty()) {
            return List.of();
        }
        List<Long> ids = sessions.stream().map(ClassSession::getId).toList();

        Map<Long, Map<ReservationStatus, Long>> counts = new HashMap<>();
        for (Object[] row : reservationRepository.countGroupedBySessionAndStatus(ids)) {
            counts.computeIfAbsent((Long) row[0], k -> new HashMap<>()).put((ReservationStatus) row[1], (Long) row[2]);
        }
        Map<Long, Reservation> mine = userId == null ? Map.of()
                : reservationRepository.findBySessionIdInAndUserId(ids, userId).stream()
                .filter(Reservation::isActive)
                .collect(Collectors.toMap(r -> r.getSession().getId(), Function.identity()));

        LocalDateTime now = SeoulTime.now();
        return sessions.stream().map(s -> {
            Map<ReservationStatus, Long> c = counts.getOrDefault(s.getId(), Map.of());
            long confirmed = c.getOrDefault(ReservationStatus.CONFIRMED, 0L);
            long waiting = c.getOrDefault(ReservationStatus.WAITING, 0L);
            Reservation my = mine.get(s.getId());
            ReservationStatus myStatus = my == null ? null : my.getReservationStatus();
            Long myPosition = myStatus == ReservationStatus.WAITING
                    ? reservationRepository.countWaitingUpTo(s.getId(), my.getRequestedAt(), my.getId()) : null;
            return new SessionItem(s.getId(), s.getProgram().getId(), s.getProgram().getName(),
                    s.getProgram().getProgramType(), s.getInstructor() == null ? null : s.getInstructor().getName(),
                    SeoulTime.toOffset(s.getStartAt()), SeoulTime.toOffset(s.getEndAt()),
                    s.getCapacity(), confirmed, waiting, Math.max(0, s.getCapacity() - confirmed),
                    s.getSessionStatus(), bookingAction(s, confirmed, myStatus, now), myStatus, myPosition);
        }).toList();
    }

    /** 끝난 회차 → 내 예약 → 시작 여부 → 마감 → 자리 순으로 판단 */
    private static BookingAction bookingAction(ClassSession s, long confirmed, ReservationStatus myStatus,
                                               LocalDateTime now) {
        if (s.getSessionStatus() == SessionStatus.CANCELLED) return BookingAction.CANCELLED;
        if (s.getSessionStatus() == SessionStatus.COMPLETED) return BookingAction.COMPLETED;
        if (myStatus == ReservationStatus.CONFIRMED) return BookingAction.ALREADY_CONFIRMED;
        if (myStatus == ReservationStatus.WAITING) return BookingAction.ALREADY_WAITING;
        if (!s.getStartAt().isAfter(now)) return BookingAction.STARTED;
        if (s.getSessionStatus() != SessionStatus.OPEN) return BookingAction.CLOSED;
        return confirmed < s.getCapacity() ? BookingAction.BOOKABLE : BookingAction.WAITLISTABLE;
    }
}
