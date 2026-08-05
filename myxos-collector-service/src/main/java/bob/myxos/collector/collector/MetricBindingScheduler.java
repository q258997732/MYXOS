package bob.myxos.collector.collector;

import bob.myxos.common.util.AndroidStatusParser;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.MetricBinding;
import bob.myxos.domain.entity.MetricSnapshot;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.domain.mapper.MetricBindingMapper;
import bob.myxos.domain.mapper.MetricSnapshotMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** 按绑定到期时间分发受控指标采集任务。 */
@Component
public class MetricBindingScheduler {

    /** 连续前向批次数达到该值后主动回绕，避免持续新增的高 ID 饿死低 ID。 */
    private static final int MAX_FORWARD_BATCHES = 1;

    /** 供有界线程池拒绝策略回调的任务契约。 */
    public interface RejectedTask {
        void rejected();
    }

    private final MetricBindingMapper bindingMapper;
    private final DeviceMapper deviceMapper;
    private final MetricSnapshotMapper snapshotMapper;
    private final BoundMetricCollector boundMetricCollector;
    private final ThreadPoolTaskExecutor executor;
    private final int batchSize;
    private final int backoffSec;
    private final ConcurrentHashMap<String, Boolean> inFlight = new ConcurrentHashMap<String, Boolean>();
    private volatile long dispatchCursor;
    private int consecutiveForwardBatches;

    public MetricBindingScheduler(MetricBindingMapper bindingMapper, DeviceMapper deviceMapper,
                                  MetricSnapshotMapper snapshotMapper, BoundMetricCollector boundMetricCollector,
                                  @Qualifier("metricCollectExecutor") ThreadPoolTaskExecutor metricCollectExecutor,
                                  @Value("${myxos.collector.metric-dispatch-batch-size:100}") int batchSize,
                                  @Value("${myxos.collector.metric-dispatch-backoff-sec:5}") int backoffSec) {
        this.bindingMapper = bindingMapper;
        this.deviceMapper = deviceMapper;
        this.snapshotMapper = snapshotMapper;
        this.boundMetricCollector = boundMetricCollector;
        this.executor = metricCollectExecutor;
        this.batchSize = batchSize;
        this.backoffSec = backoffSec;
    }

    @Scheduled(fixedDelay = 5000)
    public void schedule() {
        LocalDateTime now = LocalDateTime.now();
        List<MetricBinding> bindings;
        if (consecutiveForwardBatches >= MAX_FORWARD_BATCHES) {
            bindings = bindingMapper.selectDueFromStart(now, dispatchCursor, batchSize);
            consecutiveForwardBatches = 0;
        } else {
            bindings = bindingMapper.selectDueAfter(now, dispatchCursor, batchSize);
            if (bindings.isEmpty()) {
                bindings = bindingMapper.selectDueFromStart(now, dispatchCursor, batchSize);
                consecutiveForwardBatches = 0;
            } else {
                consecutiveForwardBatches++;
            }
        }
        if (!bindings.isEmpty()) {
            dispatchCursor = bindings.get(bindings.size() - 1).getId();
        }
        dispatch(bindings, resolveStatuses(bindings));
    }

    public void dispatch(List<MetricBinding> bindings, Map<String, String> statuses) {
        LocalDateTime now = LocalDateTime.now();
        for (MetricBinding binding : bindings) {
            String targetKey = targetKey(binding);
            if (!isRunnable(binding, statuses.get(targetKey))) {
                skip(binding, now, backoffSec);
                continue;
            }
            if (inFlight.putIfAbsent(targetKey, Boolean.TRUE) != null) {
                continue;
            }
            try {
                executor.execute(new DispatchTask(binding, targetKey));
            } catch (RuntimeException e) {
                inFlight.remove(targetKey);
                skip(binding, now, backoffSec);
            }
        }
    }

    public void markTargetRunning(String targetKey) {
        inFlight.put(targetKey, Boolean.TRUE);
    }

    private void execute(MetricBinding binding, String targetKey) {
        try {
            Device device = deviceMapper.selectById(binding.getDeviceId());
            if (device == null) {
                throw new IllegalStateException("设备不存在");
            }
            MetricExecutionResult result = boundMetricCollector.collect(device, binding);
            snapshotMapper.insert(result.getSnapshot());
            LocalDateTime completedAt = LocalDateTime.now();
            bindingMapper.markCollected(binding.getId(), completedAt, nextTime(binding, completedAt));
        } catch (Exception e) {
            skip(binding, LocalDateTime.now(), backoffSec);
        } finally {
            inFlight.remove(targetKey);
        }
    }

    private final class DispatchTask implements Runnable, RejectedTask {
        private final MetricBinding binding;
        private final String targetKey;

        private DispatchTask(MetricBinding binding, String targetKey) {
            this.binding = binding;
            this.targetKey = targetKey;
        }

        @Override
        public void run() {
            execute(binding, targetKey);
        }

        @Override
        public void rejected() {
            inFlight.remove(targetKey);
            skip(binding, LocalDateTime.now(), backoffSec);
        }
    }

    private Map<String, String> resolveStatuses(List<MetricBinding> bindings) {
        Map<String, String> statuses = new HashMap<String, String>();
        for (MetricBinding binding : bindings) {
            Device device = deviceMapper.selectById(binding.getDeviceId());
            if (device == null) continue;
            if ("HOST".equals(binding.getTargetType())) {
                statuses.put(targetKey(binding), device.getStatus());
            } else {
                MetricSnapshot snapshot = snapshotMapper.selectLatestByDeviceMetricCodeAndAndroidName(
                        binding.getDeviceId(), "ANDROID_STATUS", binding.getAndroidName());
                statuses.put(targetKey(binding), snapshot == null ? AndroidStatusParser.UNKNOWN : snapshot.getMetricValue());
            }
        }
        return statuses;
    }

    private boolean isRunnable(MetricBinding binding, String status) {
        return "HOST".equals(binding.getTargetType()) ? "ONLINE".equals(status)
                : AndroidStatusParser.RUNNING.equals(status);
    }

    private void skip(MetricBinding binding, LocalDateTime completedAt, int seconds) {
        bindingMapper.markSkipped(binding.getId(), completedAt, completedAt.plusSeconds(seconds));
    }

    private LocalDateTime nextTime(MetricBinding binding, LocalDateTime completedAt) {
        int interval = binding.getIntervalSec() == null ? backoffSec : binding.getIntervalSec();
        return completedAt.plusSeconds(interval);
    }

    private String targetKey(MetricBinding binding) {
        return "HOST".equals(binding.getTargetType()) ? "HOST:" + binding.getDeviceId()
                : "ANDROID:" + binding.getDeviceId() + ":" + binding.getAndroidName();
    }
}
