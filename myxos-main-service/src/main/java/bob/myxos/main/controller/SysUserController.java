package bob.myxos.main.controller;

import bob.myxos.common.api.PageResult;
import bob.myxos.common.api.Result;
import bob.myxos.domain.entity.SysUser;
import bob.myxos.main.dto.UserCreateReq;
import bob.myxos.main.dto.UserUpdateReq;
import bob.myxos.main.service.SysUserService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

/**
 * 系统用户控制器
 */
@RestController
@RequestMapping("/api/users")
@RequiredArgsConstructor
public class SysUserController {

    private final SysUserService sysUserService;

    /**
     * 分页查询用户
     *
     * @param page 当前页
     * @param size 每页大小
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<SysUser>> list(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "20") Long size) {
        Page<SysUser> p = sysUserService.list(page, size);
        PageResult<SysUser> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPages(p.getPages());
        result.setCurrent(p.getCurrent());
        result.setSize(p.getSize());
        result.setRecords(p.getRecords());
        return Result.ok(result);
    }

    /**
     * 创建用户
     *
     * @param req 创建请求
     * @return 已创建用户
     */
    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public Result<SysUser> create(@RequestBody UserCreateReq req) {
        return Result.ok(sysUserService.create(req));
    }

    /**
     * 更新用户
     *
     * @param id  用户 ID
     * @param req 更新请求
     * @return 更新后的用户
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<SysUser> update(@PathVariable Long id, @RequestBody UserUpdateReq req) {
        return Result.ok(sysUserService.update(id, req));
    }

    /**
     * 重置密码
     *
     * @param id       用户 ID
     * @param password 新密码
     * @return 空响应
     */
    @PostMapping("/{id}/reset-password")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> resetPassword(@PathVariable Long id, @RequestBody Map<String, String> body) {
        sysUserService.resetPassword(id, body.get("password"));
        return Result.ok();
    }
}
