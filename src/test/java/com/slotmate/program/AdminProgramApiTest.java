package com.slotmate.program;

import com.slotmate.support.ApiTestSupport;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.ResultActions;

import java.util.HashMap;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class AdminProgramApiTest extends ApiTestSupport {

    private static final String BASE = "/api/v1/admin/programs";

    private String admin;

    @BeforeEach
    void setUp() throws Exception {
        admin = adminToken();
    }

    @Test
    void 프로그램_등록_201_활성() throws Exception {
        create("기초 요가", "YOGA", 50, 10)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.programId").isNumber())
                .andExpect(jsonPath("$.name").value("기초 요가"))
                .andExpect(jsonPath("$.type").value("YOGA"))
                .andExpect(jsonPath("$.defaultDurationMinutes").value(50))
                .andExpect(jsonPath("$.defaultCapacity").value(10))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void 프로그램_등록_필수값_누락_400() throws Exception {
        perform(post(BASE), admin, Map.of("type", "YOGA", "defaultDurationMinutes", 50, "defaultCapacity", 10))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"));
    }

    @Test
    void 프로그램_목록_type_keyword_필터() throws Exception {
        create("필터테스트 요가", "YOGA", 50, 10);
        create("필터테스트 GX", "GX", 50, 20);

        perform(get(BASE).param("type", "YOGA").param("keyword", "필터테스트"), admin, null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items.length()").value(1))
                .andExpect(jsonPath("$.items[0].name").value("필터테스트 요가"))
                .andExpect(jsonPath("$.page.totalElements").value(1))
                .andExpect(jsonPath("$.page.size").value(20));
    }

    @Test
    void 프로그램_수정_보낸_항목만() throws Exception {
        long id = idFrom(create("기초 요가", "YOGA", 50, 10), "programId");

        perform(patch(BASE + "/" + id), admin, Map.of("defaultCapacity", 12))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("기초 요가"))
                .andExpect(jsonPath("$.defaultDurationMinutes").value(50))
                .andExpect(jsonPath("$.defaultCapacity").value(12));
    }

    @Test
    void 프로그램_비활성화() throws Exception {
        long id = idFrom(create("기초 요가", "YOGA", 50, 10), "programId");

        perform(patch(BASE + "/" + id + "/active"), admin, Map.of("active", false))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    @Test
    void 없는_프로그램_수정_404() throws Exception {
        perform(patch(BASE + "/999999999"), admin, Map.of("name", "x"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("RESOURCE_NOT_FOUND"));
    }

    @Test
    void MEMBER_는_403() throws Exception {
        perform(get(BASE), memberToken(), null)
                .andExpect(status().isForbidden());
    }

    private ResultActions create(String name, String type, int duration, int capacity) throws Exception {
        Map<String, Object> body = new HashMap<>();
        body.put("name", name);
        body.put("type", type);
        body.put("description", "설명");
        body.put("defaultDurationMinutes", duration);
        body.put("defaultCapacity", capacity);
        return perform(post(BASE), admin, body);
    }
}
