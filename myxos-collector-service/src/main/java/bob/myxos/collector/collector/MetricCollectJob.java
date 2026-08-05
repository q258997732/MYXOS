package bob.myxos.collector.collector;

import bob.myxos.common.enums.DeviceStatus;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.mytos.MytosClientFactory;
import bob.myxos.collector.evaluate.ThresholdEvaluator;
import bob.myxos.collector.service.MetricPersistService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 指标采集定时任务
 * 按固定间隔扫描所有非禁用设备，提交到采集线程池并发执行
 * <p>
 * 通过 inFlight 标记避免同一设备在上次采集未完成时被重复提交。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class MetricCollectJob {

    private final DeviceMapper deviceMapper;
    private final MytosClientFactory clientFactory;
    private final MetricPersistService metricPersistService;
    private final ThresholdEvaluator thresholdEvaluator;

    /** 指标采集线程池 */
    @Resource(name = "metricCollectExecutor")
    private ThreadPoolTaskExecutor metricCollectExecutor;

    /** 正在采集中的设备标记，防止重复提交 */
    private final ConcurrentHashMap<Long, Boolean> inFlight = new ConcurrentHashMap<>();

    /**
     * 定时采集入口
     * <p>
     * 由 {@code MetricCollectScheduleConfig} 以动态 Trigger 调度：
     * 每轮执行完成后按 sys_config 的 collect.interval.sec（秒）计算下一轮执行时间，
     * 页面上修改采集间隔后下一轮调度即生效，无需重启服务
     */
    public void collect() {
        List<Device> devices = deviceMapper.selectList(
                new LambdaQueryWrapper<Device>()
                        .ne(Device::getStatus, DeviceStatus.DISABLED.name())
                        .eq(Device::getDeleted, 0));
        log.info("开始采集，设备数量：{}", devices.size());
        for (Device device : devices) {
            // 已在采集中的设备跳过本轮
            if (inFlight.putIfAbsent(device.getId(), Boolean.TRUE) != null) {
                continue;
            }
            metricCollectExecutor.execute(() -> {
                try {
                    MetricCollector collector = new MetricCollector(device, clientFactory,
                            snapshots -> {
                                // 先持久化再评估：DURATION/CONSECUTIVE 触发模式依赖历史快照查询
                                metricPersistService.saveBatchSnapshots(snapshots);
                                thresholdEvaluator.evaluate(device, snapshots);
                            },
                            deviceMapper);
                    collector.run();
                } finally {
                    inFlight.remove(device.getId());
                }
            });
        }
    }
}
