package com.slotmate.session;

import com.slotmate.support.ApiTestSupport;
import com.slotmate.user.Role;
import com.slotmate.user.User;
import com.slotmate.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 예약·강사 API 가 아직 없어서 해당 행은 JdbcTemplate 로 직접 넣음 */
class AdminSessionApiTest extends ApiTestSupport {

    private static final String BASE = "/api/v1/admin/sessions";
    // 다른 데이터와 겹치지 않게 먼 미래 날짜 사용
    private static final String DAY = "2099-03-02";

    @Autowired JdbcTemplate jdbcTemplate;
    @Autowired UserRepository userRepository;

    private String admin;
    private long programId;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        programId = idFrom(perform(post("/api/v1/admin/programs"), admin, Map.of(
                "name", "회차테스트 요가", "type", "YOGA", "defaultDurationMinutes", 50, "defaultCapacity", 10)),
                "programId");
    }

    @Test
    void 회차_생성_기본_DRAFT_시간은_0900_오프셋() throws Exception {
        create("10:00", "10:50", 10, null)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.sessionId").isNumber())
                .andExpect(jsonPath("$.programName").value("회차테스트 요가"))
                .andExpect(jsonPath("$.sessionStatus").value("DRAFT"))
                .andExpect(jsonPath("$.startAt").value(DAY + "T10:00:00+09:00"))
                .andExpect(jsonPath("$.confirmedCount").value(0));
    }

    @Test
    void 회차_생성_UTC_로_보내도_서울_기준으로_저장() throws Exception {
        Map<String, Object> body = body("10:00", "10:50", 10, null);
        body.put("startAt", "2099-03-02T01:00:00Z");
        body.put("endAt", "2099-03-02T01:50:00Z");
        perform(post(BASE), admin, body)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.startAt").value(DAY + "T10:00:00+09:00"));
    }

    @Test
    void 회차_생성_종료가_시작보다_빠르면_400() throws Exception {
        create("10:00", "09:00", 10, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("endAt"));
    }

    @Test
    void 회차_생성_정원_0_이면_400() throws Exception {
        create("10:00", "10:50", 0, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("capacity"));
    }

    @Test
    void 회차_생성_initialStatus_CLOSED_400() throws Exception {
        create("10:00", "10:50", 10, "CLOSED")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("initialStatus"));
    }

    @Test
    void 회차_생성_비활성_강사_409() throws Exception {
        jdbcTemplate.update("INSERT INTO instructors (name, is_active) VALUES ('퇴사 강사', 0)");
        Long instructorId = jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);

        Map<String, Object> body = body("10:00", "10:50", 10, null);
        body.put("instructorId", instructorId);
        perform(post(BASE), admin, body)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INSTRUCTOR_NOT_ACTIVE"));
    }

    @Test
    void 시간표_기간_조회_시작_오름차순_예약_인원_포함() throws Exception {
        long late = idFrom(create("15:00", "15:50", 10, "OPEN"), "sessionId");
        long early = idFrom(create("09:00", "09:50", 2, "OPEN"), "sessionId");
        insertReservation(early, "CONFIRMED");
        insertReservation(early, "CONFIRMED");
        insertReservation(early, "WAITING");

        perform(get(BASE).param("from", DAY).param("to", DAY), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(2))
                .andExpect(jsonPath("$.items[0].sessionId").value(early))
                .andExpect(jsonPath("$.items[0].confirmedCount").value(2))
                .andExpect(jsonPath("$.items[0].waitingCount").value(1))
                .andExpect(jsonPath("$.items[1].sessionId").value(late))
                .andExpect(jsonPath("$.items[1].confirmedCount").value(0));
    }

    @Test
    void 시간표_조회_from_누락_400() throws Exception {
        perform(get(BASE).param("to", DAY), admin, null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"));
    }

    @Test
    void 정원을_확정_인원보다_작게_409() throws Exception {
        long id = idFrom(create("10:00", "10:50", 5, "OPEN"), "sessionId");
        insertReservation(id, "CONFIRMED");
        insertReservation(id, "CONFIRMED");

        perform(patch(BASE + "/" + id), admin, Map.of("capacity", 1))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CAPACITY_BELOW_CONFIRMED_COUNT"));
        perform(patch(BASE + "/" + id), admin, Map.of("capacity", 2))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.capacity").value(2));
    }

    @Test
    void 예약_있는_회차_시간_변경_409_없으면_변경() throws Exception {
        long withReservation = idFrom(create("10:00", "10:50", 5, "OPEN"), "sessionId");
        insertReservation(withReservation, "WAITING");
        long empty = idFrom(create("11:00", "11:50", 5, "OPEN"), "sessionId");

        perform(patch(BASE + "/" + withReservation), admin, Map.of("startAt", DAY + "T10:10:00+09:00"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_HAS_ACTIVE_RESERVATIONS"));
        perform(patch(BASE + "/" + empty), admin, Map.of("startAt", DAY + "T11:10:00+09:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.startAt").value(DAY + "T11:10:00+09:00"));
    }

    @Test
    void 수정_후_종료가_시작보다_빠르면_400() throws Exception {
        long id = idFrom(create("10:00", "10:50", 5, null), "sessionId");

        perform(patch(BASE + "/" + id), admin, Map.of("startAt", DAY + "T11:00:00+09:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("endAt"));
    }

    @Test
    void 상태_전이_허용() throws Exception {
        long id = idFrom(create("10:00", "10:50", 5, null), "sessionId");

        changeStatus(id, "OPEN").andExpect(status().isOk()).andExpect(jsonPath("$.sessionStatus").value("OPEN"));
        changeStatus(id, "CLOSED").andExpect(status().isOk());
        changeStatus(id, "OPEN").andExpect(status().isOk());
        changeStatus(id, "CANCELLED").andExpect(status().isOk());
    }

    @Test
    void 상태_전이_불가_409() throws Exception {
        long id = idFrom(create("10:00", "10:50", 5, null), "sessionId");

        changeStatus(id, "CLOSED").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATUS_TRANSITION"));
        changeStatus(id, "COMPLETED").andExpect(status().isConflict());
        changeStatus(id, "CANCELLED").andExpect(status().isOk());
        changeStatus(id, "OPEN").andExpect(status().isConflict());
    }

    @Test
    void 예약_있는_회차_취소_409() throws Exception {
        long id = idFrom(create("10:00", "10:50", 5, "OPEN"), "sessionId");
        insertReservation(id, "CONFIRMED");

        changeStatus(id, "CANCELLED").andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("SESSION_HAS_ACTIVE_RESERVATIONS"));
    }

    @Test
    void 취소된_예약만_있으면_회차_취소_가능() throws Exception {
        long id = idFrom(create("10:00", "10:50", 5, "OPEN"), "sessionId");
        insertReservation(id, "CANCELLED");

        changeStatus(id, "CANCELLED").andExpect(status().isOk());
    }

    @Test
    void 없는_회차_404() throws Exception {
        changeStatus(999999999L, "OPEN").andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    private ResultActions create(String start, String end, int capacity, String initialStatus) throws Exception {
        return perform(post(BASE), admin, body(start, end, capacity, initialStatus));
    }

    private Map<String, Object> body(String start, String end, int capacity, String initialStatus) {
        Map<String, Object> body = new HashMap<>();
        body.put("programId", programId);
        body.put("startAt", DAY + "T" + start + ":00+09:00");
        body.put("endAt", DAY + "T" + end + ":00+09:00");
        body.put("capacity", capacity);
        body.put("location", "2층 GX룸");
        if (initialStatus != null) {
            body.put("initialStatus", initialStatus);
        }
        return body;
    }

    private ResultActions changeStatus(long sessionId, String status) throws Exception {
        return perform(patch(BASE + "/" + sessionId + "/status"), admin, Map.of("status", status));
    }

    private void insertReservation(long sessionId, String status) {
        User member = userRepository.save(User.create("resv-" + System.nanoTime() + "@example.com",
                "{noop}unused", "예약회원", Role.MEMBER));
        jdbcTemplate.update("INSERT INTO reservations (session_id, user_id, reservation_status) VALUES (?, ?, ?)",
                sessionId, member.getId(), status);
    }
}
