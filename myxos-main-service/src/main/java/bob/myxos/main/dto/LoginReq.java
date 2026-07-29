package bob.myxos.main.dto;

import lombok.Data;

import javax.validation.constraints.NotBlank;

/**
 * 登录请求 DTO
 */
@Data
public class LoginReq {

    /** 登录用户名 */
    @NotBlank(message = "用户名不能为空")
    private String username;

    /** 登录密码（明文，服务端使用 BCrypt 校验） */
    @NotBlank(message = "密码不能为空")
    private String password;
}
