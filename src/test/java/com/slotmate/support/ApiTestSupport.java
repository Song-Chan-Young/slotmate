package com.slotmate.support;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 로컬 MariaDB 사용. 각 테스트는 롤백. 관리자 계정은 .env 의 ADMIN_* */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
public abstract class ApiTestSupport {

    @Autowired protected MockMvc mockMvc;
    @Autowired protected JsonMapper jsonMapper;

    @Value("${slotmate.admin.email}") String adminEmail;
    @Value("${slotmate.admin.password}") String adminPassword;

    protected String adminToken() throws Exception {
        return accessToken(adminEmail, adminPassword);
    }

    protected record Member(long userId, String token) {
    }

    protected Member newMember() throws Exception {
        String email = "member-" + System.nanoTime() + "@example.com";
        String password = "ExamplePassword123!";
        long userId = idFrom(mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("displayName", "회원", "email", email, "password", password))))
                .andExpect(status().isCreated()), "userId");
        return new Member(userId, accessToken(email, password));
    }

    protected String memberToken() throws Exception {
        return newMember().token();
    }

    protected String accessToken(String email, String password) throws Exception {
        String body = mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content(json(Map.of("email", email, "password", password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body).get("accessToken").asString();
    }

    /** JSON 본문 + Bearer 토큰 */
    protected ResultActions perform(MockHttpServletRequestBuilder request, String token, Object body) throws Exception {
        request.header("Authorization", "Bearer " + token);
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json(body));
        }
        return mockMvc.perform(request);
    }

    protected String json(Object value) {
        return jsonMapper.writeValueAsString(value);
    }

    protected long idFrom(ResultActions result, String field) throws Exception {
        String body = result.andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(body).get(field).asLong();
    }
}
