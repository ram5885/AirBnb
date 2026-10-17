package com.HotelManagement.AirBnb;

import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestFlywayConfig.class)
class AuthApiIntegrationTest {

    private static final String INTERNAL = "test-internal-token-0123456789";

    @Autowired
    MockMvc mvc;

    @Autowired
    JwtDecoder jwtDecoder;

    private String signupBody(String email, String role) {
        return """
                {"name":"Asha Rao","email":"%s","password":"secret123","role":"%s"}""".formatted(email, role);
    }

    @Test
    void signupIssuesTokenWithUserIdEmailAndRole() throws Exception {
        String email = "guest-" + UUID.randomUUID() + "@test.com";
        String body = mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON)
                        .content(signupBody(email, "GUEST")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.roles[0]").value("GUEST"))
                .andExpect(jsonPath("$.message").value("Signup successful"))
                .andReturn().getResponse().getContentAsString();

        long id = ((Number) JsonPath.read(body, "$.id")).longValue();
        Jwt jwt = jwtDecoder.decode(JsonPath.read(body, "$.token"));
        assertThat(jwt.getSubject()).isEqualTo(String.valueOf(id));
        assertThat(jwt.getClaimAsString("email")).isEqualTo(email);
        assertThat(jwt.getClaimAsStringList("roles")).containsExactly("GUEST");
    }

    @Test
    void duplicateEmailIsConflict() throws Exception {
        String email = "dup-" + UUID.randomUUID() + "@test.com";
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signupBody(email, "GUEST")))
                .andExpect(status().isCreated());
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signupBody(email, "GUEST")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Email is already registered: " + email));
    }

    @Test
    void loginChecksBcryptPasswordWithoutSayingWhichPartIsWrong() throws Exception {
        String email = "mgr-" + UUID.randomUUID() + "@test.com";
        mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signupBody(email, "HOTEL_MANAGER")))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"secret123\"}".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.roles[0]").value("HOTEL_MANAGER"))
                .andExpect(jsonPath("$.token").isNotEmpty());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"%s\",\"password\":\"wrong\"}".formatted(email)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"nobody-%s@test.com\",\"password\":\"secret123\"}".formatted(UUID.randomUUID())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error").value("Invalid email or password"));
    }

    @Test
    void internalUserApiRequiresTheInternalTokenAndNeverReturnsThePassword() throws Exception {
        String email = "int-" + UUID.randomUUID() + "@test.com";
        String body = mvc.perform(post("/api/auth/signup").contentType(MediaType.APPLICATION_JSON).content(signupBody(email, "GUEST")))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.id")).longValue();

        mvc.perform(get("/internal/users/" + id)).andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/users/" + id).header("X-Internal-Token", "wrong-token-wrong-token"))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/internal/users/" + id).header("X-Internal-Token", INTERNAL))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Asha Rao"))
                .andExpect(jsonPath("$.email").value(email))
                .andExpect(jsonPath("$.password").doesNotExist());
        mvc.perform(get("/internal/users/987654321").header("X-Internal-Token", INTERNAL))
                .andExpect(status().isNotFound());
        mvc.perform(post("/internal/users/lookup").header("X-Internal-Token", INTERNAL)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"ids\":[%d, 987654321]}".formatted(id)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(id));
    }

    @Test
    void healthIsPublic() throws Exception {
        mvc.perform(get("/actuator/health")).andExpect(status().isOk());
    }
}
