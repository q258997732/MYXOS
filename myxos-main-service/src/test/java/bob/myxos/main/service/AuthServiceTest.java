package bob.myxos.main.service;

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
import bob.myxos.main.service.impl.AuthServiceImpl;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AuthService 单元测试
 * 使用 Mockito 隔离数据库依赖，验证登录与登出业务逻辑
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService 测试")
class AuthServiceTest {

    private static final String TEST_SECRET = "myxos-test-secret-key-0123456789abcdef";

    @Mock
    private SysUserMapper sysUserMapper;

    @Mock
    private LoginTokenMapper loginTokenMapper;

    private PasswordEncoder passwordEncoder;

    private JwtTokenProvider jwtTokenProvider;

    private AuthServiceImpl authService;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder();
        JwtConfig jwtConfig = new JwtConfig();
        jwtConfig.setSecret(TEST_SECRET);
        jwtConfig.setExpiration(7200000L);
        jwtConfig.setRefreshExpiration(604800000L);
        jwtTokenProvider = new JwtTokenProvider(jwtConfig);
        jwtTokenProvider.init();
        authService = new AuthServiceImpl(sysUserMapper, loginTokenMapper, passwordEncoder, jwtTokenProvider, jwtConfig);
    }

    /** 构造一个启用状态的测试用户 */
    private SysUser buildActiveUser() {
        SysUser user = new SysUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword(passwordEncoder.encode("admin123"));
        user.setRole("ADMIN");
        user.setStatus(1);
        user.setDeleted(0);
        return user;
    }

    @Test
    @DisplayName("登录成功应返回 token、username、role，并写入 LoginToken 与更新 lastLoginAt")
    void loginSuccessReturnsTokenAndPersists() {
        // Arrange
        SysUser user = buildActiveUser();
        when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);

        LoginReq req = new LoginReq();
        req.setUsername("admin");
        req.setPassword("admin123");

        // Act
        LoginResp resp = authService.login(req);

        // Assert
        assertNotNull(resp);
        assertNotNull(resp.getToken());
        assertEquals("admin", resp.getUsername());
        assertEquals("ADMIN", resp.getRole());
        assertEquals(7200L, resp.getExpiresIn());

        // 验证写入了 LoginToken
        ArgumentCaptor<LoginToken> tokenCaptor = ArgumentCaptor.forClass(LoginToken.class);
        verify(loginTokenMapper).insert(tokenCaptor.capture());
        LoginToken saved = tokenCaptor.getValue();
        assertEquals(1L, saved.getUserId());
        assertNotNull(saved.getTokenId());
        assertNotNull(saved.getIssuedAt());
        assertNotNull(saved.getExpiresAt());
        assertTrue(saved.getExpiresAt().isAfter(saved.getIssuedAt()));

        // 验证更新了 lastLoginAt
        ArgumentCaptor<SysUser> userCaptor = ArgumentCaptor.forClass(SysUser.class);
        verify(sysUserMapper).updateById(userCaptor.capture());
        assertNotNull(userCaptor.getValue().getLastLoginAt());
    }

    @Test
    @DisplayName("用户不存在时登录应抛出 BizException")
    void loginFailsWhenUserNotFound() {
        // Arrange
        when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(null);
        LoginReq req = new LoginReq();
        req.setUsername("ghost");
        req.setPassword("any");

        // Act & Assert
        BizException ex = assertThrows(BizException.class, () -> authService.login(req));
        assertTrue(ex.getMessage().contains("用户名或密码错误"));
        verify(loginTokenMapper, never()).insert(any(LoginToken.class));
    }

    @Test
    @DisplayName("密码错误时登录应抛出 BizException")
    void loginFailsWhenPasswordWrong() {
        // Arrange
        SysUser user = buildActiveUser();
        when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        LoginReq req = new LoginReq();
        req.setUsername("admin");
        req.setPassword("wrong-password");

        // Act & Assert
        BizException ex = assertThrows(BizException.class, () -> authService.login(req));
        assertTrue(ex.getMessage().contains("用户名或密码错误"));
        verify(loginTokenMapper, never()).insert(any(LoginToken.class));
    }

    @Test
    @DisplayName("用户被禁用时登录应抛出 BizException")
    void loginFailsWhenUserDisabled() {
        // Arrange
        SysUser user = buildActiveUser();
        user.setStatus(0);
        when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        LoginReq req = new LoginReq();
        req.setUsername("admin");
        req.setPassword("admin123");

        // Act & Assert
        BizException ex = assertThrows(BizException.class, () -> authService.login(req));
        assertTrue(ex.getMessage().contains("用户名或密码错误"));
        verify(loginTokenMapper, never()).insert(any(LoginToken.class));
    }

    @Test
    @DisplayName("登出应吊销当前 JWT 令牌")
    void logoutRevokesCurrentToken() {
        // Arrange
        when(loginTokenMapper.update(any(LoginToken.class), any())).thenReturn(1);

        // Act
        authService.logout(1L, "test-token-id");

        // Assert
        ArgumentCaptor<LoginToken> captor = ArgumentCaptor.forClass(LoginToken.class);
        verify(loginTokenMapper).update(captor.capture(), any());
        assertEquals(1, captor.getValue().getRevoked());
    }

    @Test
    @DisplayName("生成的 token 应可被 JwtTokenProvider 解析并校验通过")
    void generatedTokenIsValid() {
        // Arrange
        SysUser user = buildActiveUser();
        when(sysUserMapper.selectOne(any(LambdaQueryWrapper.class))).thenReturn(user);
        LoginReq req = new LoginReq();
        req.setUsername("admin");
        req.setPassword("admin123");

        // Act
        LoginResp resp = authService.login(req);

        // Assert
        assertTrue(jwtTokenProvider.validateToken(resp.getToken()));
        LoginUser parsed = new LoginUser();
        parsed.setUserId(Long.valueOf(jwtTokenProvider.parseToken(resp.getToken()).getSubject()));
        assertEquals(1L, parsed.getUserId());
    }
}
