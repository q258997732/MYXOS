package bob.myxos.main.service.impl;

import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.LoginToken;
import bob.myxos.domain.entity.SysUser;
import bob.myxos.domain.mapper.LoginTokenMapper;
import bob.myxos.domain.mapper.SysUserMapper;
import bob.myxos.main.dto.LoginReq;
import bob.myxos.main.dto.LoginResp;
import bob.myxos.main.security.JwtConfig;
import bob.myxos.main.security.JwtTokenProvider;
import bob.myxos.main.security.LoginUser;
import bob.myxos.main.service.AuthService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * 认证服务实现
 */
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    private final SysUserMapper sysUserMapper;
    private final LoginTokenMapper loginTokenMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final JwtConfig jwtConfig;

    /**
     * 登录：校验用户名密码、签发 JWT、记录 LoginToken、更新 lastLoginAt
     *
     * @param req 登录请求
     * @return 登录响应
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginResp login(LoginReq req) {
        // 查询用户（逻辑删除由 MyBatis-Plus 自动过滤）
        SysUser user = sysUserMapper.selectOne(
                new LambdaQueryWrapper<SysUser>()
                        .eq(SysUser::getUsername, req.getUsername()));

        // 用户不存在或密码错误均返回相同提示，避免用户名枚举
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            throw new BizException(401, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            throw new BizException(401, "用户名或密码错误");
        }

        // 签发 JWT
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(user.getId());
        loginUser.setUsername(user.getUsername());
        loginUser.setRole(user.getRole());
        String token = jwtTokenProvider.generateToken(loginUser);

        // 记录 LoginToken，jti 与 JWT 中的 jti 保持一致，便于按令牌吊销
        Claims claims = jwtTokenProvider.parseToken(token);
        LoginToken loginToken = new LoginToken();
        loginToken.setUserId(user.getId());
        loginToken.setTokenId(claims.getId());
        loginToken.setIssuedAt(LocalDateTime.ofInstant(claims.getIssuedAt().toInstant(), ZoneId.systemDefault()));
        loginToken.setExpiresAt(LocalDateTime.ofInstant(claims.getExpiration().toInstant(), ZoneId.systemDefault()));
        loginToken.setRevoked(0);
        loginTokenMapper.insert(loginToken);

        // 更新最近登录时间
        SysUser update = new SysUser();
        update.setId(user.getId());
        update.setLastLoginAt(LocalDateTime.now());
        sysUserMapper.updateById(update);

        // 构造响应
        LoginResp resp = new LoginResp();
        resp.setToken(token);
        resp.setExpiresIn(jwtConfig.getExpiration() / 1000L);
        resp.setUsername(user.getUsername());
        resp.setRole(user.getRole());
        return resp;
    }

    /**
     * 登出：吊销当前 JWT 令牌
     *
     * @param userId  用户 ID
     * @param tokenId 当前 JWT 的 jti
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void logout(Long userId, String tokenId) {
        if (userId == null || tokenId == null || tokenId.isEmpty()) {
            return;
        }
        LoginToken update = new LoginToken();
        update.setRevoked(1);
        loginTokenMapper.update(update,
                new LambdaUpdateWrapper<LoginToken>()
                        .eq(LoginToken::getUserId, userId)
                        .eq(LoginToken::getTokenId, tokenId)
                        .eq(LoginToken::getRevoked, 0));
    }
}
