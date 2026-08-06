package bob.myxos.main.controller;

import bob.myxos.common.api.PageResult;
import bob.myxos.common.api.Result;
import bob.myxos.domain.entity.ActionLog;
import bob.myxos.domain.entity.AlarmEvent;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.MetricSnapshot;
import bob.myxos.domain.entity.OpTask;
import bob.myxos.main.dto.AndroidInstanceVO;
import bob.myxos.main.dto.DeviceCreateReq;
import bob.myxos.main.dto.DeviceListResp;
import bob.myxos.main.dto.DeviceOpReq;
import bob.myxos.main.dto.DeviceUpdateReq;
import bob.myxos.main.dto.ShellReq;
import bob.myxos.main.dto.MetricBindingReq;
import bob.myxos.main.dto.BatchMetricBindingReq;
import bob.myxos.main.dto.BatchMetricBindingResult;
import bob.myxos.domain.entity.MetricBinding;
import bob.myxos.main.service.DeviceService;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
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
import javax.validation.constraints.NotBlank;
import javax.validation.constraints.Pattern;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 设备管理控制器
 */
@RestController
@RequestMapping("/api/devices")
@Validated
@RequiredArgsConstructor
public class DeviceController {

    private final DeviceService deviceService;

    /**
     * 分页查询设备列表
     *
     * @param groupId 分组 ID（可选）
     * @param status  状态（可选）
     * @param keyword 关键字（可选）
     * @param page    当前页
     * @param size    每页大小
     * @return 分页结果（附带 FIRING 告警数量）
     */
    @GetMapping
    public Result<PageResult<DeviceListResp>> list(
            @RequestParam(required = false) Long groupId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "1") Long page,
            @RequestParam(defaultValue = "20") Long size) {
        Page<DeviceListResp> p = deviceService.listDevices(groupId, status, keyword, page, size);
        PageResult<DeviceListResp> result = new PageResult<>();
        result.setTotal(p.getTotal());
        result.setPages(p.getPages());
        result.setCurrent(p.getCurrent());
        result.setSize(p.getSize());
        result.setRecords(p.getRecords());
        return Result.ok(result);
    }

    /**
     * 手动创建设备
     *
     * @param req 创建请求
     * @return 已创建的设备
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<Device> create(@Valid @RequestBody DeviceCreateReq req) {
        return Result.ok(deviceService.createDevice(req));
    }

    /**
     * 查询指定作用范围内采集到过的安卓实例名称（去重排序）
     * <p>
     * 用于阈值规则"实例名称"多选下拉的数据源
     *
     * @param scopeType 作用范围：ALL/GROUP/DEVICE
     * @param scopeId   分组 ID 或单设备 ID（可选）
     * @param scopeIds  多设备 ID 逗号串（可选）
     * @return 安卓实例名称列表
     */
    @GetMapping("/android-names")
    public Result<List<String>> androidNames(@RequestParam String scopeType,
                                             @RequestParam(required = false) Long scopeId,
                                             @RequestParam(required = false) String scopeIds) {
        return Result.ok(deviceService.listAndroidNames(scopeType, scopeId, scopeIds));
    }

    /**
     * 查询设备详情
     *
     * @param id 设备 ID
     * @return 设备实体
     */
    @GetMapping("/{id}")
    public Result<Device> detail(@PathVariable Long id) {
        return Result.ok(deviceService.getDetail(id));
    }

    /**
     * 更新设备
     *
     * @param id  设备 ID
     * @param req 更新请求
     * @return 更新后的设备
     */
    @PutMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<Device> update(@PathVariable Long id, @Valid @RequestBody DeviceUpdateReq req) {
        return Result.ok(deviceService.updateDevice(id, req));
    }

    /**
     * 删除设备（逻辑删除）
     *
     * @param id 设备 ID
     * @return 空响应
     */
    @DeleteMapping("/{id}")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<Void> delete(@PathVariable Long id) {
        deviceService.deleteDevice(id);
        return Result.ok();
    }

    /**
     * 触发一次立即采集（写入一条 COLLECT 操作任务）
     *
     * @param id 设备 ID
     * @return 空响应
     */
    @PostMapping("/{id}/collect")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<Void> collect(@PathVariable Long id) {
        deviceService.triggerCollect(id);
        return Result.ok();
    }

    /**
     * 下发手动操作任务（写入 op_task 表）
     *
     * @param id  设备 ID
     * @param req 操作请求
     * @return 任务 ID
     */
    @PostMapping("/{id}/ops")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<Map<String, Object>> ops(@PathVariable Long id, @Valid @RequestBody DeviceOpReq req) {
        Long taskId = deviceService.submitOpTask(id, req.getOperationCode(), req.getParams());
        Map<String, Object> data = new HashMap<>(2);
        data.put("taskId", taskId);
        return Result.ok(data);
    }

    /**
     * 设备截图（临时查看，不保存）
     *
     * @param id    设备 ID
     * @param name  安卓容器名称
     * @param level 截图等级
     * @return 图片数据（Base64 或 URL）
     */
    @GetMapping("/{id}/screenshot")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<String> screenshot(@PathVariable Long id,
                                       @RequestParam @NotBlank(message = "容器名称不能为空")
                                       @Pattern(regexp = "^[A-Za-z0-9_.-]{1,64}$", message = "容器名称包含非法字符")
                                       String name,
                                       @RequestParam(defaultValue = "1")
                                       @Pattern(regexp = "^[0-9]{1,4}$", message = "截图等级格式错误")
                                       String level) {
        String data = deviceService.screenshot(id, name, level);
        return Result.ok(data);
    }

    /**
     * 分页查询设备指标快照
     *
     * @param id   设备 ID
     * @param page 当前页
     * @param size 每页大小
     * @return 指标快照分页结果
     */
    @GetMapping("/{id}/metrics")
    public Result<PageResult<MetricSnapshot>> metrics(@PathVariable Long id,
                                                          @RequestParam(defaultValue = "1") Long page,
                                                          @RequestParam(defaultValue = "20") Long size) {
        Page<MetricSnapshot> p = deviceService.listMetrics(id, page, size);
        return Result.ok(toPageResult(p));
    }

    /**
     * 查询设备实时指标（每种类型最新一条）
     *
     * @param id 设备 ID
     * @return 最新指标列表
     */
    @GetMapping("/{id}/metrics/latest")
    public Result<List<MetricSnapshot>> latestMetrics(@PathVariable Long id) {
        return Result.ok(deviceService.listLatestMetrics(id));
    }

    /**
     * 分页查询指定类型的指标历史记录
     *
     * @param id         设备 ID
     * @param metricType 指标类型
     * @param page       当前页
     * @param size       每页大小
     * @return 指标快照分页结果
     */
    @GetMapping("/{id}/metrics/history")
    public Result<PageResult<MetricSnapshot>> metricHistory(@PathVariable Long id,
                                                                @RequestParam String metricType,
                                                                @RequestParam(defaultValue = "1") Long page,
                                                                @RequestParam(defaultValue = "20") Long size) {
        Page<MetricSnapshot> p = deviceService.listMetricHistory(id, metricType, page, size);
        return Result.ok(toPageResult(p));
    }

    /**
     * 分页查询设备告警事件
     *
     * @param id   设备 ID
     * @param page 当前页
     * @param size 每页大小
     * @return 告警事件分页结果
     */
    @GetMapping("/{id}/alarms")
    public Result<PageResult<AlarmEvent>> alarms(@PathVariable Long id,
                                                     @RequestParam(defaultValue = "1") Long page,
                                                     @RequestParam(defaultValue = "20") Long size) {
        Page<AlarmEvent> p = deviceService.listAlarms(id, page, size);
        return Result.ok(toPageResult(p));
    }

    /**
     * 分页查询设备动作日志
     *
     * @param id   设备 ID
     * @param page 当前页
     * @param size 每页大小
     * @return 动作日志分页结果
     */
    @GetMapping("/{id}/logs")
    public Result<PageResult<ActionLog>> logs(@PathVariable Long id,
                                                   @RequestParam(defaultValue = "1") Long page,
                                                   @RequestParam(defaultValue = "50") Long size) {
        Page<ActionLog> p = deviceService.listLogs(id, page, size);
        return Result.ok(toPageResult(p));
    }

    /**
     * 分页查询设备运维任务
     *
     * @param id   设备 ID
     * @param page 当前页
     * @param size 每页大小
     * @return 运维任务分页结果
     */
    @GetMapping("/{id}/tasks")
    public Result<PageResult<OpTask>> tasks(@PathVariable Long id,
                                                  @RequestParam(defaultValue = "1") Long page,
                                                  @RequestParam(defaultValue = "20") Long size) {
        Page<OpTask> p = deviceService.listOpTasks(id, page, size);
        return Result.ok(toPageResult(p));
    }

    /**
     * 查询设备上的安卓实例列表（含状态）
     *
     * @param id 设备 ID
     * @return 安卓实例视图列表
     */
    @GetMapping("/{id}/androids")
    public Result<List<AndroidInstanceVO>> androids(@PathVariable Long id) {
        return Result.ok(deviceService.listAndroidInstances(id));
    }

    @GetMapping("/{id}/metric-bindings")
    public Result<List<MetricBinding>> metricBindings(@PathVariable Long id) {
        return Result.ok(deviceService.listMetricBindings(id, ""));
    }

    @PutMapping("/{id}/metric-bindings")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<MetricBinding>> saveMetricBindings(@PathVariable Long id, @Valid @RequestBody MetricBindingReq req) {
        return Result.ok(deviceService.saveMetricBindings(id, "", req));
    }

    @PostMapping("/metric-bindings/batch")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<BatchMetricBindingResult> saveBatchMetricBindings(@Valid @RequestBody BatchMetricBindingReq req) {
        return Result.ok(deviceService.saveMetricBindings(req));
    }

    @GetMapping("/{id}/androids/{name}/metric-bindings")
    public Result<List<MetricBinding>> androidMetricBindings(@PathVariable Long id,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_.-]{1,128}$", message = "安卓实例名称格式不合法") String name) {
        return Result.ok(deviceService.listMetricBindings(id, name));
    }

    @PutMapping("/{id}/androids/{name}/metric-bindings")
    @PreAuthorize("hasRole('ADMIN')")
    public Result<List<MetricBinding>> saveAndroidMetricBindings(@PathVariable Long id,
            @PathVariable @Pattern(regexp = "^[A-Za-z0-9_.-]{1,128}$", message = "安卓实例名称格式不合法") String name,
            @Valid @RequestBody MetricBindingReq req) {
        return Result.ok(deviceService.saveMetricBindings(id, name, req));
    }

    /**
     * 同步执行 Adb shell 命令
     *
     * @param id      设备 ID
     * @param name    安卓容器名称
     * @param command shell 命令
     * @return 命令执行输出
     */
    /**
     * 同步执行 Adb 命令（请求体为 JSON：{"name":"容器名","command":"shell 命令"}）
     *
     * @param id  设备 ID
     * @param req shell 请求（容器名称 + 命令）
     * @return 命令执行输出
     */
    @PostMapping("/{id}/shell")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<String> shell(@PathVariable Long id, @RequestBody @Valid ShellReq req) {
        return Result.ok(deviceService.executeShell(id, req.getName(), req.getCommand().trim()));
    }

    /**
     * 同步获取剪贴板内容
     *
     * @param id   设备 ID
     * @param name 安卓容器名称
     * @return 剪贴板文本内容
     */
    @GetMapping("/{id}/clipboard")
    @PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
    public Result<String> clipboard(@PathVariable Long id,
                                       @RequestParam @NotBlank(message = "容器名称不能为空")
                                       @Pattern(regexp = "^[A-Za-z0-9_.-]{1,64}$", message = "容器名称包含非法字符")
                                       String name) {
        return Result.ok(deviceService.getClipboard(id, name));
    }

    /**
     * 将 MyBatis-Plus Page 转换为通用分页结果
     */
    private <T> PageResult<T> toPageResult(Page<T> page) {
        PageResult<T> result = new PageResult<>();
        result.setTotal(page.getTotal());
        result.setPages(page.getPages());
        result.setCurrent(page.getCurrent());
        result.setSize(page.getSize());
        result.setRecords(page.getRecords());
        return result;
    }
}
