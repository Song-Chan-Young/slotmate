package com.slotmate.reservation;

import com.slotmate.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 확장 설계 v0.1 — 여러 명 예약(3절)·카테고리(2절) */
class PartyReservationApiTest extends ApiTestSupport {

    @Autowired JdbcTemplate jdbcTemplate;

    private String admin;
    private long programId;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        programId = idFrom(perform(post("/api/v1/admin/programs"), admin, Map.of(
                "name", "인원테스트 야경 투어", "type", "TOUR", "defaultDurationMinutes", 90,
                "defaultCapacity", 10, "maxPartySize", 6)), "programId");
    }

    @Test
    void 인원만큼_자리를_차지하고_잔여석도_인원_기준() throws Exception {
        long sessionId = session(5);

        reserve(sessionId, memberToken(), 3)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.partySize").value(3));

        perform(get("/api/v1/sessions/" + sessionId), memberToken(), null)
                .andExpect(jsonPath("$.category").value("ACTIVITY"))
                .andExpect(jsonPath("$.maxPartySize").value(6))
                .andExpect(jsonPath("$.confirmedCount").value(3))
                .andExpect(jsonPath("$.availableSeats").value(2));
    }

    @Test
    void 남은_자리보다_많으면_팀_전체가_대기하고_들어가는_팀은_확정() throws Exception {
        long sessionId = session(5);
        reserve(sessionId, memberToken(), 3).andExpect(jsonPath("$.reservationStatus").value("CONFIRMED"));

        reserve(sessionId, memberToken(), 3)
                .andExpect(jsonPath("$.reservationStatus").value("WAITING"))
                .andExpect(jsonPath("$.partySize").value(3))
                .andExpect(jsonPath("$.waitingPosition").value(1));
        reserve(sessionId, memberToken(), 2)
                .andExpect(jsonPath("$.reservationStatus").value("CONFIRMED"));
    }

    @Test
    void 인원은_1_이상_최대_인원_이하_정원_이하만_받음() throws Exception {
        long sessionId = session(4);
        String member = memberToken();

        for (int bad : new int[]{0, 5, 7}) { // 0 · 정원(4) 초과 · 최대 인원(6) 초과
            reserve(sessionId, member, bad)
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.code").value("INVALID_PARTY_SIZE"));
        }
        perform(post("/api/v1/sessions/" + sessionId + "/reservations"), member, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.partySize").value(1));
    }

    @Test
    void 운동_수업처럼_최대_인원이_1이면_2명_예약_불가() throws Exception {
        long yogaProgram = idFrom(perform(post("/api/v1/admin/programs"), admin, Map.of(
                "name", "인원테스트 요가", "type", "YOGA", "defaultDurationMinutes", 50, "defaultCapacity", 10)),
                "programId");
        long sessionId = session(yogaProgram, 10);

        reserve(sessionId, memberToken(), 2).andExpect(jsonPath("$.code").value("INVALID_PARTY_SIZE"));
    }

    /** 자리 4 · 대기 B 3명 → C 2명 → D 1명 → E 2명. A(4명) 취소 → B 승격(남은 1) → C 건너뜀 → D 승격(남은 0) */
    @Test
    void 취소하면_오래_기다린_순으로_들어가는_팀부터_승격() throws Exception {
        long sessionId = session(4);
        Member a = newMember();
        long aId = idFrom(reserve(sessionId, a.token(), 4), "reservationId");
        long b = idFrom(reserve(sessionId, memberToken(), 3), "reservationId");
        long c = idFrom(reserve(sessionId, memberToken(), 2), "reservationId");
        long d = idFrom(reserve(sessionId, memberToken(), 1), "reservationId");
        long e = idFrom(reserve(sessionId, memberToken(), 2), "reservationId");

        perform(delete("/api/v1/reservations/" + aId), a.token(), null).andExpect(status().isOk());

        perform(get("/api/v1/admin/sessions/" + sessionId + "/reservations?status=CONFIRMED"), admin, null)
                .andExpect(jsonPath("$.items[*].reservationId", contains((int) b, (int) d)));
        perform(get("/api/v1/admin/sessions/" + sessionId + "/reservations?status=WAITING"), admin, null)
                .andExpect(jsonPath("$.items[0].reservationId").value(c))
                .andExpect(jsonPath("$.items[0].waitingPosition").value(1))
                .andExpect(jsonPath("$.items[0].partySize").value(2))
                .andExpect(jsonPath("$.items[1].reservationId").value(e))
                .andExpect(jsonPath("$.items[1].waitingPosition").value(2));
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM reservation_histories WHERE event_type = 'PROMOTED' "
                + "AND reservation_id IN (?, ?, ?, ?)", Integer.class, b, c, d, e)).isEqualTo(2);
    }

    @Test
    void 카테고리로_수업_목록을_거름() throws Exception {
        long sessionId = session(5);

        perform(get("/api/v1/sessions?date=2099-06-01&category=ACTIVITY"), memberToken(), null)
                .andExpect(jsonPath("$.items[?(@.sessionId == " + sessionId + ")]").exists());
        perform(get("/api/v1/sessions?date=2099-06-01&category=CLASS"), memberToken(), null)
                .andExpect(jsonPath("$.items[?(@.sessionId == " + sessionId + ")]").doesNotExist());
        perform(get("/api/v1/programs?category=ACTIVITY&size=100"), memberToken(), null)
                .andExpect(jsonPath("$.items[*].programId", hasItem((int) programId)))
                .andExpect(jsonPath("$.items[*].category", everyItem(is("ACTIVITY"))));
    }

    @Test
    void 두_명_이상이면_알림에_인원이_붙음() throws Exception {
        long sessionId = session(5);
        Member member = newMember();
        reserve(sessionId, member.token(), 3);

        String message = jdbcTemplate.queryForObject("SELECT message FROM notifications WHERE user_id = ?",
                String.class, member.userId());
        assertThat(message).endsWith("인원테스트 야경 투어 · 3명");
    }

    private long session(int capacity) throws Exception {
        return session(programId, capacity);
    }

    private long session(long program, int capacity) throws Exception {
        return idFrom(perform(post("/api/v1/admin/sessions"), admin, Map.of(
                "programId", program, "startAt", "2099-06-01T19:00:00+09:00", "endAt", "2099-06-01T20:30:00+09:00",
                "capacity", capacity, "initialStatus", "OPEN")), "sessionId");
    }

    private ResultActions reserve(long sessionId, String token, int partySize) throws Exception {
        return perform(post("/api/v1/sessions/" + sessionId + "/reservations"), token, Map.of("partySize", partySize));
    }
}
