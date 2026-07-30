package bob.myxos.collector.collector;

import bob.myxos.domain.entity.DiscoverTask;
import bob.myxos.domain.mapper.DiscoverTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 设备发现任务调度器
 * <p>
 * 每 5 秒轮询一次状态为 PENDING 的发现任务，交给 {@link DeviceDiscoveryScanner} 执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceDiscoveryJob {

    private final DiscoverTaskMapper discoverTaskMapper;
    private final DeviceDiscoveryScanner scanner;

    /**
     * 每 5 秒轮询一次待执行发现任务
     */
    @Scheduled(fixedDelay = 5000)
    public void poll() {
        List<DiscoverTask> tasks = discoverTaskMapper.selectList(
                new LambdaQueryWrapper<DiscoverTask>()
                        .eq(DiscoverTask::getStatus, "PENDING")
                        .orderByAsc(DiscoverTask::getId)
                        .last("LIMIT 1"));
        if (tasks == null || tasks.isEmpty()) {
            return;
        }
        for (DiscoverTask task : tasks) {
            try {
                scanner.scan(task);
            } catch (Exception e) {
                log.error("网段扫描任务失败：taskId={}", task.getId(), e);
            }
        }
    }
}
