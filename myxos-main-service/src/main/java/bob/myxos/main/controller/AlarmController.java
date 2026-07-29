package bob.myxos.main.controller;

import bob.myxos.common.api.PageResult;
import bob.myxos.common.api.Result;
import bob.myxos.domain.entity.AlarmEvent;
import bob.myxos.main.service.AlarmService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 告警事件管理控制器
 */
@RestController
@RequestMapping("/api/alarms")
@RequiredArgsConstructor
public class AlarmController {

    private final AlarmService alarmService;

    /**
     * 分页查询告警事件
     *
     * @param status   状态（可选：FIRING / RESOLVED）
     * @param deviceId 设备 ID（可选）
     * @param page     当前页
     * @param size     每页大小
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<AlarmEvent>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long deviceId,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "20") Long size) {
        Page<AlarmEvent> p = alarmService.list(status, deviceId, page, size);
        PageResult<AlarmEvent> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPages(p.getPages());
        result.setCurrent(p.getCurrent());
        result.setSize(p.getSize());
        result.setRecords(p.getRecords());
        return Result.ok(result);
    }

    /**
     * 手动恢复告警
     *
     * @param id 告警 ID
     * @return 空响应
     */
    @PostMapping("/{id}/resolve")
    public Result<Void> resolve(@PathVariable Long id) {
        alarmService.resolve(id);
        return Result.ok();
    }
}
