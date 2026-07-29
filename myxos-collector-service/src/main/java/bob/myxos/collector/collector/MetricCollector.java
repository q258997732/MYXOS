package bob.myxos.collector.collector;

import bob.myxos.common.enums.DeviceStatus;
import bob.myxos.common.enums.MetricType;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.MetricSnapshot;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.mytos.MytosClient;
import bob.myxos.mytos.MytosClientFactory;
import bob.myxos.mytos.dto.InfoResp;
import bob.myxos.mytos.dto.VersionResp;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 单设备指标采集任务
 * 负责调用 MYTOS 客户端采集一台设备的指标并更新设备状态
 * <p>
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
        try {
            InfoResp info = client.info();
            VersionResp versionResp = client.queryVersion();
            if (info.getCode() != null && info.getCode() == 200) {
                status = DeviceStatus.ONLINE;
                version = versionResp.getMsg();
                MetricSnapshot snapshot = new MetricSnapshot();
                snapshot.setDeviceId(device.getId());
                snapshot.setMetricType(MetricType.VERSION.name());
                snapshot.setMetricValue(versionResp.getMsg());
                snapshot.setMetricNum(parseVersionNumber(versionResp.getMsg()));
                snapshot.setCollectedAt(LocalDateTime.now());
                snapshots.add(snapshot);
            }
        } catch (Exception e) {
            log.warn("采集设备失败 {}:{}", device.getIp(), device.getPort(), e);
            status = DeviceStatus.OFFLINE;
        }

        // 更新设备状态、版本与最近在线时间
        Device update = new Device();
        update.setId(device.getId());
        update.setStatus(status.name());
        update.setVersion(version);
        update.setLastSeenAt(LocalDateTime.now());
        deviceMapper.updateById(update);

        if (!snapshots.isEmpty()) {
            persistCallback.persist(snapshots);
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
