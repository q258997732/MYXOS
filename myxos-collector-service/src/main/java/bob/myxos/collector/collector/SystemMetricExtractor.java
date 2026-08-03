package bob.myxos.collector.collector;

import bob.myxos.common.enums.MetricType;
import bob.myxos.domain.entity.MetricSnapshot;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * 系统信息指标提取器
 * <p>
 * 从 MYTOS /host_api/v1/get_systeminfo 返回的动态 JSON 中提取常见指标，
 * 生成 {@link MetricSnapshot} 列表。
 */
@Slf4j
public final class SystemMetricExtractor {

    private SystemMetricExtractor() {
    }

    /**
     * 从系统信息 JSON 中提取指标快照
     *
     * @param deviceId     设备 ID
     * @param systemInfo   系统信息 JSON 节点
     * @param collectedAt  采集时间
     * @return 指标快照列表
     */
    public static List<MetricSnapshot> extract(Long deviceId, JsonNode systemInfo, LocalDateTime collectedAt) {
        List<MetricSnapshot> snapshots = new ArrayList<>();
        if (systemInfo == null || systemInfo.isNull()) {
            return snapshots;
        }

        Map<MetricType, String> candidates = MetricCandidate.candidates();
        for (Map.Entry<MetricType, String> entry : candidates.entrySet()) {
            JsonNode valueNode = findValue(systemInfo, entry.getValue());
            if (valueNode == null || valueNode.isNull() || valueNode.isMissingNode()) {
                continue;
            }
            String text = valueNode.isTextual() ? valueNode.asText() : valueNode.toString();
            MetricSnapshot snapshot = new MetricSnapshot();
            snapshot.setDeviceId(deviceId);
            snapshot.setMetricType(entry.getKey().name());
            snapshot.setMetricValue(text);
            snapshot.setMetricNum(parseNumber(text));
            snapshot.setCollectedAt(collectedAt);
            snapshots.add(snapshot);
        }

        // 递归提取所有可能的热点字段（兜底）
        extractFromNode(systemInfo, deviceId, collectedAt, snapshots);
        return deduplicateByType(snapshots);
    }

    /**
     * 在 JSON 节点中查找指定键的值，支持忽略大小写与下划线/驼峰转换
     */
    private static JsonNode findValue(JsonNode root, String key) {
        if (root == null || !root.isObject()) {
            return null;
        }
        String normalized = normalize(key);
        Iterator<Map.Entry<String, JsonNode>> fields = root.fields();
        while (fields.hasNext()) {
            Map.Entry<String, JsonNode> field = fields.next();
            if (normalize(field.getKey()).equals(normalized)) {
                return field.getValue();
            }
        }
        return null;
    }

    private static String normalize(String key) {
        return key == null ? "" : key.toLowerCase().replace("_", "").replace("-", "");
    }

    /**
     * 递归遍历 JSON 节点，当遇到已知指标键时生成快照
     */
    private static void extractFromNode(JsonNode node, Long deviceId, LocalDateTime collectedAt,
                                        List<MetricSnapshot> snapshots) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            Iterator<Map.Entry<String, JsonNode>> fields = node.fields();
            while (fields.hasNext()) {
                Map.Entry<String, JsonNode> field = fields.next();
                MetricType type = resolveTypeByKey(field.getKey());
                if (type != null && isLeaf(field.getValue())) {
                    addSnapshot(snapshots, deviceId, type, field.getValue(), collectedAt);
                } else {
                    extractFromNode(field.getValue(), deviceId, collectedAt, snapshots);
                }
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                extractFromNode(child, deviceId, collectedAt, snapshots);
            }
        }
    }

    private static boolean isLeaf(JsonNode node) {
        return node != null && (node.isTextual() || node.isNumber() || node.isBoolean());
    }

    private static void addSnapshot(List<MetricSnapshot> snapshots, Long deviceId, MetricType type,
                                    JsonNode valueNode, LocalDateTime collectedAt) {
        String text = valueNode.isTextual() ? valueNode.asText() : valueNode.toString();
        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.setDeviceId(deviceId);
        snapshot.setMetricType(type.name());
        snapshot.setMetricValue(text);
        snapshot.setMetricNum(parseNumber(text));
        snapshot.setCollectedAt(collectedAt);
        snapshots.add(snapshot);
    }

    private static MetricType resolveTypeByKey(String key) {
        if (key == null) {
            return null;
        }
        String normalized = normalize(key);
        if (normalized.contains("cpu")) {
            return MetricType.CPU;
        }
        if (normalized.contains("mem") || normalized.contains("ram") || normalized.contains("memory")) {
            return MetricType.MEM;
        }
        if (normalized.contains("disk")) {
            return MetricType.DISK;
        }
        if (normalized.contains("tx") || normalized.contains("upload") || normalized.contains("sent")) {
            return MetricType.NET_TX;
        }
        if (normalized.contains("rx") || normalized.contains("download") || normalized.contains("recv")) {
            return MetricType.NET_RX;
        }
        if (normalized.contains("temp")) {
            return MetricType.TEMP;
        }
        if (normalized.contains("uptime")) {
            return MetricType.UPTIME;
        }
        return null;
    }

    private static BigDecimal parseNumber(String text) {
        if (text == null || text.isEmpty()) {
            return null;
        }
        try {
            String number = text.replaceAll("[^0-9\\-\\.]", "").trim();
            if (number.isEmpty()) {
                return null;
            }
            return new BigDecimal(number);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<MetricSnapshot> deduplicateByType(List<MetricSnapshot> snapshots) {
        List<MetricSnapshot> result = new ArrayList<>();
        for (MetricSnapshot snapshot : snapshots) {
            boolean exists = false;
            for (MetricSnapshot existing : result) {
                if (existing.getMetricType().equals(snapshot.getMetricType())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                result.add(snapshot);
            }
        }
        return result;
    }

    /**
     * 指标候选键配置
     */
    private static final class MetricCandidate {

        private static final Map<MetricType, String> CANDIDATES = new HashMap<>();

        static {
            CANDIDATES.put(MetricType.CPU, "cpu_usage");
            CANDIDATES.put(MetricType.MEM, "mem_usage");
            CANDIDATES.put(MetricType.DISK, "disk_usage");
            CANDIDATES.put(MetricType.NET_RX, "net_rx");
            CANDIDATES.put(MetricType.NET_TX, "net_tx");
            CANDIDATES.put(MetricType.TEMP, "temperature");
            CANDIDATES.put(MetricType.UPTIME, "uptime");
        }

        private static Map<MetricType, String> candidates() {
            return CANDIDATES;
        }
    }
}
