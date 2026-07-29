package bob.myxos.main.controller;

import bob.myxos.common.api.PageResult;
import bob.myxos.common.api.Result;
import bob.myxos.domain.entity.Device;
import bob.myxos.main.dto.DeviceCreateReq;
import bob.myxos.main.dto.DeviceListResp;
import bob.myxos.main.dto.DeviceOpReq;
import bob.myxos.main.dto.DeviceUpdateReq;
import bob.myxos.main.service.DeviceService;
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
import java.util.Map;

/**
 * 设备管理控制器
 */
@RestController
@RequestMapping("/api/devices")
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
}
