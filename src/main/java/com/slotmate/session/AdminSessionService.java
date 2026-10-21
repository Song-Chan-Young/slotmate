package com.slotmate.session;

import com.slotmate.common.api.PageResponse;
import com.slotmate.common.error.BusinessException;
import com.slotmate.common.error.ErrorCode;
import com.slotmate.common.time.SeoulTime;
import com.slotmate.instructor.Instructor;
import com.slotmate.instructor.InstructorRepository;
import com.slotmate.program.Program;
import com.slotmate.program.ProgramRepository;
import com.slotmate.program.ProgramType;
import com.slotmate.reservation.ReservationRepository;
import com.slotmate.reservation.ReservationStatus;
import com.slotmate.session.SessionDtos.CreateRequest;
import com.slotmate.session.SessionDtos.SessionResponse;
import com.slotmate.session.SessionDtos.UpdateRequest;
import com.slotmate.user.UserRepository;
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
import java.util.Objects;

@Service
public class AdminSessionService {

    private final ClassSessionRepository sessionRepository;
    private final ProgramRepository programRepository;
    private final InstructorRepository instructorRepository;
    private final ReservationRepository reservationRepository;
    private final UserRepository userRepository;

    public AdminSessionService(ClassSessionRepository sessionRepository, ProgramRepository programRepository,
                               InstructorRepository instructorRepository, ReservationRepository reservationRepository,
                               UserRepository userRepository) {
        this.sessionRepository = sessionRepository;
        this.programRepository = programRepository;
        this.instructorRepository = instructorRepository;
        this.reservationRepository = reservationRepository;
        this.userRepository = userRepository;
    }

    /** from·to 날짜 모두 포함. 시작 시각 오름차순 */
    @Transactional(readOnly = true)
    public PageResponse<SessionResponse> search(LocalDate from, LocalDate to, ProgramType type,
                                                SessionStatus status, int page, int size) {
        if (from.isAfter(to)) {
            throw BusinessException.invalidField("to", "종료 날짜는 시작 날짜보다 빠를 수 없습니다.");
        }
        PageRequest pageable = PageRequest.of(page, size, Sort.by("startAt", "id"));
        Page<ClassSession> sessions = sessionRepository.searchForAdmin(
                from.atStartOfDay(), to.plusDays(1).atStartOfDay(), type, status, pageable);

        Map<Long, Map<ReservationStatus, Long>> counts = new HashMap<>();
        List<Long> ids = sessions.getContent().stream().map(ClassSession::getId).toList();
        if (!ids.isEmpty()) {
            for (Object[] row : reservationRepository.sumGroupedBySessionAndStatus(ids)) {
                counts.computeIfAbsent((Long) row[0], k -> new HashMap<>())
                        .put((ReservationStatus) row[1], (Long) row[2]);
            }
        }
        return PageResponse.of(sessions, s -> {
            Map<ReservationStatus, Long> c = counts.getOrDefault(s.getId(), Map.of());
            return SessionResponse.of(s, c.getOrDefault(ReservationStatus.CONFIRMED, 0L),
                    c.getOrDefault(ReservationStatus.WAITING, 0L));
        });
    }

    @Transactional
    public SessionResponse create(CreateRequest request, Long adminUserId) {
        SessionStatus initialStatus = request.initialStatus() == null ? SessionStatus.DRAFT : request.initialStatus();
        if (initialStatus != SessionStatus.DRAFT && initialStatus != SessionStatus.OPEN) {
            throw BusinessException.invalidField("initialStatus", "DRAFT 또는 OPEN 만 가능합니다.");
        }
        LocalDateTime startAt = SeoulTime.toLocal(request.startAt());
        LocalDateTime endAt = SeoulTime.toLocal(request.endAt());
        validateTimeRange(startAt, endAt);

        Program program = programRepository.findById(request.programId())
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        Instructor instructor = request.instructorId() == null ? null : findActiveInstructor(request.instructorId());

        ClassSession session = ClassSession.create(program, instructor, startAt, endAt, request.capacity(),
                request.location(), request.description(), initialStatus,
                userRepository.getReferenceById(adminUserId));
        return SessionResponse.of(sessionRepository.save(session), 0, 0);
    }

    @Transactional
    public SessionResponse update(Long sessionId, UpdateRequest request) {
        ClassSession session = findForUpdate(sessionId);
        long confirmed = countReservations(sessionId, ReservationStatus.CONFIRMED);
        long waiting = countReservations(sessionId, ReservationStatus.WAITING);

        LocalDateTime startAt = SeoulTime.toLocal(request.startAt());
        LocalDateTime endAt = SeoulTime.toLocal(request.endAt());
        validateTimeRange(startAt == null ? session.getStartAt() : startAt,
                endAt == null ? session.getEndAt() : endAt);

        boolean timeChanged = (startAt != null && !startAt.equals(session.getStartAt()))
                || (endAt != null && !endAt.equals(session.getEndAt()));
        if (timeChanged && confirmed + waiting > 0) {
            throw new BusinessException(ErrorCode.SESSION_HAS_ACTIVE_RESERVATIONS);
        }
        if (request.capacity() != null && request.capacity() < confirmed) {
            throw new BusinessException(ErrorCode.CAPACITY_BELOW_CONFIRMED_COUNT);
        }
        Instructor instructor = request.instructorId() == null
                || Objects.equals(request.instructorId(), instructorIdOf(session))
                ? null : findActiveInstructor(request.instructorId());

        session.update(instructor, startAt, endAt, request.capacity(), request.location());
        return SessionResponse.of(session, confirmed, waiting);
    }

    @Transactional
    public SessionResponse changeStatus(Long sessionId, SessionStatus target) {
        ClassSession session = findForUpdate(sessionId);
        long confirmed = countReservations(sessionId, ReservationStatus.CONFIRMED);
        long waiting = countReservations(sessionId, ReservationStatus.WAITING);

        if (!session.getSessionStatus().canChangeTo(target)) {
            throw new BusinessException(ErrorCode.INVALID_STATUS_TRANSITION);
        }
        if (target == SessionStatus.CANCELLED && confirmed + waiting > 0) {
            throw new BusinessException(ErrorCode.SESSION_HAS_ACTIVE_RESERVATIONS);
        }
        session.changeStatus(target);
        return SessionResponse.of(session, confirmed, waiting);
    }

    private ClassSession findForUpdate(Long sessionId) {
        return sessionRepository.findByIdForUpdate(sessionId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
    }

    private Instructor findActiveInstructor(Long instructorId) {
        Instructor instructor = instructorRepository.findById(instructorId)
                .orElseThrow(() -> new BusinessException(ErrorCode.RESOURCE_NOT_FOUND));
        if (!instructor.isActive()) {
            throw new BusinessException(ErrorCode.INSTRUCTOR_NOT_ACTIVE);
        }
        return instructor;
    }

    private static Long instructorIdOf(ClassSession session) {
        return session.getInstructor() == null ? null : session.getInstructor().getId();
    }

    /** BR-011 */
    private static void validateTimeRange(LocalDateTime startAt, LocalDateTime endAt) {
        if (!endAt.isAfter(startAt)) {
            throw BusinessException.invalidField("endAt", "종료 시각은 시작 시각보다 늦어야 합니다.");
        }
    }

    private long countReservations(Long sessionId, ReservationStatus status) {
        return reservationRepository.sumPartySize(sessionId, status);
    }
}
