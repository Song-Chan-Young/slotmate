package com.slotmate.reservation;

import com.slotmate.support.ApiTestSupport;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API 명세 7.2 내 예약 · 7.3 취소와 자동 승격 · 9.5 회차별 예약자. 동시 요청 검증은 단계 5 */
class ReservationListCancelApiTest extends ApiTestSupport {

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired ReservationHistoryRepository historyRepository;
    @Autowired EntityManager entityManager;

    private String admin;
    private long programId;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        programId = idFrom(perform(post("/api/v1/admin/programs"), admin, Map.of(
                "name", "취소테스트 요가", "type", "YOGA", "defaultDurationMinutes", 50, "defaultCapacity", 10)),
                "programId");
    }

    // ---- 7.2 ----

    @Test
    void 내_예약은_본인_것만_시작시각_내림차순_대기는_순번() throws Exception {
        long early = session("2099-04-01T10:00:00+09:00", 1, "OPEN");
        long late = session("2099-04-02T10:00:00+09:00", 5, "OPEN");
        Member me = newMember();
        reserve(early, memberToken());
        reserve(early, memberToken());
        reserve(early, me.token());
        reserve(late, me.token());

        perform(get("/api/v1/my/reservations"), me.token(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].sessionId").value(late))
                .andExpect(jsonPath("$.items[0].reservationStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.items[0].waitingPosition").doesNotExist())
                .andExpect(jsonPath("$.items[0].programName").value("취소테스트 요가"))
                .andExpect(jsonPath("$.items[1].sessionId").value(early))
                .andExpect(jsonPath("$.items[1].reservationStatus").value("WAITING"))
                .andExpect(jsonPath("$.items[1].waitingPosition").value(2));
    }

    @Test
    void 내_예약_status_from_to_필터() throws Exception {
        long first = session("2099-04-01T10:00:00+09:00", 1, "OPEN");
        long second = session("2099-04-03T10:00:00+09:00", 5, "OPEN");
        reserve(first, memberToken());
        String me = memberToken();
        reserve(first, me);
        reserve(second, me);

        perform(get("/api/v1/my/reservations").param("status", "WAITING"), me, null)
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].sessionId").value(first));
        perform(get("/api/v1/my/reservations").param("from", "2099-04-02").param("to", "2099-04-03"), me, null)
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].sessionId").value(second));
        perform(get("/api/v1/my/reservations").param("from", "2099-04-03").param("to", "2099-04-01"), me, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 내_예약_토큰_없으면_401_ADMIN_은_403() throws Exception {
        mockMvc.perform(get("/api/v1/my/reservations")).andExpect(status().isUnauthorized());
        perform(get("/api/v1/my/reservations"), admin, null).andExpect(status().isForbidden());
    }

    // ---- 7.3 ----

    @Test
    void 확정_취소하면_가장_오래_기다린_대기자_승격과_이력() throws Exception {
        long sessionId = session("2099-04-01T10:00:00+09:00", 1, "OPEN");
        String owner = memberToken();
        long confirmedId = idFrom(reserve(sessionId, owner), "reservationId");
        long firstWaiting = idFrom(reserve(sessionId, memberToken()), "reservationId");
        long secondWaiting = idFrom(reserve(sessionId, memberToken()), "reservationId");

        cancel(confirmedId, owner)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reservationId").value(confirmedId))
                .andExpect(jsonPath("$.reservationStatus").value("CANCELLED"))
                .andExpect(jsonPath("$.message").value("예약이 취소되었습니다."));

        assertThat(statusOf(firstWaiting)).isEqualTo("CONFIRMED");
        assertThat(statusOf(secondWaiting)).isEqualTo("WAITING");
        List<ReservationHistory> cancelled = historyRepository.findByReservationIdOrderByIdAsc(confirmedId);
        assertThat(cancelled.getLast().getEventType()).isEqualTo(ReservationEventType.CANCELLED);
        assertThat(cancelled.getLast().getFromStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        ReservationHistory promoted = historyRepository.findByReservationIdOrderByIdAsc(firstWaiting).getLast();
        assertThat(promoted.getEventType()).isEqualTo(ReservationEventType.PROMOTED);
        assertThat(promoted.getFromStatus()).isEqualTo(ReservationStatus.WAITING);
        assertThat(promoted.getToStatus()).isEqualTo(ReservationStatus.CONFIRMED);
        assertThat(promoted.getActor()).isNull();
    }

    @Test
    void 대기_취소는_그_항목만_취소() throws Exception {
        long sessionId = session("2099-04-01T10:00:00+09:00", 1, "OPEN");
        long confirmedId = idFrom(reserve(sessionId, memberToken()), "reservationId");
        String waiter = memberToken();
        long waitingId = idFrom(reserve(sessionId, waiter), "reservationId");
        long nextWaiting = idFrom(reserve(sessionId, memberToken()), "reservationId");

        cancel(waitingId, waiter)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("대기가 취소되었습니다."));

        assertThat(statusOf(confirmedId)).isEqualTo("CONFIRMED");
        assertThat(statusOf(nextWaiting)).isEqualTo("WAITING");
    }

    @Test
    void OPEN_이_아닌_회차는_확정_취소해도_승격_안_함() throws Exception {
        long sessionId = session("2099-04-01T10:00:00+09:00", 1, "OPEN");
        String owner = memberToken();
        long confirmedId = idFrom(reserve(sessionId, owner), "reservationId");
        long waitingId = idFrom(reserve(sessionId, memberToken()), "reservationId");
        perform(patch("/api/v1/admin/sessions/" + sessionId + "/status"), admin, Map.of("status", "CLOSED"));

        cancel(confirmedId, owner).andExpect(status().isOk());

        assertThat(statusOf(waitingId)).isEqualTo("WAITING");
    }

    @Test
    void 다른_회원_예약은_403_없는_예약은_404() throws Exception {
        long sessionId = session("2099-04-01T10:00:00+09:00", 5, "OPEN");
        long reservationId = idFrom(reserve(sessionId, memberToken()), "reservationId");

        cancel(reservationId, memberToken())
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
        cancel(Long.MAX_VALUE, memberToken())
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void 이미_취소한_예약과_시작한_회차는_409() throws Exception {
        long sessionId = session("2099-04-01T10:00:00+09:00", 5, "OPEN");
        String owner = memberToken();
        long reservationId = idFrom(reserve(sessionId, owner), "reservationId");
        cancel(reservationId, owner).andExpect(status().isOk());

        cancel(reservationId, owner)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_CANCELLABLE"));

        long pastSession = session("2000-01-03T10:00:00+09:00", 5, "OPEN");
        Member member = newMember();
        jdbcTemplate.update("INSERT INTO reservations (session_id, user_id, reservation_status, confirmed_at) "
                + "VALUES (?, ?, 'CONFIRMED', NOW(6))", pastSession, member.userId());
        Long pastId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        cancel(pastId, member.token())
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESERVATION_NOT_CANCELLABLE"));
    }

    @Test
    void 취소_토큰_없으면_401() throws Exception {
        mockMvc.perform(delete("/api/v1/reservations/1")).andExpect(status().isUnauthorized());
    }

    // ---- 9.5 ----

    @Test
    void 회차별_예약자는_신청순_대기는_순번_status_필터() throws Exception {
        long sessionId = session("2099-04-01T10:00:00+09:00", 1, "OPEN");
        long confirmedId = idFrom(reserve(sessionId, memberToken()), "reservationId");
        long waitingId = idFrom(reserve(sessionId, memberToken()), "reservationId");

        perform(get("/api/v1/admin/sessions/" + sessionId + "/reservations"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].reservationId").value(confirmedId))
                .andExpect(jsonPath("$.items[0].memberDisplayName").value("회원"))
                .andExpect(jsonPath("$.items[0].memberEmail").exists())
                .andExpect(jsonPath("$.items[0].waitingPosition").doesNotExist())
                .andExpect(jsonPath("$.items[1].reservationId").value(waitingId))
                .andExpect(jsonPath("$.items[1].waitingPosition").value(1))
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
        perform(get("/api/v1/admin/sessions/" + sessionId + "/reservations").param("status", "WAITING"), admin, null)
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].reservationId").value(waitingId));
    }

    @Test
    void 회차별_예약자_없는_회차_404_회원은_403() throws Exception {
        perform(get("/api/v1/admin/sessions/" + Long.MAX_VALUE + "/reservations"), admin, null)
                .andExpect(status().isNotFound());
        perform(get("/api/v1/admin/sessions/1/reservations"), memberToken(), null)
                .andExpect(status().isForbidden());
    }

    private long session(String startAt, int capacity, String initialStatus) throws Exception {
        String endAt = startAt.replace("T10:00", "T10:50");
        return idFrom(perform(post("/api/v1/admin/sessions"), admin, Map.of(
                "programId", programId, "startAt", startAt, "endAt", endAt,
                "capacity", capacity, "initialStatus", initialStatus)), "sessionId");
    }

    private ResultActions reserve(long sessionId, String token) throws Exception {
        return perform(post("/api/v1/sessions/" + sessionId + "/reservations"), token, null)
                .andExpect(status().isCreated());
    }

    private ResultActions cancel(long reservationId, String token) throws Exception {
        return perform(delete("/api/v1/reservations/" + reservationId), token, null);
    }

    /** 테스트 트랜잭션 안이라 JDBC 로 읽기 전에 flush */
    private String statusOf(long reservationId) {
        entityManager.flush();
        return jdbcTemplate.queryForObject("SELECT reservation_status FROM reservations WHERE reservation_id = ?",
                String.class, reservationId);
    }
}
