package com.slotmate.reservation;

import com.slotmate.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API 명세 7.1. 동시 요청 검증은 단계 5 */
class ReservationApiTest extends ApiTestSupport {

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ReservationHistoryRepository historyRepository;

    private String admin;
    private long programId;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        programId = idFrom(perform(post("/api/v1/admin/programs"), admin, Map.of(
                "name", "예약테스트 요가", "type", "YOGA", "defaultDurationMinutes", 50, "defaultCapacity", 10)),
                "programId");
    }

    @Test
    void 자리가_있으면_CONFIRMED_와_BOOKED_이력() throws Exception {
        long sessionId = session("2099-04-01T10:00:00+09:00", 2, "OPEN");

        ResultActions result = reserve(sessionId, memberToken())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").value(sessionId))
                .andExpect(jsonPath("$.reservationStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.waitingPosition").doesNotExist())
                .andExpect(jsonPath("$.message").value("예약이 확정되었습니다."));

        List<ReservationHistory> histories = historyRepository.findByReservationIdOrderByIdAsc(idFrom(result, "reservationId"));
        assertThat(histories).hasSize(1);
        assertThat(histories.getFirst().getEventType()).isEqualTo(ReservationEventType.BOOKED);
        assertThat(histories.getFirst().getFromStatus()).isNull();
        assertThat(histories.getFirst().getToStatus()).isEqualTo(ReservationStatus.CONFIRMED);
    }

    @Test
    void 정원이_차면_WAITING_과_대기_순번() throws Exception {
        long sessionId = session("2099-04-01T10:00:00+09:00", 1, "OPEN");
        reserve(sessionId, memberToken()).andExpect(jsonPath("$.reservationStatus").value("CONFIRMED"));

        reserve(sessionId, memberToken())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationStatus").value("WAITING"))
                .andExpect(jsonPath("$.waitingPosition").value(1))
                .andExpect(jsonPath("$.message").value("대기 신청이 완료되었습니다."));
        ResultActions second = reserve(sessionId, memberToken())
                .andExpect(jsonPath("$.waitingPosition").value(2));

        List<ReservationHistory> histories = historyRepository.findByReservationIdOrderByIdAsc(idFrom(second, "reservationId"));
        assertThat(histories.getFirst().getEventType()).isEqualTo(ReservationEventType.WAITLISTED);
    }

    @Test
    void 같은_회차_중복_신청_409() throws Exception {
        long sessionId = session("2099-04-01T10:00:00+09:00", 5, "OPEN");
        String member = memberToken();
        reserve(sessionId, member).andExpect(status().isCreated());

        reserve(sessionId, member)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("DUPLICATE_ACTIVE_RESERVATION"));
    }

    @Test
    void 취소한_예약_재신청은_같은_행_attemptNo_증가_REBOOKED() throws Exception {
        long sessionId = session("2099-04-01T10:00:00+09:00", 5, "OPEN");
        Member member = newMember();
        jdbcTemplate.update("INSERT INTO reservations (session_id, user_id, reservation_status, cancelled_at) "
                + "VALUES (?, ?, 'CANCELLED', NOW(6))", sessionId, member.userId());
        Long cancelledId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        reserve(sessionId, member.token())
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reservationId").value(cancelledId))
                .andExpect(jsonPath("$.reservationStatus").value("CONFIRMED"));

        assertThat(jdbcTemplate.queryForObject("SELECT attempt_no FROM reservations WHERE reservation_id = ?",
                Integer.class, cancelledId)).isEqualTo(2);
        ReservationHistory history = historyRepository.findByReservationIdOrderByIdAsc(cancelledId).getFirst();
        assertThat(history.getEventType()).isEqualTo(ReservationEventType.REBOOKED);
        assertThat(history.getFromStatus()).isEqualTo(ReservationStatus.CANCELLED);
        assertThat(history.getAttemptNo()).isEqualTo(2);
    }

    @Test
    void DRAFT_회차는_404() throws Exception {
        long sessionId = session("2099-04-01T10:00:00+09:00", 5, "DRAFT");

        reserve(sessionId, memberToken())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void CLOSED_회차는_409() throws Exception {
        long sessionId = session("2099-04-01T10:00:00+09:00", 5, "OPEN");
        perform(patch("/api/v1/admin/sessions/" + sessionId + "/status"), admin, Map.of("status", "CLOSED"));

        reserve(sessionId, memberToken())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_OPEN"));
    }

    @Test
    void 이미_시작한_회차는_409() throws Exception {
        long sessionId = session("2000-01-03T10:00:00+09:00", 5, "OPEN");

        reserve(sessionId, memberToken())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_NOT_OPEN"));
    }

    @Test
    void 토큰_없으면_401_ADMIN_은_403() throws Exception {
        long sessionId = session("2099-04-01T10:00:00+09:00", 5, "OPEN");

        mockMvc.perform(post("/api/v1/sessions/" + sessionId + "/reservations"))
                .andExpect(status().isUnauthorized());
        reserve(sessionId, admin)
                .andExpect(status().isForbidden());
    }

    private long session(String startAt, int capacity, String initialStatus) throws Exception {
        String endAt = startAt.replace("T10:00", "T10:50");
        return idFrom(perform(post("/api/v1/admin/sessions"), admin, Map.of(
                "programId", programId, "startAt", startAt, "endAt", endAt,
                "capacity", capacity, "initialStatus", initialStatus)), "sessionId");
    }

    private ResultActions reserve(long sessionId, String token) throws Exception {
        return perform(post("/api/v1/sessions/" + sessionId + "/reservations"), token, null);
    }
}
