package com.slotmate.session;

import java.util.Set;

/** class_sessions.session_status (요구사항 6.1) */
public enum SessionStatus {
    DRAFT, OPEN, CLOSED, CANCELLED, COMPLETED;

    /** 관리자 상태 변경 API 가 허용하는 전이. CANCELLED·COMPLETED 는 끝 상태 */
    public boolean canChangeTo(SessionStatus target) {
        return switch (this) {
            case DRAFT -> Set.of(OPEN, CANCELLED).contains(target);
            case OPEN -> Set.of(CLOSED, CANCELLED).contains(target);
            case CLOSED -> Set.of(OPEN, CANCELLED).contains(target);
            case CANCELLED, COMPLETED -> false;
        };
    }
}
