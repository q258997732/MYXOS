package bob.myxos.main.config;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;

/**
 * 测试专用安全配置
 * 提供一个无操作的 OncePerRequestFilter，避免在 @WebMvcTest 中因 Mockito 模拟
 * OncePerRequestFilter 导致 filter name 为空而抛出 "Attribute name must not be null"。
 *
 * <strong>TEST-ONLY</strong>：本配置仅位于 src/test，切勿移动到 src/main 或添加 @Configuration。
 */
@TestConfiguration
public class TestSecurityConfig {

    /**
     * 测试用过滤器，直接放行请求。
     * 认证信息由 Spring Security Test 的 @WithMockUser 提供。
     *
     * @return 无操作 OncePerRequestFilter
     */
    @Bean
    public OncePerRequestFilter jwtAuthenticationFilter() {
        return new OncePerRequestFilter() {
            @Override
            protected void doFilterInternal(HttpServletRequest request,
                                            HttpServletResponse response,
                                            FilterChain filterChain) throws ServletException, IOException {
                filterChain.doFilter(request, response);
            }
        };
    }
}
