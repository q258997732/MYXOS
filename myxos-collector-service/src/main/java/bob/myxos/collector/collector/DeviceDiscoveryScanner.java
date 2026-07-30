package bob.myxos.collector.collector;

import bob.myxos.common.enums.DeviceMode;
import bob.myxos.common.enums.DeviceStatus;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.DiscoverTask;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.domain.mapper.DiscoverTaskMapper;
import bob.myxos.mytos.MytosClient;
import bob.myxos.mytos.MytosClientFactory;
import bob.myxos.mytos.dto.InfoResp;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * CIDR 网段设备发现扫描器
 * <p>
 * 对指定 CIDR 网段和端口范围并发探测，调用 MYTOS /info 接口识别设备并写入 device 表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceDiscoveryScanner {

    /** 扫描线程数 */
    private static final int SCAN_THREADS = 16;
    /** 单次扫描超时上限（秒） */
    private static final int SCAN_TIMEOUT_SECONDS = 600;

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
            ips = bob.myxos.common.util.IpUtils.expandCidr(task.getCidr());
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

        markRunning(task);

        ExecutorService executor = Executors.newFixedThreadPool(SCAN_THREADS);
        AtomicInteger found = new AtomicInteger(0);
        CountDownLatch latch = new CountDownLatch(total);

        for (String ip : ips) {
            for (int port = task.getPortFrom(); port <= task.getPortTo(); port++) {
                final int p = port;
                executor.execute(() -> {
                    try {
                        MytosClient client = clientFactory.create(ip, p);
                        InfoResp resp = client.info();
                        if (resp.getCode() != null && resp.getCode() == 200) {
                            saveDiscoveredDevice(ip, p, resp);
                            found.incrementAndGet();
                        }
                    } catch (Exception ignored) {
                        // 探测失败属于正常现象，不记录堆栈避免日志刷屏
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        try {
            if (!latch.await(SCAN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                log.warn("网段扫描任务超时：taskId={}", task.getId());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.warn("网段扫描任务被中断：taskId={}", task.getId());
        } finally {
            executor.shutdownNow();
        }

        markDone(task, found.get());
    }

    private void markRunning(DiscoverTask task) {
        task.setStatus("RUNNING");
        task.setStartedAt(LocalDateTime.now());
        discoverTaskMapper.updateById(task);
    }

    private void markDone(DiscoverTask task, int foundCount) {
        task.setStatus("DONE");
        task.setFinishedAt(LocalDateTime.now());
        task.setFoundCount(foundCount);
        task.setMessage("扫描完成，发现设备：" + foundCount);
        discoverTaskMapper.updateById(task);
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
    private void saveDiscoveredDevice(String ip, int port, InfoResp resp) {
        Long count = deviceMapper.selectCount(
                new LambdaQueryWrapper<Device>()
                        .eq(Device::getIp, ip)
                        .eq(Device::getPort, port)
                        .eq(Device::getDeleted, 0));
        if (count != null && count > 0) {
            return;
        }

        String name = ip + ":" + port;
        if (resp.getData() != null && resp.getData().getName() != null) {
            name = resp.getData().getName();
        }

        Device device = new Device();
        device.setName(name);
        device.setIp(ip);
        device.setPort(port);
        device.setMode(DeviceMode.BRIDGE.name());
        device.setStatus(DeviceStatus.UNKNOWN.name());
        device.setSource("DISCOVERED");
        deviceMapper.insert(device);
        log.info("发现新设备：{}:{}, name={}", ip, port, name);
    }
}
