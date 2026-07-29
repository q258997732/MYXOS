package bob.myxos.collector.internal;

import com.fasterxml.jackson.databind.ObjectMapper;
import bob.myxos.common.api.Result;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 内部令牌过滤器
 * 用于保护采集服务暴露给主服务的内部 REST 接口（路径以 /internal/ 开头）。
 * 校验请求头 X-Internal-Token 是否与环境变量 MYXOS_INTERNAL_TOKEN 注入的令牌一致，
 * 不一致时返回 401。
 *
 * 当前阶段内部接口尚未实现，本过滤器作为框架类先行注册，
 * 后续任务 11 补充 InternalController 后即可生效。
 */
@Slf4j
@Component
public class InternalTokenFilter extends OncePerRequestFilter {

    /** 内部接口路径前缀 */
    private static final String INTERNAL_PATH_PREFIX = "/internal/";

    /** 内部令牌请求头名称 */
    private static final String INTERNAL_TOKEN_HEADER = "X-Internal-Token";

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 通过配置注入的内部共享令牌 */
    @Value("${myxos.internal.token:}")
    private String internalToken;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        // 仅拦截内部接口路径，其余请求直接放行
        String path = request.getRequestURI();
        return path == null || !path.startsWith(INTERNAL_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        // 未配置内部令牌时拒绝所有内部接口访问，避免裸露
        if (!StringUtils.hasText(internalToken)) {
            log.warn("内部令牌未配置，拒绝访问：{}", request.getRequestURI());
            writeUnauthorized(response, "内部令牌未配置");
            return;
        }

        String token = request.getHeader(INTERNAL_TOKEN_HEADER);
        if (!internalToken.equals(token)) {
            log.warn("内部令牌校验失败：{}", request.getRequestURI());
            writeUnauthorized(response, "内部令牌无效");
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * 输出 401 响应
     *
     * @param response HTTP 响应
     * @param msg      错误信息
     * @throws IOException 写入响应失败时抛出
     */
    private void writeUnauthorized(HttpServletResponse response, String msg) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<Void> body = Result.fail(HttpStatus.UNAUTHORIZED.value(), msg);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
