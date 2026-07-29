package bob.myxos.main.security;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * JWT 配置属性类
 * 从 application.yml 的 myxos.jwt 节点读取配置
 * secret 必须通过环境变量 MYXOS_JWT_SECRET 注入，禁止硬编码
 */
@Data
@Component
@ConfigurationProperties(prefix = "myxos.jwt")
public class JwtConfig {

    /** JWT 签名密钥（HS256 要求至少 32 字节） */
    private String secret;

    /** 访问令牌有效期（毫秒），默认 2 小时 */
    private Long expiration = 7200000L;

    /** 刷新令牌有效期（毫秒），默认 7 天 */
    private Long refreshExpiration = 604800000L;
}
