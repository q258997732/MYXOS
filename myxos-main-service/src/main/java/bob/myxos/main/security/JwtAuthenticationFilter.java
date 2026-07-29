package bob.myxos.main.security;

import bob.myxos.domain.audit.LoginUserHolder;
import bob.myxos.domain.entity.LoginToken;
import bob.myxos.domain.mapper.LoginTokenMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.LocalDateTime;

/**
 * JWT 认证过滤器
 * 从 Authorization: Bearer 头解析 token，校验签名、过期时间及吊销状态后写入 SecurityContext
 * 同时将用户名写入 LoginUserHolder 供审计字段填充使用
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /** 请求属性键：当前登录用户 ID */
    public static final String ATTR_USER_ID = "MYXOS_LOGIN_USER_ID";

    /** 请求属性键：当前 JWT 的 jti */
    public static final String ATTR_TOKEN_ID = "MYXOS_TOKEN_ID";

    private final JwtTokenProvider jwtTokenProvider;
    private final UserDetailsService userDetailsService;
    private final LoginTokenMapper loginTokenMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try {
            String token = resolveToken(request);
            if (StringUtils.hasText(token) && jwtTokenProvider.validateToken(token)) {
                Claims claims = jwtTokenProvider.parseToken(token);
                String username = claims.get("username", String.class);
                String tokenId = claims.getId();

                // 校验令牌是否被吊销或过期
                if (!isTokenValid(tokenId)) {
                    log.warn("JWT 已吊销或无效：tokenId={}", tokenId);
                    filterChain.doFilter(request, response);
                    return;
                }

                // 写入审计上下文，供 MyBatis-Plus 自动填充 who_created / who_modified
                LoginUserHolder.set(username);

                // 加载用户详情并构造 Authentication，权限以数据库角色为准
                try {
                    UserDetails userDetails = userDetailsService.loadUserByUsername(username);
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
                    authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(authentication);

                    // 将 userId 与 tokenId 暂存到请求属性，便于 Controller 使用
                    request.setAttribute(ATTR_USER_ID, Long.valueOf(claims.getSubject()));
                    request.setAttribute(ATTR_TOKEN_ID, tokenId);
                } catch (UsernameNotFoundException e) {
                    log.warn("JWT 有效但用户不存在：{}", username);
                }
            }
            filterChain.doFilter(request, response);
        } finally {
            // 请求结束后清理 ThreadLocal，避免线程复用导致的数据污染
            LoginUserHolder.clear();
        }
    }

    /**
     * 校验 token 是否在数据库中且未被吊销、未过期
     *
     * @param tokenId JWT 的 jti
     * @return true 有效，false 已吊销或不存在
     */
    private boolean isTokenValid(String tokenId) {
        if (!StringUtils.hasText(tokenId)) {
            return false;
        }
        LoginToken loginToken = loginTokenMapper.selectOne(
                new LambdaQueryWrapper<LoginToken>()
                        .eq(LoginToken::getTokenId, tokenId)
                        .eq(LoginToken::getRevoked, 0)
                        .eq(LoginToken::getDeleted, 0));
        if (loginToken == null) {
            return false;
        }
        return loginToken.getExpiresAt() == null || loginToken.getExpiresAt().isAfter(LocalDateTime.now());
    }

    /**
     * 从请求头解析 Bearer Token
     *
     * @param request HTTP 请求
     * @return token 字符串，未携带时返回 null
     */
    private String resolveToken(HttpServletRequest request) {
        String bearer = request.getHeader("Authorization");
        if (StringUtils.hasText(bearer) && bearer.startsWith("Bearer ")) {
            return bearer.substring(7);
        }
        return null;
    }
}
