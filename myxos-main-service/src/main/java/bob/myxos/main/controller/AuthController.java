package bob.myxos.main.controller;

import bob.myxos.common.api.Result;
import bob.myxos.main.dto.LoginReq;
import bob.myxos.main.dto.LoginResp;
import bob.myxos.main.security.JwtAuthenticationFilter;
import bob.myxos.main.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.util.HashMap;
import java.util.Map;

/**
 * 认证控制器
 * 提供登录、登出、当前用户信息查询接口
 */
@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    /**
     * 登录
     *
     * @param req 登录请求
     * @return 登录响应（包含 JWT）
     */
    @PostMapping("/login")
    public Result<LoginResp> login(@Valid @RequestBody LoginReq req) {
        return Result.ok(authService.login(req));
    }

    /**
     * 登出
     * 吊销当前 JWT 令牌
     *
     * @param request HTTP 请求（由 JwtAuthenticationFilter 写入用户 ID 与 tokenId 属性）
     * @return 空响应
     */
    @PostMapping("/logout")
    public Result<Void> logout(HttpServletRequest request) {
        Object userId = request.getAttribute(JwtAuthenticationFilter.ATTR_USER_ID);
        Object tokenId = request.getAttribute(JwtAuthenticationFilter.ATTR_TOKEN_ID);
        if (userId instanceof Long && tokenId instanceof String) {
            authService.logout((Long) userId, (String) tokenId);
        }
        return Result.ok();
    }

    /**
     * 获取当前登录用户信息
     *
     * @return 用户名与角色
     */
    @GetMapping("/me")
    public Result<Map<String, Object>> me() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        Map<String, Object> data = new HashMap<>(4);
        if (authentication != null && authentication.getPrincipal() instanceof UserDetails) {
            UserDetails userDetails = (UserDetails) authentication.getPrincipal();
            data.put("username", userDetails.getUsername());
            data.put("authorities", userDetails.getAuthorities());
        }
        return Result.ok(data);
    }
}
