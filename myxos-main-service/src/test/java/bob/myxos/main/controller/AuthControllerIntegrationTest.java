package bob.myxos.main.controller;
import bob.myxos.main.config.SecurityConfig;
import bob.myxos.main.config.TestSecurityConfig;

import bob.myxos.common.api.Result;
import bob.myxos.main.dto.LoginReq;
import bob.myxos.main.dto.LoginResp;
import bob.myxos.main.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证控制器集成测试
 * 验证登录接口、参数校验与 JWT 过滤器链路
 */
@WebMvcTest(AuthController.class)
@ContextConfiguration(classes = {SecurityConfig.class, TestSecurityConfig.class, AuthController.class})
class AuthControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;


    @MockBean
    private AuthService authService;

    @Test
    @DisplayName("登录成功应返回 token 与用户信息")
    void loginSuccessReturnsToken() throws Exception {
        // Arrange
        LoginResp resp = new LoginResp();
        resp.setToken("test-jwt-token");
        resp.setExpiresIn(7200L);
        resp.setUsername("admin");
        resp.setRole("ADMIN");
        when(authService.login(any(LoginReq.class))).thenReturn(resp);

        LoginReq req = new LoginReq();
        req.setUsername("admin");
        req.setPassword("admin123");

        // Act & Assert
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE))
                .andExpect(jsonPath("$.data.token").value("test-jwt-token"))
                .andExpect(jsonPath("$.data.username").value("admin"))
                .andExpect(jsonPath("$.data.role").value("ADMIN"));
    }

    @Test
    @DisplayName("登录请求缺少用户名或密码时应返回 400")
    void loginWithEmptyBodyReturnsBadRequest() throws Exception {
        // Arrange
        LoginReq req = new LoginReq();
        req.setUsername("");
        req.setPassword("");

        // Act & Assert
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("未认证访问 /api/auth/me 应返回 401")
    void meWithoutAuthReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "admin", roles = "ADMIN")
    @DisplayName("已认证用户访问 /api/auth/me 应返回用户名")
    void meWithAuthReturnsUserInfo() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE))
                .andExpect(jsonPath("$.data.username").value("admin"));
    }
}
