package bob.myxos.main.controller;

import bob.myxos.common.api.PageResult;
import bob.myxos.common.api.Result;
import bob.myxos.domain.entity.OpTask;
import bob.myxos.main.service.OpTaskService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 操作任务控制器
 */
@RestController
@RequestMapping("/api/op-tasks")
@RequiredArgsConstructor
public class OpTaskController {

    private final OpTaskService opTaskService;

    /**
     * 分页查询操作任务
     *
     * @param status 状态
     * @param source 来源
     * @param page   当前页
     * @param size   每页大小
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<OpTask>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String source,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "20") Long size) {
        Page<OpTask> p = opTaskService.list(status, source, page, size);
        PageResult<OpTask> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPages(p.getPages());
        result.setCurrent(p.getCurrent());
        result.setSize(p.getSize());
        result.setRecords(p.getRecords());
        return Result.ok(result);
    }

    /**
     * 查询操作任务详情
     *
     * @param id 任务 ID
     * @return 任务详情
     */
    @GetMapping("/{id}")
    public Result<OpTask> detail(@PathVariable Long id) {
        return Result.ok(opTaskService.getById(id));
    }

    /**
     * 重试操作任务
     *
     * @param id 任务 ID
     * @return 空结果
     */
    @PostMapping("/{id}/retry")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<Void> retry(@PathVariable Long id) {
        opTaskService.retry(id);
        return Result.ok();
    }
}
