package com.slotmate.session;

import com.slotmate.program.ProgramCategory;
import com.slotmate.program.ProgramType;
import com.slotmate.reservation.ReservationStatus;

import java.time.OffsetDateTime;

/** API 명세 6.3·6.4 회원용 회차. my* 는 비로그인이면 null */
public final class PublicSessionDtos {

    private PublicSessionDtos() {
    }

    /** confirmedCount·waitingCount·availableSeats 는 인원(자리) 기준 (확장 설계 v0.1 3.1) */
    public record SessionItem(Long sessionId, Long programId, String programName, ProgramCategory category,
                              ProgramType programType, String instructorName, OffsetDateTime startAt,
                              OffsetDateTime endAt, int capacity, int maxPartySize, long confirmedCount,
                              long waitingCount, long availableSeats, SessionStatus sessionStatus,
                              BookingAction bookingAction, ReservationStatus myReservationStatus,
                              Integer myPartySize, Long myWaitingPosition) {
    }

    /** 6.4 — 목록 항목 + 수업 소개(프로그램 설명)·회차 설명·장소 */
    public record SessionDetail(Long sessionId, Long programId, String programName, ProgramCategory category,
                                ProgramType programType, String programDescription, String description,
                                String location, String instructorName, OffsetDateTime startAt, OffsetDateTime endAt,
                                int capacity, int maxPartySize, long confirmedCount, long waitingCount,
                                long availableSeats, SessionStatus sessionStatus, BookingAction bookingAction,
                                ReservationStatus myReservationStatus, Integer myPartySize, Long myWaitingPosition) {
        static SessionDetail of(ClassSession s, SessionItem item) {
            return new SessionDetail(item.sessionId(), item.programId(), item.programName(), item.category(),
                    item.programType(), s.getProgram().getDescription(), s.getDescription(), s.getLocation(),
                    item.instructorName(), item.startAt(), item.endAt(), item.capacity(), item.maxPartySize(),
                    item.confirmedCount(), item.waitingCount(), item.availableSeats(), item.sessionStatus(),
                    item.bookingAction(), item.myReservationStatus(), item.myPartySize(), item.myWaitingPosition());
        }
    }
}
