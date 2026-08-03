package bob.myxos.main.service;

import bob.myxos.domain.entity.SysUser;
import bob.myxos.main.dto.UserCreateReq;
import bob.myxos.main.dto.UserUpdateReq;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 系统用户业务接口
 */
public interface SysUserService {

    /**
     * 分页查询用户
     *
     * @param page 当前页
     * @param size 每页大小
     * @return 分页结果
     */
    Page<SysUser> list(Long page, Long size);

    /**
     * 创建用户
     *
     * @param req 创建请求
     * @return 已创建用户
     */
    SysUser create(UserCreateReq req);

    /**
     * 更新用户
     *
     * @param id  用户 ID
     * @param req 更新请求
     * @return 更新后的用户
     */
    SysUser update(Long id, UserUpdateReq req);

    /**
     * 重置密码
     *
     * @param id          用户 ID
     * @param rawPassword 明文新密码
     */
    void resetPassword(Long id, String rawPassword);
}
