package bob.myxos.collector.collector;

import bob.myxos.common.enums.DeviceMode;
import bob.myxos.common.enums.DeviceStatus;
import bob.myxos.common.util.IpUtils;
import bob.myxos.domain.entity.ActionLog;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.DiscoverTask;
import bob.myxos.domain.mapper.ActionLogMapper;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.domain.mapper.DiscoverTaskMapper;
import bob.myxos.mytos.MytosClient;
import bob.myxos.mytos.MytosClientFactory;
import bob.myxos.mytos.dto.HealthResp;
import bob.myxos.collector.service.MetricPersistService;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
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
    private final MetricPersistService metricPersistService;
    private final ObjectMapper objectMapper;
    private final ActionLogMapper actionLogMapper;

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
        List<DiscoveryIpResult> ipResults = Collections.synchronizedList(new ArrayList<>());
        AtomicInteger found = new AtomicInteger(0);
        AtomicInteger duplicate = new AtomicInteger(0);
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
                            SaveResult saveResult = saveDiscoveredDevice(ip, p, resp);
                            if (saveResult.added) {
                                found.incrementAndGet();
                                ipResults.add(new DiscoveryIpResult(ip, p, "ADDED", null));
                            } else {
                                duplicate.incrementAndGet();
                                ipResults.add(new DiscoveryIpResult(ip, p, "DUPLICATE", saveResult.reason));
                            }
                        } else {
                            ipResults.add(new DiscoveryIpResult(ip, p, "IGNORED", "健康检查未通过"));
                        }
                    } catch (Exception e) {
                        ipResults.add(new DiscoveryIpResult(ip, p, "ERROR", e.getMessage()));
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

        markDone(taskId, found.get(), duplicate.get(), scanned.get(), total, completed, ipResults);
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

    private void markDone(Long taskId, int foundCount, int duplicateCount, int scannedCount, int total,
                          boolean completed, List<DiscoveryIpResult> ipResults) {
        DiscoverTask update = new DiscoverTask();
        update.setId(taskId);
        update.setFinishedAt(LocalDateTime.now());
        update.setFoundCount(foundCount);
        update.setScannedIpCount(scannedCount);
        try {
            DiscoverTaskDetail detail = new DiscoverTaskDetail();
            detail.setAddedCount(foundCount);
            detail.setDuplicateCount(duplicateCount);
            detail.setFailedCount(Math.max(0, total - scannedCount));
            detail.setIpResults(ipResults);
            update.setDetail(objectMapper.writeValueAsString(detail));
        } catch (Exception e) {
            log.warn("序列化发现详情失败：taskId={}", taskId, e);
        }
        if (completed) {
            update.setStatus("DONE");
            update.setMessage("扫描完成，新增 " + foundCount + " 台，重复 " + duplicateCount + " 台");
        } else {
            update.setStatus("TIMEOUT");
            update.setMessage(String.format("扫描超时，已扫描 %d / %d，新增 %d 台", scannedCount, total, foundCount));
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
    private SaveResult saveDiscoveredDevice(String ip, int port, HealthResp resp) {
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
            writeActionLog(device, "网段发现添加设备：" + name + "(" + ip + ":" + port + ")");
            collectImmediately(device);
            return new SaveResult(true, null);
        } catch (DuplicateKeyException e) {
            log.debug("设备已存在，跳过：{}:{}", ip, port);
            return new SaveResult(false, "设备已存在");
        }
    }

    private void writeActionLog(Device device, String message) {
        ActionLog log = new ActionLog();
        log.setDeviceId(device.getId());
        log.setActionType("SYSTEM");
        log.setLogLevel("INFO");
        log.setMessage(message);
        log.setCreatedAt(LocalDateTime.now());
        actionLogMapper.insert(log);
    }

    /**
     * 发现设备后立即执行一次指标采集，让用户能尽快在详情页看到数据
     */
    private void collectImmediately(Device device) {
        try {
            MetricCollector collector = new MetricCollector(device, clientFactory,
                    snapshots -> metricPersistService.saveBatchSnapshots(snapshots), deviceMapper);
            collector.run();
            log.info("发现设备立即采集完成：{}:{}", device.getIp(), device.getPort());
        } catch (Exception e) {
            log.warn("发现设备立即采集失败：{}:{}", device.getIp(), device.getPort(), e);
        }
    }

    private static class SaveResult {
        final boolean added;
        final String reason;
        SaveResult(boolean added, String reason) {
            this.added = added;
            this.reason = reason;
        }
    }

    public static class DiscoveryIpResult {
        public String ip;
        public Integer port;
        public String result;
        public String message;
        public DiscoveryIpResult(String ip, Integer port, String result, String message) {
            this.ip = ip;
            this.port = port;
            this.result = result;
            this.message = message;
        }
    }

    public static class DiscoverTaskDetail {
        private int addedCount;
        private int duplicateCount;
        private int failedCount;
        private List<DiscoveryIpResult> ipResults;
        public int getAddedCount() { return addedCount; }
        public void setAddedCount(int addedCount) { this.addedCount = addedCount; }
        public int getDuplicateCount() { return duplicateCount; }
        public void setDuplicateCount(int duplicateCount) { this.duplicateCount = duplicateCount; }
        public int getFailedCount() { return failedCount; }
        public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
        public List<DiscoveryIpResult> getIpResults() { return ipResults; }
        public void setIpResults(List<DiscoveryIpResult> ipResults) { this.ipResults = ipResults; }
    }
}
