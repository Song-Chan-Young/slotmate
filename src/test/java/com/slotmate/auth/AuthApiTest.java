package com.slotmate.auth;

import com.slotmate.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** 로컬 MariaDB 사용. 각 테스트는 롤백 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AuthApiTest {

    private static final String EMAIL = "auth-test@example.com";
    private static final String PASSWORD = "ExamplePassword123!";

    @Autowired MockMvc mockMvc;
    @Autowired JsonMapper jsonMapper;
    @Autowired UserRepository userRepository;

    @Value("${slotmate.admin.email}") String adminEmail;
    @Value("${slotmate.admin.password}") String adminPassword;

    @Test
    void 회원가입_201_MEMBER_비밀번호는_해시로_저장() throws Exception {
        signup("김지민", EMAIL, PASSWORD)
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.userId").isNumber())
                .andExpect(jsonPath("$.displayName").value("김지민"))
                .andExpect(jsonPath("$.email").value(EMAIL))
                .andExpect(jsonPath("$.role").value("MEMBER"))
                .andExpect(jsonPath("$.password").doesNotExist());

        String hash = userRepository.findByEmail(EMAIL).orElseThrow().getPasswordHash();
        assertThat(hash).isNotEqualTo(PASSWORD).startsWith("{bcrypt}");
    }

    @Test
    void 회원가입_중복_이메일_409() throws Exception {
        signup("김지민", EMAIL, PASSWORD).andExpect(status().isCreated());

        signup("다른이름", EMAIL, PASSWORD)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void 회원가입_비밀번호_8자_미만_400() throws Exception {
        signup("김지민", EMAIL, "short7!")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_ERROR"))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
    }

    @Test
    void 회원가입_비밀번호_64자_초과_400() throws Exception {
        signup("김지민", EMAIL, "a".repeat(65))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("password"));
    }

    @Test
    void 로그인_성공_토큰_발급() throws Exception {
        signup("김지민", EMAIL, PASSWORD);

        login(EMAIL, PASSWORD)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isString())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(3600))
                .andExpect(jsonPath("$.user.displayName").value("김지민"))
                .andExpect(jsonPath("$.user.role").value("MEMBER"));
    }

    @Test
    void 로그인_비밀번호_틀림과_없는_계정은_같은_401() throws Exception {
        signup("김지민", EMAIL, PASSWORD);

        login(EMAIL, "WrongPassword123!")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
        login("nobody@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    // /me/** 는 컨트롤러가 아직 없어서, 통과하면 404 / 막히면 401 로 구분

    @Test
    void 토큰_없이_보호_경로_401() throws Exception {
        mockMvc.perform(get("/api/v1/me/reservations"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void 잘못된_토큰_401() throws Exception {
        mockMvc.perform(get("/api/v1/me/reservations").header("Authorization", "Bearer invalid.token.value"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
    }

    @Test
    void MEMBER_토큰_보호_경로_통과() throws Exception {
        signup("김지민", EMAIL, PASSWORD);
        String token = accessToken(EMAIL, PASSWORD);

        mockMvc.perform(get("/api/v1/me/reservations").header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    void MEMBER_토큰_admin_경로_403() throws Exception {
        signup("김지민", EMAIL, PASSWORD);
        String token = accessToken(EMAIL, PASSWORD);

        mockMvc.perform(get("/api/v1/admin/programs").header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("FORBIDDEN"));
    }

    @Test
    void ADMIN_토큰_admin_경로_통과() throws Exception {
        String token = accessToken(adminEmail, adminPassword);

        mockMvc.perform(get("/api/v1/admin/programs").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    private ResultActions signup(String displayName, String email, String password) throws Exception {
        String body = jsonMapper.writeValueAsString(new AuthDtos.SignupRequest(displayName, email, password));
        return mockMvc.perform(post("/api/v1/auth/signup").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private ResultActions login(String email, String password) throws Exception {
        String body = jsonMapper.writeValueAsString(new AuthDtos.LoginRequest(email, password));
        return mockMvc.perform(post("/api/v1/auth/login").contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private String accessToken(String email, String password) throws Exception {
        String json = login(email, password).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return jsonMapper.readTree(json).get("accessToken").asString();
    }
}
