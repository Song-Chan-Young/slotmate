package com.slotmate.notification;

import com.slotmate.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** API 명세 8절 — 예약·대기·취소·승격 때 생성되는 인앱 알림 */
class NotificationApiTest extends ApiTestSupport {

    private String admin;
    private long sessionId;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
        long programId = idFrom(perform(post("/api/v1/admin/programs"), admin, Map.of(
                "name", "알림테스트 요가", "type", "YOGA", "defaultDurationMinutes", 50, "defaultCapacity", 10)),
                "programId");
        sessionId = idFrom(perform(post("/api/v1/admin/sessions"), admin, Map.of(
                "programId", programId, "startAt", "2099-04-01T10:00:00+09:00", "endAt", "2099-04-01T10:50:00+09:00",
                "capacity", 1, "initialStatus", "OPEN")), "sessionId");
    }

    @Test
    void 확정_대기_취소_승격_알림이_최신순으로_쌓임() throws Exception {
        String owner = memberToken();
        String waiter = memberToken();
        long confirmedId = idFrom(reserve(owner), "reservationId");
        long waitingId = idFrom(reserve(waiter), "reservationId");
        perform(delete("/api/v1/reservations/" + confirmedId), owner, null).andExpect(status().isOk());

        perform(get("/api/v1/my/notifications"), owner, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].notificationType").value("CANCELLED"))
                .andExpect(jsonPath("$.items[0].title").value("예약이 취소되었습니다"))
                .andExpect(jsonPath("$.items[0].message").value("4월 1일 (수) 10:00 알림테스트 요가"))
                .andExpect(jsonPath("$.items[0].sessionId").value(sessionId))
                .andExpect(jsonPath("$.items[0].read").value(false))
                .andExpect(jsonPath("$.items[1].notificationType").value("BOOKING_CONFIRMED"));
        perform(get("/api/v1/my/notifications"), waiter, null)
                .andExpect(jsonPath("$.page.totalElements").value(2))
                .andExpect(jsonPath("$.items[0].notificationType").value("PROMOTED"))
                .andExpect(jsonPath("$.items[0].reservationId").value(waitingId))
                .andExpect(jsonPath("$.items[1].notificationType").value("WAITLISTED"))
                .andExpect(jsonPath("$.items[1].message").value("4월 1일 (수) 10:00 알림테스트 요가 · 현재 대기 1번째"));
    }

    @Test
    void 읽음_처리와_unreadOnly() throws Exception {
        String member = memberToken();
        reserve(member);
        String body = perform(get("/api/v1/my/notifications"), member, null).andReturn().getResponse().getContentAsString();
        long notificationId = jsonMapper.readTree(body).get("items").get(0).get("notificationId").asLong();

        perform(patch("/api/v1/my/notifications/" + notificationId + "/read"), member, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true))
                .andExpect(jsonPath("$.readAt").exists());
        perform(patch("/api/v1/my/notifications/" + notificationId + "/read"), member, null)
                .andExpect(status().isOk());
        perform(get("/api/v1/my/notifications").param("unreadOnly", "true"), member, null)
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void 남의_알림_읽음은_404_ADMIN_은_403() throws Exception {
        String member = memberToken();
        reserve(member);
        String body = perform(get("/api/v1/my/notifications"), member, null).andReturn().getResponse().getContentAsString();
        long notificationId = jsonMapper.readTree(body).get("items").get(0).get("notificationId").asLong();

        perform(patch("/api/v1/my/notifications/" + notificationId + "/read"), memberToken(), null)
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
        perform(get("/api/v1/my/notifications"), admin, null).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/v1/my/notifications")).andExpect(status().isUnauthorized());
    }

    private ResultActions reserve(String token) throws Exception {
        return perform(post("/api/v1/sessions/" + sessionId + "/reservations"), token, null)
                .andExpect(status().isCreated());
    }
}
