package bob.myxos.collector.collector;

import bob.myxos.common.enums.DeviceStatus;
import bob.myxos.common.enums.MetricType;
import bob.myxos.common.util.AndroidStatusParser;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.MetricSnapshot;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.mytos.MytosClient;
import bob.myxos.mytos.MytosClientFactory;
import bob.myxos.mytos.dto.AndroidListResp;
import bob.myxos.mytos.dto.BootStatusResp;
import bob.myxos.mytos.dto.HealthResp;
import bob.myxos.mytos.dto.HostSystemInfoResp;
import bob.myxos.mytos.dto.HostVerResp;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单设备指标采集任务
 * <p>
 * 负责调用 MYTOS 客户端采集一台设备的主机指标、安卓实例状态，并更新设备状态。
 * 非 Spring Bean，由 {@link MetricCollectJob} 在采集调度时按需创建。
 */
@Slf4j
@RequiredArgsConstructor
public class MetricCollector implements Runnable {

    /** 目标设备 */
    private final Device device;

    /** MYTOS 客户端工厂 */
    private final MytosClientFactory clientFactory;

    /** 指标持久化回调 */
    private final MetricPersistCallback persistCallback;

    /** 设备 Mapper（用于更新设备状态） */
    private final DeviceMapper deviceMapper;

    /**
     * 指标持久化回调接口
     * 由调用方注入具体的批量保存实现
     */
    public interface MetricPersistCallback {
        /**
         * 持久化指标快照列表
         *
         * @param snapshots 待保存的指标快照
         */
        void persist(List<MetricSnapshot> snapshots);
    }

    @Override
    public void run() {
        MytosClient client = clientFactory.create(device.getIp(), device.getPort());
        List<MetricSnapshot> snapshots = new ArrayList<>();
        DeviceStatus status = DeviceStatus.OFFLINE;
        String version = null;
        LocalDateTime collectedAt = LocalDateTime.now();

        try {
            HealthResp health = client.healthcheck(device.getIp());
            if (health.getCode() != null && health.getCode() == 200) {
                status = DeviceStatus.ONLINE;
                version = fetchHostVersion(client, device.getIp());
                snapshots.add(buildVersionSnapshot(version, collectedAt));
                snapshots.addAll(collectSystemMetrics(client, device.getIp(), collectedAt));
                snapshots.addAll(collectAndroidStatuses(client, device.getIp(), collectedAt));
            }
        } catch (Exception e) {
            log.warn("采集设备失败 {}:{}", device.getIp(), device.getPort(), e);
            status = DeviceStatus.OFFLINE;
        }

        updateDeviceStatus(status, version);
        persistSnapshots(snapshots);
    }

    private String fetchHostVersion(MytosClient client, String ip) {
        try {
            HostVerResp verResp = client.getHostVer(ip);
            String version = verResp.getData();
            if (version == null || version.isEmpty()) {
                version = verResp.getMsg();
            }
            return version;
        } catch (Exception e) {
            log.debug("获取主机版本失败：{}", ip, e);
            return null;
        }
    }

    private List<MetricSnapshot> collectSystemMetrics(MytosClient client, String ip, LocalDateTime collectedAt) {
        try {
            HostSystemInfoResp resp = client.getSystemInfo(ip);
            if (resp == null || resp.getData() == null) {
                return Collections.emptyList();
            }
            return SystemMetricExtractor.extract(device.getId(), resp.getData(), collectedAt);
        } catch (Exception e) {
            log.debug("采集系统指标失败：{}", ip, e);
            return Collections.emptyList();
        }
    }

    private List<MetricSnapshot> collectAndroidStatuses(MytosClient client, String ip, LocalDateTime collectedAt) {
        try {
            AndroidListResp listResp = client.listAndroid(ip);
            if (listResp == null || listResp.getData() == null || !listResp.getData().isArray()) {
                return Collections.emptyList();
            }
            List<MetricSnapshot> snapshots = new ArrayList<>();
            for (JsonNode node : listResp.getData()) {
                String name = extractAndroidName(node);
                if (name == null || name.isEmpty()) {
                    continue;
                }
                String status = fetchAndroidStatus(client, ip, name);
                snapshots.add(buildAndroidStatusSnapshot(name, status, collectedAt));
            }
            return snapshots;
        } catch (Exception e) {
            log.debug("采集安卓实例状态失败：{}", ip, e);
            return Collections.emptyList();
        }
    }

    private String extractAndroidName(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        if (node.isTextual()) {
            return node.asText();
        }
        if (node.isObject()) {
            JsonNode nameNode = node.get("name");
            return nameNode != null && !nameNode.isNull() ? nameNode.asText() : null;
        }
        return null;
    }

    private String fetchAndroidStatus(MytosClient client, String ip, String name) {
        try {
            BootStatusResp resp = client.getAndroidBootStatus(ip, name);
            if (resp == null || resp.getData() == null) {
                return AndroidStatusParser.UNKNOWN;
            }
            String raw = resp.getData().isTextual() ? resp.getData().asText() : resp.getData().toString();
            return AndroidStatusParser.parse(raw);
        } catch (Exception e) {
            log.debug("获取安卓实例状态失败：{}/{}", ip, name, e);
            return AndroidStatusParser.UNKNOWN;
        }
    }

    private MetricSnapshot buildVersionSnapshot(String version, LocalDateTime collectedAt) {
        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.setDeviceId(device.getId());
        snapshot.setMetricType(MetricType.VERSION.name());
        snapshot.setMetricValue(version == null ? "" : version);
        snapshot.setMetricNum(parseVersionNumber(version));
        snapshot.setCollectedAt(collectedAt);
        return snapshot;
    }

    private MetricSnapshot buildAndroidStatusSnapshot(String name, String status, LocalDateTime collectedAt) {
        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.setDeviceId(device.getId());
        snapshot.setMetricType(MetricType.ANDROID_STATUS.name());
        snapshot.setMetricValue(status);
        snapshot.setCollectedAt(collectedAt);
        snapshot.setExtra(String.format("{\"name\":\"%s\"}", name.replace("\\", "\\\\").replace("\"", "\\\"")));
        return snapshot;
    }

    private void updateDeviceStatus(DeviceStatus status, String version) {
        Device update = new Device();
        update.setId(device.getId());
        update.setStatus(status.name());
        update.setVersion(version);
        update.setLastSeenAt(LocalDateTime.now());
        deviceMapper.updateById(update);
    }

    private void persistSnapshots(List<MetricSnapshot> snapshots) {
        if (snapshots.isEmpty()) {
            return;
        }
        try {
            persistCallback.persist(snapshots);
        } catch (Exception e) {
            log.warn("持久化指标失败：deviceId={}", device.getId(), e);
        }
    }

    /**
     * 解析版本号字符串中的数值部分
     * 提取首个数字段作为 BigDecimal 返回，无法解析时返回 null
     *
     * @param version 版本号字符串
     * @return 数值形式的版本号，可能为 null
     */
    private BigDecimal parseVersionNumber(String version) {
        if (version == null || version.isEmpty()) {
            return null;
        }
        try {
            return new BigDecimal(version.replaceAll("[^0-9.]", "").split("\\.")[0]);
        } catch (Exception e) {
            return null;
        }
    }
}
