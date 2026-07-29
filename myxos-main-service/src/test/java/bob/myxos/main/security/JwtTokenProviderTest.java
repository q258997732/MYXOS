package bob.myxos.main.security;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Date;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * JWT 令牌提供者单元测试
 * 覆盖：生成、解析、过期校验、异常路径
 */
@DisplayName("JwtTokenProvider 测试")
class JwtTokenProviderTest {

    /** 至少 32 字节的测试密钥（HS256 要求 >= 256 bit） */
    private static final String TEST_SECRET = "myxos-test-secret-key-0123456789abcdef";
    private static final Long TEST_EXPIRATION = 7200000L;

    private JwtConfig jwtConfig;
    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        jwtConfig = new JwtConfig();
        jwtConfig.setSecret(TEST_SECRET);
        jwtConfig.setExpiration(TEST_EXPIRATION);
        jwtConfig.setRefreshExpiration(604800000L);
        provider = new JwtTokenProvider(jwtConfig);
        provider.init();
    }

    @Test
    @DisplayName("secret 为空时 init 应抛出 IllegalStateException")
    void initThrowsWhenSecretEmpty() {
        // Arrange
        JwtConfig emptyConfig = new JwtConfig();
        emptyConfig.setSecret("");
        JwtTokenProvider p = new JwtTokenProvider(emptyConfig);

        // Act & Assert
        IllegalStateException ex = assertThrows(IllegalStateException.class, p::init);
        assertTrue(ex.getMessage().contains("MYXOS_JWT_SECRET"));
    }

    @Test
    @DisplayName("secret 长度不足 32 字节时 init 应抛出 IllegalStateException")
    void initThrowsWhenSecretTooShort() {
        // Arrange
        JwtConfig shortConfig = new JwtConfig();
        shortConfig.setSecret("short");
        JwtTokenProvider p = new JwtTokenProvider(shortConfig);

        // Act & Assert
        IllegalStateException ex = assertThrows(IllegalStateException.class, p::init);
        assertTrue(ex.getMessage().contains("长度至少为"));
    }

    @Test
    @DisplayName("secret 为 null 时 init 应抛出 IllegalStateException")
    void initThrowsWhenSecretNull() {
        // Arrange
        JwtConfig nullConfig = new JwtConfig();
        nullConfig.setSecret(null);
        JwtTokenProvider p = new JwtTokenProvider(nullConfig);

        // Act & Assert
        assertThrows(IllegalStateException.class, p::init);
    }

    @Test
    @DisplayName("生成令牌应包含 userId、username、role 与唯一 jti")
    void generateTokenContainsClaims() {
        // Arrange
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(1L);
        loginUser.setUsername("admin");
        loginUser.setRole("ADMIN");

        // Act
        String token = provider.generateToken(loginUser);

        // Assert
        assertNotNull(token);
        Claims claims = provider.parseToken(token);
        assertEquals("1", claims.getSubject());
        assertEquals("admin", claims.get("username", String.class));
        assertEquals("ADMIN", claims.get("role", String.class));
        assertNotNull(claims.getId());
        assertNotNull(claims.getIssuedAt());
        assertNotNull(claims.getExpiration());
    }

    @Test
    @DisplayName("两次生成令牌应产生不同的 jti")
    void generateTokenProducesUniqueJti() {
        // Arrange
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(1L);
        loginUser.setUsername("admin");
        loginUser.setRole("ADMIN");

        // Act
        String t1 = provider.generateToken(loginUser);
        String t2 = provider.generateToken(loginUser);

        // Assert
        String jti1 = provider.parseToken(t1).getId();
        String jti2 = provider.parseToken(t2).getId();
        assertNotNull(jti1);
        assertNotNull(jti2);
        assertFalse(jti1.equals(jti2));
    }

    @Test
    @DisplayName("过期时间应等于签发时间 + expiration")
    void expirationMatchesConfiguredValue() {
        // Arrange
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(2L);
        loginUser.setUsername("op");
        loginUser.setRole("OPERATOR");

        // Act
        String token = provider.generateToken(loginUser);
        Claims claims = provider.parseToken(token);

        // Assert
        long diff = claims.getExpiration().getTime() - claims.getIssuedAt().getTime();
        assertEquals(TEST_EXPIRATION, diff);
    }

    @Test
    @DisplayName("有效令牌 validateToken 应返回 true")
    void validateTokenReturnsTrueForValidToken() {
        // Arrange
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(1L);
        loginUser.setUsername("admin");
        loginUser.setRole("ADMIN");
        String token = provider.generateToken(loginUser);

        // Act & Assert
        assertTrue(provider.validateToken(token));
    }

    @Test
    @DisplayName("过期令牌 validateToken 应返回 false")
    void validateTokenReturnsFalseForExpiredToken() throws InterruptedException {
        // Arrange：构造一个 expiration 极短的 provider
        JwtConfig shortConfig = new JwtConfig();
        shortConfig.setSecret(TEST_SECRET);
        shortConfig.setExpiration(1L); // 1 毫秒
        JwtTokenProvider shortProvider = new JwtTokenProvider(shortConfig);
        shortProvider.init();

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(1L);
        loginUser.setUsername("admin");
        loginUser.setRole("ADMIN");
        String token = shortProvider.generateToken(loginUser);

        // Act：等待令牌过期
        Thread.sleep(50);

        // Assert
        assertFalse(shortProvider.validateToken(token));
    }

    @Test
    @DisplayName("非法令牌 validateToken 应返回 false")
    void validateTokenReturnsFalseForInvalidToken() {
        // Act & Assert
        assertFalse(provider.validateToken("not.a.valid.token"));
        assertFalse(provider.validateToken(""));
        assertFalse(provider.validateToken(null));
    }

    @Test
    @DisplayName("错误密钥签名的令牌 validateToken 应返回 false")
    void validateTokenReturnsFalseForWrongSignature() {
        // Arrange：用另一个 secret 生成 token
        JwtConfig otherConfig = new JwtConfig();
        otherConfig.setSecret("another-secret-key-0123456789abcdef-xyz");
        otherConfig.setExpiration(TEST_EXPIRATION);
        JwtTokenProvider otherProvider = new JwtTokenProvider(otherConfig);
        otherProvider.init();

        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(1L);
        loginUser.setUsername("admin");
        loginUser.setRole("ADMIN");
        String foreignToken = otherProvider.generateToken(loginUser);

        // Act & Assert：原 provider 校验应失败
        assertFalse(provider.validateToken(foreignToken));
    }

    @Test
    @DisplayName("解析有效令牌应返回 Claims，subject 为 userId 字符串")
    void parseTokenReturnsClaims() {
        // Arrange
        LoginUser loginUser = new LoginUser();
        loginUser.setUserId(99L);
        loginUser.setUsername("viewer");
        loginUser.setRole("VIEWER");
        String token = provider.generateToken(loginUser);

        // Act
        Claims claims = provider.parseToken(token);

        // Assert
        assertEquals("99", claims.getSubject());
        assertEquals("viewer", claims.get("username", String.class));
        assertEquals("VIEWER", claims.get("role", String.class));
        assertTrue(claims.getExpiration().after(new Date()));
    }
}
