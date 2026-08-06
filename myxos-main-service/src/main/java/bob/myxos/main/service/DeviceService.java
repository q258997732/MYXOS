package bob.myxos.main.service;

import bob.myxos.domain.entity.ActionLog;
import bob.myxos.domain.entity.AlarmEvent;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.MetricSnapshot;
import bob.myxos.domain.entity.OpTask;
import bob.myxos.domain.entity.MetricBinding;
import bob.myxos.main.dto.MetricBindingReq;
import bob.myxos.main.dto.BatchMetricBindingReq;
import bob.myxos.main.dto.BatchMetricBindingResult;
import bob.myxos.main.dto.AndroidInstanceVO;
import bob.myxos.main.dto.DeviceCreateReq;
import bob.myxos.main.dto.DeviceListResp;
import bob.myxos.main.dto.DeviceUpdateReq;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

import java.util.List;
import java.util.Map;

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
     * @param params        操作参数（Map，可选）
     * @return 任务 ID
     */
    Long submitOpTask(Long id, String operationCode, Map<String, Object> params);

    /**
     * 设备截图（临时查看，不保存）
     *
     * @param id   设备 ID
     * @param name 安卓容器名称
     * @param level 截图等级
     * @return Base64 图片数据或图片 URL
     */
    String screenshot(Long id, String name, String level);

    /**
     * 分页查询设备指标快照
     *
     * @param id   设备 ID
     * @param page 当前页
     * @param size 每页大小
     * @return 指标快照分页结果
     */
    Page<MetricSnapshot> listMetrics(Long id, Long page, Long size);

    /**
     * 分页查询设备告警事件
     *
     * @param id   设备 ID
     * @param page 当前页
     * @param size 每页大小
     * @return 告警事件分页结果
     */
    Page<AlarmEvent> listAlarms(Long id, Long page, Long size);

    /**
     * 分页查询设备动作日志
     *
     * @param id   设备 ID
     * @param page 当前页
     * @param size 每页大小
     * @return 动作日志分页结果
     */
    Page<ActionLog> listLogs(Long id, Long page, Long size);

    /**
     * 查询设备上运行的安卓实例列表（包含状态）
     *
     * @param id 设备 ID
     * @return 安卓实例视图列表
     */
    List<AndroidInstanceVO> listAndroidInstances(Long id);

    /**
     * 查询设备实时指标（每种类型返回最新一条）
     *
     * @param id 设备 ID
     * @return 最新指标快照列表
     */
    List<MetricSnapshot> listLatestMetrics(Long id);

    /**
     * 分页查询指定类型的指标历史记录
     *
     * @param id         设备 ID
     * @param metricType 指标类型
     * @param page       当前页
     * @param size       每页大小
     * @return 指标快照分页结果
     */
    Page<MetricSnapshot> listMetricHistory(Long id, String metricType, Long page, Long size);

    /**
     * 分页查询设备运维任务
     *
     * @param id   设备 ID
     * @param page 当前页
     * @param size 每页大小
     * @return 运维任务分页结果
     */
    Page<OpTask> listOpTasks(Long id, Long page, Long size);

    /**
     * 同步执行 Adb shell 命令并返回结果
     *
     * @param id      设备 ID
     * @param name    安卓容器名称
     * @param command shell 命令
     * @return 命令执行输出
     */
    String executeShell(Long id, String name, String command);

    /**
     * 同步获取剪贴板内容
     *
     * @param id   设备 ID
     * @param name 安卓容器名称
     * @return 剪贴板文本内容
     */
    String getClipboard(Long id, String name);

    /**
     * 查询指定作用范围内采集到过的安卓实例名称（去重排序）
     * <p>
     * 用于阈值规则"实例名称"多选下拉的数据源
     *
     * @param scopeType 作用范围：ALL/GROUP/DEVICE
     * @param scopeId   分组 ID 或单设备 ID（可选）
     * @param scopeIds  多设备 ID 逗号串（可选，DEVICE 场景优先）
     * @return 安卓实例名称列表
     */
    List<String> listAndroidNames(String scopeType, Long scopeId, String scopeIds);

    List<MetricBinding> listMetricBindings(Long id, String androidName);

    List<MetricBinding> saveMetricBindings(Long id, String androidName, MetricBindingReq req);

    BatchMetricBindingResult saveMetricBindings(BatchMetricBindingReq req);

    MetricBinding resolveEffectiveMetricBinding(Long id, String androidName, String metricCode);
}
