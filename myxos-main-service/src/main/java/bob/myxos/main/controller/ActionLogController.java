package bob.myxos.main.controller;

import bob.myxos.common.api.PageResult;
import bob.myxos.common.api.Result;
import bob.myxos.domain.entity.ActionLog;
import bob.myxos.main.service.ActionLogService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 动作日志控制器
 */
@RestController
@RequestMapping("/api/logs")
@RequiredArgsConstructor
public class ActionLogController {

    private final ActionLogService actionLogService;

    /**
     * 分页查询动作日志
     *
     * @param actionType 动作类型
     * @param logLevel   日志级别
     * @param page       当前页
     * @param size       每页大小
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<ActionLog>> list(
            @RequestParam(required = false) String actionType,
            @RequestParam(required = false) String logLevel,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "20") Long size) {
        Page<ActionLog> p = actionLogService.list(actionType, logLevel, page, size);
        PageResult<ActionLog> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPages(p.getPages());
        result.setCurrent(p.getCurrent());
        result.setSize(p.getSize());
        result.setRecords(p.getRecords());
        return Result.ok(result);
    }
}
