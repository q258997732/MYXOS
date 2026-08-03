package bob.myxos.main.controller;

import bob.myxos.common.api.PageResult;
import bob.myxos.common.api.Result;
import bob.myxos.domain.entity.DiscoverTask;
import bob.myxos.main.dto.DiscoverReq;
import bob.myxos.main.service.DiscoverService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.validation.Valid;

/**
 * 设备发现控制器
 */
@RestController
@RequestMapping("/api/discover")
@RequiredArgsConstructor
public class DiscoverController {

    private final DiscoverService discoverService;

    /**
     * 提交 CIDR 网段扫描任务
     *
     * @param req 扫描请求
     * @return 已创建的发现任务
     */
    @PostMapping("/scan")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<DiscoverTask> scan(@Valid @RequestBody DiscoverReq req) {
        return Result.ok(discoverService.submit(req));
    }

    /**
     * 分页查询发现任务
     *
     * @param page 当前页
     * @param size 每页大小
     * @return 分页结果
     */
    @GetMapping("/tasks")
    public Result<PageResult<DiscoverTask>> list(
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "20") Long size) {
        Page<DiscoverTask> p = discoverService.list(page, size);
        PageResult<DiscoverTask> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPages(p.getPages());
        result.setCurrent(p.getCurrent());
        result.setSize(p.getSize());
        result.setRecords(p.getRecords());
        return Result.ok(result);
    }

    /**
     * 删除指定发现任务（逻辑删除）
     *
     * @param id 任务 ID
     * @return 空响应
     */
    @DeleteMapping("/tasks/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<Void> delete(@PathVariable Long id) {
        discoverService.delete(id);
        return Result.ok();
    }

    /**
     * 清空所有已完成/失败的发现任务
     *
     * @return 空响应
     */
    @DeleteMapping("/tasks")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<Void> clear() {
        discoverService.clear();
        return Result.ok();
    }
}
