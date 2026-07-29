package bob.myxos.main.service;

import bob.myxos.domain.entity.Device;
import bob.myxos.main.dto.DeviceCreateReq;
import bob.myxos.main.dto.DeviceListResp;
import bob.myxos.main.dto.DeviceUpdateReq;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 设备业务接口
 */
public interface DeviceService {

    /**
     * 手动创建设备
     *
     * @param req 创建请求
     * @return 已创建的设备
     */
    Device createDevice(DeviceCreateReq req);

    /**
     * 分页查询设备列表（附带 FIRING 告警数量）
     *
     * @param groupId 分组 ID（可选）
     * @param status  状态（可选）
     * @param keyword 关键字（匹配名称或 IP，可选）
     * @param page    当前页
     * @param size    每页大小
     * @return 分页结果
     */
    Page<DeviceListResp> listDevices(Long groupId, String status, String keyword, Long page, Long size);

    /**
     * 查询设备详情
     *
     * @param id 设备 ID
     * @return 设备实体
     */
    Device getDetail(Long id);

    /**
     * 更新设备
     *
     * @param id  设备 ID
     * @param req 更新请求
     * @return 更新后的设备
     */
    Device updateDevice(Long id, DeviceUpdateReq req);

    /**
     * 删除设备（逻辑删除）
     *
     * @param id 设备 ID
     */
    void deleteDevice(Long id);

    /**
     * 触发一次立即采集（当前阶段简化为写入一条采集类操作任务）
     *
     * @param id 设备 ID
     */
    void triggerCollect(Long id);

    /**
     * 下发手动操作任务（写入 op_task 表）
     *
     * @param id            设备 ID
     * @param operationCode 操作码
     * @param params        操作参数（JSON 字符串）
     * @return 任务 ID
     */
    Long submitOpTask(Long id, String operationCode, String params);
}
