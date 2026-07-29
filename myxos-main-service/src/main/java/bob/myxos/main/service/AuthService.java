package bob.myxos.main.service;

import bob.myxos.main.dto.LoginReq;
import bob.myxos.main.dto.LoginResp;

/**
 * 认证服务接口
 */
public interface AuthService {

    /**
     * 用户登录
     *
     * @param req 登录请求
     * @return 登录响应（包含 JWT 与用户信息）
     */
    LoginResp login(LoginReq req);

    /**
     * 用户登出
     * 吊销当前 JWT 令牌
     *
     * @param userId  用户 ID
     * @param tokenId 当前 JWT 的 jti
     */
    void logout(Long userId, String tokenId);
}
