package com.slotmate.session;

import com.slotmate.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API 명세 6.1~6.4 회원용 조회 */
class PublicQueryApiTest extends ApiTestSupport {

    private static final String DAY = "2099-05-04";
    private static final String PAST_DAY = "2000-01-03";

    @Autowired JdbcTemplate jdbcTemplate;

    private String admin;
    private long programId;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        programId = program("조회테스트 요가", "YOGA");
    }

    @Test
    void 날짜별_목록_DRAFT_제외_시작순_bookingAction() throws Exception {
        long full = session(DAY + "T09:00", 1, "OPEN", null);
        long open = session(DAY + "T10:00", 5, "OPEN", null);
        session(DAY + "T11:00", 5, "DRAFT", null);
        long closed = session(DAY + "T12:00", 5, "OPEN", null);
        changeStatus(closed, "CLOSED");
        long cancelled = session(DAY + "T13:00", 5, "OPEN", null);
        changeStatus(cancelled, "CANCELLED");
        reserve(full, memberToken());

        mockMvc.perform(get("/api/v1/sessions").param("date", DAY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(4))
                .andExpect(jsonPath("$.items[0].sessionId").value(full))
                .andExpect(jsonPath("$.items[0].bookingAction").value("WAITLISTABLE"))
                .andExpect(jsonPath("$.items[0].availableSeats").value(0))
                .andExpect(jsonPath("$.items[0].confirmedCount").value(1))
                .andExpect(jsonPath("$.items[0].myReservationStatus").doesNotExist())
                .andExpect(jsonPath("$.items[1].sessionId").value(open))
                .andExpect(jsonPath("$.items[1].bookingAction").value("BOOKABLE"))
                .andExpect(jsonPath("$.items[1].availableSeats").value(5))
                .andExpect(jsonPath("$.items[2].bookingAction").value("CLOSED"))
                .andExpect(jsonPath("$.items[3].bookingAction").value("CANCELLED"));
    }

    @Test
    void 지난_회차는_STARTED() throws Exception {
        session(PAST_DAY + "T10:00", 5, "OPEN", null);

        mockMvc.perform(get("/api/v1/sessions").param("date", PAST_DAY).param("keyword", "조회테스트"))
                .andExpect(jsonPath("$.items[0].bookingAction").value("STARTED"));
    }

    @Test
    void 로그인하면_본인_예약_상태와_대기_순번() throws Exception {
        long sessionId = session(DAY + "T09:00", 1, "OPEN", null);
        String first = memberToken();
        String second = memberToken();
        String third = memberToken();
        reserve(sessionId, first);
        reserve(sessionId, second);
        reserve(sessionId, third);

        perform(get("/api/v1/sessions").param("date", DAY), first, null)
                .andExpect(jsonPath("$.items[0].myReservationStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.items[0].bookingAction").value("ALREADY_CONFIRMED"))
                .andExpect(jsonPath("$.items[0].myWaitingPosition").doesNotExist());
        perform(get("/api/v1/sessions").param("date", DAY), third, null)
                .andExpect(jsonPath("$.items[0].myReservationStatus").value("WAITING"))
                .andExpect(jsonPath("$.items[0].bookingAction").value("ALREADY_WAITING"))
                .andExpect(jsonPath("$.items[0].myWaitingPosition").value(2))
                .andExpect(jsonPath("$.items[0].waitingCount").value(2));
    }

    @Test
    void keyword_는_프로그램명_또는_강사명() throws Exception {
        jdbcTemplate.update("INSERT INTO instructors (name, is_active) VALUES ('조회테스트강사', 1)");
        Long instructorId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
        long otherProgram = program("다른수업", "GX");
        long byInstructor = sessionOf(otherProgram, DAY + "T15:00", 5, "OPEN", instructorId);
        long byName = session(DAY + "T09:00", 5, "OPEN", null);
        sessionOf(otherProgram, DAY + "T16:00", 5, "OPEN", null);

        mockMvc.perform(get("/api/v1/sessions").param("date", DAY).param("keyword", "조회테스트"))
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].sessionId").value(byName))
                .andExpect(jsonPath("$.items[1].sessionId").value(byInstructor))
                .andExpect(jsonPath("$.items[1].instructorName").value("조회테스트강사"));
    }

    @Test
    void 날짜_누락_400() throws Exception {
        mockMvc.perform(get("/api/v1/sessions"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 수업_상세_와_DRAFT_404() throws Exception {
        long open = session(DAY + "T10:00", 5, "OPEN", null);
        long draft = session(DAY + "T11:00", 5, "DRAFT", null);

        mockMvc.perform(get("/api/v1/sessions/" + open))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.programDescription").value("프로그램 소개"))
                .andExpect(jsonPath("$.location").value("2층 GX룸"))
                .andExpect(jsonPath("$.bookingAction").value("BOOKABLE"));
        mockMvc.perform(get("/api/v1/sessions/" + draft))
                .andExpect(status().isNotFound());
    }

    @Test
    void 프로그램_목록은_활성만() throws Exception {
        long inactive = program("조회테스트 필라테스", "PILATES");
        perform(patch("/api/v1/admin/programs/" + inactive + "/active"), admin, Map.of("active", false));

        mockMvc.perform(get("/api/v1/programs").param("keyword", "조회테스트"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].programId").value(programId))
                .andExpect(jsonPath("$.items[0].active").doesNotExist());
        mockMvc.perform(get("/api/v1/programs/" + inactive))
                .andExpect(status().isNotFound());
    }

    @Test
    void 프로그램_상세는_공개_예정_회차만() throws Exception {
        session(PAST_DAY + "T10:00", 5, "OPEN", null);
        session(DAY + "T11:00", 5, "DRAFT", null);
        long upcoming = session(DAY + "T10:00", 5, "OPEN", null);
        long later = session("2099-05-20T10:00", 5, "OPEN", null);

        mockMvc.perform(get("/api/v1/programs/" + programId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("조회테스트 요가"))
                .andExpect(jsonPath("$.sessions.length()").value(2))
                .andExpect(jsonPath("$.sessions[0].sessionId").value(upcoming))
                .andExpect(jsonPath("$.sessions[1].sessionId").value(later));
        mockMvc.perform(get("/api/v1/programs/" + programId).param("from", DAY).param("to", DAY))
                .andExpect(jsonPath("$.sessions.length()").value(1));
    }

    private long program(String name, String type) throws Exception {
        return idFrom(perform(post("/api/v1/admin/programs"), admin, Map.of(
                "name", name, "type", type, "description", "프로그램 소개",
                "defaultDurationMinutes", 50, "defaultCapacity", 10)), "programId");
    }

    private long session(String startAt, int capacity, String initialStatus, Long instructorId) throws Exception {
        return sessionOf(programId, startAt, capacity, initialStatus, instructorId);
    }

    private long sessionOf(long program, String startAt, int capacity, String initialStatus, Long instructorId)
            throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("programId", program);
        body.put("startAt", startAt + ":00+09:00");
        body.put("endAt", startAt.substring(0, 11) + startAt.substring(11, 13) + ":50:00+09:00");
        body.put("capacity", capacity);
        body.put("location", "2층 GX룸");
        body.put("initialStatus", initialStatus);
        if (instructorId != null) {
            body.put("instructorId", instructorId);
        }
        return idFrom(perform(post("/api/v1/admin/sessions"), admin, body), "sessionId");
    }

    private void changeStatus(long sessionId, String status) throws Exception {
        perform(patch("/api/v1/admin/sessions/" + sessionId + "/status"), admin, Map.of("status", status))
                .andExpect(status().isOk());
    }

    private void reserve(long sessionId, String token) throws Exception {
        perform(post("/api/v1/sessions/" + sessionId + "/reservations"), token, null)
                .andExpect(status().isCreated());
    }
}
