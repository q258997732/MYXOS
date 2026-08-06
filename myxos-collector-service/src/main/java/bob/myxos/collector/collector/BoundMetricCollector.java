package bob.myxos.collector.collector;

import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.MetricBinding;
import bob.myxos.domain.entity.MetricSnapshot;
import bob.myxos.main.metric.AndroidMetricParser;
import bob.myxos.main.metric.MetricDefinition;
import bob.myxos.main.metric.MetricDefinitionRegistry;
import bob.myxos.mytos.MytosClient;
import bob.myxos.mytos.MytosClientFactory;
import bob.myxos.mytos.dto.ShellResp;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Optional;

/** 使用受控指标目录执行单条绑定采集。 */
@Component
public class BoundMetricCollector {

    private final MytosClientFactory clientFactory;
    private final AndroidMetricParser parser = new AndroidMetricParser();

    public BoundMetricCollector(MytosClientFactory clientFactory) {
        this.clientFactory = clientFactory;
    }

    public MetricExecutionResult collect(Device device, MetricBinding binding) {
        LocalDateTime collectedAt = LocalDateTime.now();
        MetricSnapshot snapshot = baseSnapshot(binding, collectedAt);
        try {
            MytosClient client = clientFactory.create(device.getIp(), device.getPort());
            if ("HOST".equals(binding.getTargetType())) {
                collectHostMetric(client, device, binding, snapshot, collectedAt);
            } else {
                collectAndroidMetric(client, device, binding, snapshot);
            }
        } catch (Exception e) {
            unknown(snapshot, e.getMessage());
        }
        return MetricExecutionResult.of(snapshot);
    }

    private void collectHostMetric(MytosClient client, Device device, MetricBinding binding,
                                   MetricSnapshot snapshot, LocalDateTime collectedAt) {
        for (MetricSnapshot candidate : SystemMetricExtractor.extract(device.getId(),
                client.getSystemInfo(device.getIp()).getData(), collectedAt)) {
            if (binding.getMetricCode().equals(candidate.getMetricCode())
                    || binding.getMetricCode().equals(candidate.getMetricType())) {
                snapshot.setMetricValue(candidate.getMetricValue());
                snapshot.setMetricNum(candidate.getMetricNum());
                return;
            }
        }
        unknown(snapshot, "主机系统信息中没有该指标");
    }

    private void collectAndroidMetric(MytosClient client, Device device, MetricBinding binding,
                                      MetricSnapshot snapshot) {
        Optional<MetricDefinition> definition = MetricDefinitionRegistry.findByCode(binding.getMetricCode());
        if (!definition.isPresent() || definition.get().getCommandKey() == null) {
            unknown(snapshot, "指标未在受控目录中定义");
            return;
        }
        Optional<String> command = MetricDefinitionRegistry.findReadOnlyAdbCommand(definition.get().getCommandKey());
        if (!command.isPresent()) {
            unknown(snapshot, "指标命令未获授权");
            return;
        }
        ShellResp response = client.shell(device.getIp(), binding.getAndroidName(), command.get());
        String output = response.getMsg();
        snapshot.setExtra("{\"shellCode\":" + shellCode(response) + "}");
        if (response.getCode() == null || response.getCode() != 200) {
            unknown(snapshot, "shell 响应失败");
            return;
        }
        if (shellCode(response) != 0) {
            unknown(snapshot, "shell 执行失败");
            return;
        }
        if (output == null) {
            unknown(snapshot, "shell 未返回输出");
            return;
        }
        if (!applyAndroidValue(binding.getMetricCode(), binding.getAppPackage(), output, snapshot)) {
            unknown(snapshot, "指标输出无法解析");
        }
    }

    private boolean applyAndroidValue(String code, String appPackage, String output, MetricSnapshot snapshot) {
        if (MetricDefinitionRegistry.MEM_TOTAL_KB.equals(code)) {
            return applyLong(parser.parseMemTotalKb(output), snapshot);
        }
        if (MetricDefinitionRegistry.MEM_AVAILABLE_KB.equals(code)) {
            return applyLong(parser.parseMemAvailableKb(output), snapshot);
        }
        if (MetricDefinitionRegistry.CPU_USAGE_PERCENT.equals(code)) {
            Optional<BigDecimal> value = parser.parseCpuUsagePercent(output);
            if (!value.isPresent()) return false;
            snapshot.setMetricNum(value.get()); snapshot.setMetricValue(value.get().toPlainString()); return true;
        }
        if (MetricDefinitionRegistry.TASK_TOTAL.equals(code)) {
            Optional<Integer> value = parser.parseTaskTotal(output);
            if (!value.isPresent()) return false;
            snapshot.setMetricNum(BigDecimal.valueOf(value.get())); snapshot.setMetricValue(value.get().toString()); return true;
        }
        if (MetricDefinitionRegistry.APP_PROCESS_STATE.equals(code)) {
            Optional<AndroidMetricParser.AppProcessState> state = parser.parseAppProcessState(output, appPackage);
            if (!state.isPresent()) return false;
            snapshot.setMetricValue(state.get().getStatus());
            snapshot.setMetricNum(null);
            snapshot.setExtra("{\"shellCode\":0,\"pid\":" + nullableNumber(state.get().getPid())
                    + ",\"rawState\":" + jsonString(state.get().getRawState()) + "}");
            return true;
        }
        snapshot.setMetricValue(output.trim());
        return true;
    }

    private boolean applyLong(Optional<Long> value, MetricSnapshot snapshot) {
        if (!value.isPresent()) return false;
        snapshot.setMetricNum(BigDecimal.valueOf(value.get()));
        snapshot.setMetricValue(value.get().toString());
        return true;
    }

    private MetricSnapshot baseSnapshot(MetricBinding binding, LocalDateTime collectedAt) {
        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.setDeviceId(binding.getDeviceId());
        snapshot.setMetricCode(binding.getMetricCode());
        snapshot.setMetricType(binding.getMetricCode());
        snapshot.setTargetType(binding.getTargetType());
        snapshot.setAndroidName(binding.getAndroidName() == null ? "" : binding.getAndroidName());
        snapshot.setAppPackage(binding.getAppPackage() == null ? "" : binding.getAppPackage());
        snapshot.setCollectedAt(collectedAt);
        return snapshot;
    }

    private int shellCode(ShellResp response) {
        return response.getData() != null && response.getData().has("shell_code")
                ? response.getData().path("shell_code").asInt(-1) : -1;
    }

    private void unknown(MetricSnapshot snapshot, String error) {
        snapshot.setMetricValue("UNKNOWN");
        snapshot.setMetricNum(null);
        snapshot.setExtra("{\"error\":\"" + escape(error) + "\"}");
    }

    private String escape(String value) {
        return value == null ? "未知错误" : value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String nullableNumber(Integer value) {
        return value == null ? "null" : value.toString();
    }

    private String jsonString(String value) {
        return value == null ? "null" : "\"" + escape(value) + "\"";
    }
}
