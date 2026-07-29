package bob.myxos.collector.collector;

import bob.myxos.common.enums.DeviceStatus;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.mytos.MytosClientFactory;
import bob.myxos.collector.service.MetricPersistService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
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

    /** 指标采集线程池 */
    @Resource(name = "metricCollectExecutor")
    private ThreadPoolTaskExecutor metricCollectExecutor;

    /** 正在采集中的设备标记，防止重复提交 */
    private final ConcurrentHashMap<Long, Boolean> inFlight = new ConcurrentHashMap<>();

    /**
     * 定时采集入口
     * 间隔由 myxos.collector.interval-ms 配置控制，默认 30 秒
     */
    @Scheduled(fixedDelayString = "${myxos.collector.interval-ms:30000}")
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
                            snapshots -> metricPersistService.saveBatchSnapshots(snapshots),
                            deviceMapper);
                    collector.run();
                } finally {
                    inFlight.remove(device.getId());
                }
            });
        }
    }
}
