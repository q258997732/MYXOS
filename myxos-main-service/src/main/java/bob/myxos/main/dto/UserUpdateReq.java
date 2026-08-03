package bob.myxos.main.dto;

import lombok.Data;

/**
 * 用户更新请求
 */
@Data
public class UserUpdateReq {

    /** 昵称 */
    private String nickname;

    /** 角色：ADMIN / OPERATOR / VIEWER */
    private String role;

    /** 状态：1 启用，0 禁用 */
    private Integer status;
}
