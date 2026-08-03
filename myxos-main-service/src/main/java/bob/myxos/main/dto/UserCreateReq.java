package bob.myxos.main.dto;

import lombok.Data;

/**
 * 用户创建请求
 */
@Data
public class UserCreateReq {

    /** 登录用户名 */
    private String username;

    /** 初始密码 */
    private String password;

    /** 昵称 */
    private String nickname;

    /** 角色：ADMIN / OPERATOR / VIEWER */
    private String role;
}
