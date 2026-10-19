package com.slotmate.session;

import com.slotmate.program.ProgramType;
import com.slotmate.reservation.ReservationStatus;

import java.time.OffsetDateTime;

/** API 명세 6.3·6.4 회원용 회차. my* 는 비로그인이면 null */
public final class PublicSessionDtos {

    private PublicSessionDtos() {
    }

    public record SessionItem(Long sessionId, Long programId, String programName, ProgramType programType,
                              String instructorName, OffsetDateTime startAt, OffsetDateTime endAt,
                              int capacity, long confirmedCount, long waitingCount, long availableSeats,
                              SessionStatus sessionStatus, BookingAction bookingAction,
                              ReservationStatus myReservationStatus, Long myWaitingPosition) {
    }

    /** 6.4 — 목록 항목 + 수업 소개(프로그램 설명)·회차 설명·장소 */
    public record SessionDetail(Long sessionId, Long programId, String programName, ProgramType programType,
                                String programDescription, String description, String location,
                                String instructorName, OffsetDateTime startAt, OffsetDateTime endAt,
                                int capacity, long confirmedCount, long waitingCount, long availableSeats,
                                SessionStatus sessionStatus, BookingAction bookingAction,
                                ReservationStatus myReservationStatus, Long myWaitingPosition) {
        static SessionDetail of(ClassSession s, SessionItem item) {
            return new SessionDetail(item.sessionId(), item.programId(), item.programName(), item.programType(),
                    s.getProgram().getDescription(), s.getDescription(), s.getLocation(),
                    item.instructorName(), item.startAt(), item.endAt(),
                    item.capacity(), item.confirmedCount(), item.waitingCount(), item.availableSeats(),
                    item.sessionStatus(), item.bookingAction(), item.myReservationStatus(), item.myWaitingPosition());
        }
    }
}
