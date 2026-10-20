package com.slotmate.user;

import com.slotmate.support.ApiTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.MediaType;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 명세 13.8 — 관리자 회원 조회 · 강사 목록 */
class AdminMemberInstructorApiTest extends ApiTestSupport {

    @Autowired JdbcTemplate jdbcTemplate;

    @Test
    void 회원_검색은_이름_이메일_MEMBER_만_비밀번호_없음() throws Exception {
        String email = "findme-" + System.nanoTime() + "@example.com";
        mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                .content(json(Map.of("displayName", "찾을회원", "email", email, "password", "ExamplePassword123!"))))
                .andExpect(status().isCreated());

        perform(get("/api/v1/admin/members").param("keyword", email), adminToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.items[0].displayName").value("찾을회원"))
                .andExpect(jsonPath("$.items[0].accountStatus").value("ACTIVE"))
                .andExpect(jsonPath("$.items[0].createdAt").exists())
                .andExpect(jsonPath("$.items[0].passwordHash").doesNotExist());
        perform(get("/api/v1/admin/members").param("keyword", "admin@slotmate.local"), adminToken(), null)
                .andExpect(jsonPath("$.page.totalElements").value(0));
    }

    @Test
    void 회원_조회는_ADMIN_만() throws Exception {
        perform(get("/api/v1/admin/members"), memberToken(), null).andExpect(status().isForbidden());
    }

    @Test
    void 강사_목록은_비활성_포함() throws Exception {
        jdbcTemplate.update("INSERT INTO instructors (name, specialty, is_active) VALUES ('가테스트강사', '요가', 0)");

        perform(get("/api/v1/admin/instructors"), adminToken(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name == '가테스트강사')].active").value(false));
        perform(get("/api/v1/admin/instructors"), memberToken(), null).andExpect(status().isForbidden());
    }
}
