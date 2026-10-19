package com.slotmate.session;

/** 회원 화면의 다음 행동 (API 명세 6.3 bookingAction) */
public enum BookingAction {
    BOOKABLE, WAITLISTABLE, ALREADY_CONFIRMED, ALREADY_WAITING, CLOSED, CANCELLED, COMPLETED, STARTED
}
