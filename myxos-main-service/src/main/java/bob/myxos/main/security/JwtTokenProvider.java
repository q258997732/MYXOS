package bob.myxos.main.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.nio.charset.StandardCharsets;
import java.security.Key;
import java.util.Date;
import java.util.UUID;

/**
 * JWT 令牌提供者
 * 负责生成、解析与校验 JWT
 * 启动时校验 secret 不为空，避免使用空密钥签发令牌
 */
@Component
@RequiredArgsConstructor
public class JwtTokenProvider {

    private final JwtConfig jwtConfig;

    /** 签名密钥，由 init 方法根据 secret 构造 */
    private Key key;

    /** JWT 密钥最小字节数（HS256 要求至少 32 字节） */
    private static final int MIN_SECRET_BYTES = 32;

    /**
     * 初始化签名密钥
     * 若 secret 未配置或长度不足则抛出 IllegalStateException 阻止应用启动
     */
    @PostConstruct
    public void init() {
        if (jwtConfig.getSecret() == null || jwtConfig.getSecret().isEmpty()) {
            throw new IllegalStateException("环境变量 MYXOS_JWT_SECRET 不能为空");
        }
        byte[] secretBytes = jwtConfig.getSecret().getBytes(StandardCharsets.UTF_8);
        if (secretBytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException("MYXOS_JWT_SECRET 长度至少为 " + MIN_SECRET_BYTES + " 字节，建议 64 字节以上");
        }
        this.key = Keys.hmacShaKeyFor(secretBytes);
    }

    /**
     * 生成 JWT
     *
     * @param loginUser 登录用户
     * @return 签发的 JWT 字符串
     */
    public String generateToken(LoginUser loginUser) {
        Date now = new Date();
        Date expiry = new Date(now.getTime() + jwtConfig.getExpiration());
        return Jwts.builder()
                .setSubject(String.valueOf(loginUser.getUserId()))
                .claim("username", loginUser.getUsername())
                .claim("role", loginUser.getRole())
                .setId(UUID.randomUUID().toString())
                .setIssuedAt(now)
                .setExpiration(expiry)
                .signWith(key, SignatureAlgorithm.HS256)
                .compact();
    }

    /**
     * 解析 JWT 获取 Claims
     *
     * @param token JWT 字符串
     * @return Claims
     */
    public Claims parseToken(String token) {
        return Jwts.parserBuilder().setSigningKey(key).build()
                .parseClaimsJws(token).getBody();
    }

    /**
     * 校验 JWT 是否有效（签名正确且未过期）
     *
     * @param token JWT 字符串
     * @return true 有效，false 无效或已过期
     */
    public boolean validateToken(String token) {
        try {
            Claims claims = parseToken(token);
            return claims.getExpiration().after(new Date());
        } catch (Exception e) {
            return false;
        }
    }
}
