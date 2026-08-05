package bob.myxos.collector.internal;

import bob.myxos.common.api.Result;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Bean;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 采集服务内部接口集成测试
 * 验证内部令牌过滤器的放行与拒绝逻辑
 */
@WebMvcTest(InternalController.class)
@ContextConfiguration(classes = {InternalTokenFilter.class, InternalController.class, InternalControllerIntegrationTest.TestSecurityConfig.class})
@TestPropertySource(properties = "myxos.internal.token=test-internal-token")
class InternalControllerIntegrationTest {

    @TestConfiguration
    static class TestSecurityConfig {
        @Bean
        SecurityFilterChain testSecurityFilterChain(HttpSecurity http) throws Exception {
            return http.csrf().disable().authorizeRequests().anyRequest().permitAll().and().build();
        }
    }

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("携带正确内部令牌可访问健康检查接口")
    void healthWithValidTokenReturnsOk() throws Exception {
        mockMvc.perform(get("/internal/health")
                        .header("X-Internal-Token", "test-internal-token"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(Result.SUCCESS_CODE))
                .andExpect(jsonPath("$.data").value("collector-service is running"));
    }

    @Test
    @DisplayName("未携带内部令牌应返回 401")
    void healthWithoutTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/internal/health"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("携带错误内部令牌应返回 401")
    void healthWithWrongTokenReturnsUnauthorized() throws Exception {
        mockMvc.perform(get("/internal/health")
                        .header("X-Internal-Token", "wrong-token"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }
}
