package bob.myxos.collector.collector;

import bob.myxos.common.enums.DeviceMode;
import bob.myxos.common.enums.DeviceStatus;
import bob.myxos.common.util.IpUtils;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.DiscoverTask;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.domain.mapper.DiscoverTaskMapper;
import bob.myxos.mytos.MytosClient;
import bob.myxos.mytos.MytosClientFactory;
import bob.myxos.mytos.dto.HealthResp;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CIDR 网段设备发现扫描器
 * <p>
 * 对指定 CIDR 网段和端口范围并发探测，调用 MYTOS /host_api/v1/healthcheck 接口识别设备并写入 device 表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceDiscoveryScanner {

    /** 扫描线程数 */
    private static final int SCAN_THREADS = 16;
    /** 单次扫描超时上限（秒） */
    private static final int SCAN_TIMEOUT_SECONDS = 600;
    /** 进度刷新周期（秒） */
    private static final int PROGRESS_INTERVAL_SECONDS = 2;

    private final MytosClientFactory clientFactory;
    private final DeviceMapper deviceMapper;
    private final DiscoverTaskMapper discoverTaskMapper;

    /**
     * 执行扫描任务
     *
     * @param task 待执行的发现任务
     */
    public void scan(DiscoverTask task) {
        List<String> ips;
        try {
            ips = IpUtils.expandCidr(task.getCidr());
        } catch (Exception e) {
            failTask(task, e.getMessage());
            return;
        }

        int portCount = task.getPortTo() - task.getPortFrom() + 1;
        int total = ips.size() * portCount;
        if (total <= 0) {
            failTask(task, "端口范围非法");
            return;
        }

        // 更新总探测数（IP × 端口），并置已扫描数为 0
        task.setTotalIpCount(total);
        task.setScannedIpCount(0);
        markRunning(task);

        ExecutorService executor = Executors.newFixedThreadPool(SCAN_THREADS);
        AtomicInteger found = new AtomicInteger(0);
        AtomicInteger scanned = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(total);

        ScheduledExecutorService progressScheduler = Executors.newSingleThreadScheduledExecutor();
        Long taskId = task.getId();
        progressScheduler.scheduleAtFixedRate(() -> updateProgress(taskId, scanned.get(), total),
                PROGRESS_INTERVAL_SECONDS, PROGRESS_INTERVAL_SECONDS, TimeUnit.SECONDS);

        for (String ip : ips) {
            for (int port = task.getPortFrom(); port <= task.getPortTo(); port++) {
                final int p = port;
                executor.execute(() -> {
                    try {
                        MytosClient client = clientFactory.create(ip, p);
                        HealthResp resp = client.healthcheck(ip);
                        if (resp.getCode() != null && resp.getCode() == 200) {
                            saveDiscoveredDevice(ip, p, resp);
                            found.incrementAndGet();
                        }
                    } catch (Exception e) {
                        log.debug("探测失败：{}:{}, 原因：{}", ip, p, e.toString());
                    } finally {
                        scanned.incrementAndGet();
                        latch.countDown();
                    }
                });
            }
        }

        boolean completed = false;
        try {
            completed = latch.await(SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!completed) {
                log.warn("网段扫描任务超时：taskId={}", taskId);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("网段扫描任务被中断：taskId={}", taskId);
        } finally {
            executor.shutdownNow();
            progressScheduler.shutdownNow();
            try {
                // 等待进度刷新任务结束，避免终态被覆盖
                if (!progressScheduler.awaitTermination(PROGRESS_INTERVAL_SECONDS + 1, TimeUnit.SECONDS)) {
                    log.warn("进度刷新线程未能优雅关闭：taskId={}", taskId);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        markDone(taskId, found.get(), scanned.get(), total, completed);
    }

    private void markRunning(DiscoverTask task) {
        task.setStatus("RUNNING");
        task.setStartedAt(LocalDateTime.now());
        discoverTaskMapper.updateById(task);
    }

    private void updateProgress(Long taskId, int scannedCount, int total) {
        try {
            DiscoverTask update = new DiscoverTask();
            update.setId(taskId);
            update.setScannedIpCount(scannedCount);
            update.setMessage(String.format("扫描中：%d / %d", scannedCount, total));
            discoverTaskMapper.updateById(update);
        } catch (Exception e) {
            log.warn("更新扫描进度失败：taskId={}", taskId, e);
        }
    }

    private void markDone(Long taskId, int foundCount, int scannedCount, int total, boolean completed) {
        DiscoverTask update = new DiscoverTask();
        update.setId(taskId);
        update.setFinishedAt(LocalDateTime.now());
        update.setFoundCount(foundCount);
        update.setScannedIpCount(scannedCount);
        if (completed) {
            update.setStatus("DONE");
            update.setMessage("扫描完成，发现设备：" + foundCount);
        } else {
            update.setStatus("TIMEOUT");
            update.setMessage(String.format("扫描超时，已扫描 %d / %d，发现设备：%d", scannedCount, total, foundCount));
        }
        discoverTaskMapper.updateById(update);
    }

    private void failTask(DiscoverTask task, String message) {
        task.setStatus("FAILED");
        task.setMessage(message);
        task.setFinishedAt(LocalDateTime.now());
        discoverTaskMapper.updateById(task);
    }

    /**
     * 保存发现的设备，IP+端口重复时跳过
     */
    private void saveDiscoveredDevice(String ip, int port, HealthResp resp) {
        String name = ip + ":" + port;
        if (resp.getData() != null && resp.getData().getHostIp() != null) {
            name = resp.getData().getHostIp() + ":" + port;
        }

        Device device = new Device();
        device.setName(name);
        device.setIp(ip);
        device.setPort(port);
        device.setMode(DeviceMode.BRIDGE.name());
        device.setStatus(DeviceStatus.UNKNOWN.name());
        device.setSource("DISCOVERED");
        try {
            deviceMapper.insert(device);
            log.info("发现新设备：{}:{}, name={}", ip, port, name);
        } catch (DuplicateKeyException e) {
            log.debug("设备已存在，跳过：{}:{}", ip, port);
        }
    }
}
