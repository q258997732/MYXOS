package bob.myxos.main.controller;

import bob.myxos.common.api.PageResult;
import bob.myxos.common.api.Result;
import bob.myxos.domain.entity.ThresholdAction;
import bob.myxos.domain.entity.ThresholdRule;
import bob.myxos.main.dto.ThresholdRuleReq;
import bob.myxos.main.service.ThresholdService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import javax.validation.Valid;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 阈值规则管理控制器
 */
@RestController
@RequestMapping("/api/thresholds")
@RequiredArgsConstructor
public class ThresholdController {

    private final ThresholdService thresholdService;

    /**
     * 分页查询阈值规则
     *
     * @param metricType 指标类型（可选）
     * @param enabled    启用状态（可选）
     * @param page       当前页
     * @param size       每页大小
     * @return 分页结果
     */
    @GetMapping
    public Result<PageResult<ThresholdRule>> list(
            @RequestParam(required = false) String metricType,
            @RequestParam(required = false) Integer enabled,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "20") Long size) {
        Page<ThresholdRule> p = thresholdService.list(metricType, enabled, page, size);
        PageResult<ThresholdRule> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPages(p.getPages());
        result.setCurrent(p.getCurrent());
        result.setSize(p.getSize());
        result.setRecords(p.getRecords());
        return Result.ok(result);
    }

    /**
     * 创建阈值规则
     *
     * @param req 创建请求
     * @return 已创建的规则
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<ThresholdRule> create(@Valid @RequestBody ThresholdRuleReq req) {
        return Result.ok(thresholdService.create(req));
    }

    /**
     * 查询规则详情（含动作列表）
     *
     * @param id 规则 ID
     * @return 规则及动作
     */
    @GetMapping("/{id}")
    public Result<Map<String, Object>> detail(@PathVariable Long id) {
        ThresholdRule rule = thresholdService.detail(id);
        List<ThresholdAction> actions = thresholdService.listActions(id);
        Map<String, Object> data = new HashMap<>(4);
        data.put("rule", rule);
        data.put("actions", actions);
        return Result.ok(data);
    }

    /**
     * 更新阈值规则
     *
     * @param id  规则 ID
     * @param req 更新请求
     * @return 更新后的规则
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<ThresholdRule> update(@PathVariable Long id, @Valid @RequestBody ThresholdRuleReq req) {
        return Result.ok(thresholdService.update(id, req));
    }

    /**
     * 删除阈值规则（逻辑删除）
     *
     * @param id 规则 ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<Void> delete(@PathVariable Long id) {
        thresholdService.delete(id);
        return Result.ok();
    }

    /**
     * 切换启用状态
     *
     * @param id      规则 ID
     * @param enabled 可选目标状态（不传则自动取反）
     * @return 空响应
     */
    @PostMapping("/{id}/toggle")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<Void> toggle(@PathVariable Long id,
                                   @RequestParam(required = false) Integer enabled) {
        thresholdService.toggle(id, enabled);
        return Result.ok();
    }
}
