package com.slotmate.common.time;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;

/** BR-012 — DB 에는 Asia/Seoul 기준 LocalDateTime, API 는 +09:00 오프셋 */
public final class SeoulTime {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

    private SeoulTime() {
    }

    /** DATETIME(6) 에 맞춰 마이크로초까지 */
    public static LocalDateTime now() {
        return LocalDateTime.now(ZONE).truncatedTo(ChronoUnit.MICROS);
    }

    public static LocalDateTime toLocal(OffsetDateTime value) {
        return value == null ? null : value.atZoneSameInstant(ZONE).toLocalDateTime();
    }

    public static OffsetDateTime toOffset(LocalDateTime value) {
        return value == null ? null : value.atZone(ZONE).toOffsetDateTime();
    }
}
