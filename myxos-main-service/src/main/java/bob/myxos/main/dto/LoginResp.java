package bob.myxos.main.dto;

import lombok.Data;

/**
 * 登录响应 DTO
 */
@Data
public class LoginResp {

    /** JWT 访问令牌 */
    private String token;

    /** 令牌有效期（秒） */
    private Long expiresIn;

    /** 登录用户名 */
    private String username;

    /** 用户角色 */
    private String role;
}
