package bob.myxos.main.security;

import lombok.Data;

/**
 * 登录用户视图对象
 * 用于生成 JWT 与在 SecurityContext 中传递当前登录用户信息
 */
@Data
public class LoginUser {

    /** 用户 ID */
    private Long userId;

    /** 登录用户名 */
    private String username;

    /** 角色（ADMIN / OPERATOR / VIEWER） */
    private String role;
}
