package com.slotmate.reservation;

import com.slotmate.common.time.SeoulTime;
import com.slotmate.instructor.Instructor;
import com.slotmate.program.ProgramType;
import com.slotmate.session.ClassSession;
import com.slotmate.session.SessionStatus;
import com.slotmate.user.User;

import java.time.OffsetDateTime;

/** API 명세 7.1~7.3 · 9.5 */
public final class ReservationDtos {

    private ReservationDtos() {
    }

    public record ReserveResponse(Long reservationId, Long sessionId, ReservationStatus reservationStatus,
                                  Long waitingPosition, String message) {
    }

    /** 7.2 — waitingPosition 은 WAITING 일 때만 */
    public record MyReservationItem(Long reservationId, Long sessionId, String programName, ProgramType programType,
                                    String instructorName, OffsetDateTime startAt, OffsetDateTime endAt,
                                    String location, SessionStatus sessionStatus,
                                    ReservationStatus reservationStatus, Long waitingPosition,
                                    OffsetDateTime requestedAt, OffsetDateTime confirmedAt,
                                    OffsetDateTime cancelledAt) {
        static MyReservationItem of(Reservation r, Long waitingPosition) {
            ClassSession s = r.getSession();
            Instructor instructor = s.getInstructor();
            return new MyReservationItem(r.getId(), s.getId(), s.getProgram().getName(), s.getProgram().getProgramType(),
                    instructor == null ? null : instructor.getName(),
                    SeoulTime.toOffset(s.getStartAt()), SeoulTime.toOffset(s.getEndAt()),
                    s.getLocation(), s.getSessionStatus(), r.getReservationStatus(), waitingPosition,
                    SeoulTime.toOffset(r.getRequestedAt()), SeoulTime.toOffset(r.getConfirmedAt()),
                    SeoulTime.toOffset(r.getCancelledAt()));
        }
    }

    public record CancelResponse(Long reservationId, ReservationStatus reservationStatus, String message) {
    }

    /** 9.5 — 회원 최소 정보만. confirmedAt 이 requestedAt 보다 늦으면 대기에서 승격된 것 */
    public record SessionReservationItem(Long reservationId, String memberDisplayName, String memberEmail,
                                         ReservationStatus reservationStatus, Long waitingPosition,
                                         OffsetDateTime requestedAt, OffsetDateTime confirmedAt,
                                         OffsetDateTime cancelledAt) {
        static SessionReservationItem of(Reservation r, Long waitingPosition) {
            User user = r.getUser();
            return new SessionReservationItem(r.getId(), user.getDisplayName(), user.getEmail(),
                    r.getReservationStatus(), waitingPosition, SeoulTime.toOffset(r.getRequestedAt()),
                    SeoulTime.toOffset(r.getConfirmedAt()), SeoulTime.toOffset(r.getCancelledAt()));
        }
    }
}
