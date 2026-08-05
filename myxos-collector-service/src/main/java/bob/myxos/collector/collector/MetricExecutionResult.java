package bob.myxos.collector.collector;

import bob.myxos.domain.entity.MetricSnapshot;

/** 单个绑定指标的执行结果。 */
public final class MetricExecutionResult {

    private final MetricSnapshot snapshot;

    private MetricExecutionResult(MetricSnapshot snapshot) {
        this.snapshot = snapshot;
    }

    public static MetricExecutionResult of(MetricSnapshot snapshot) {
        return new MetricExecutionResult(snapshot);
    }

    public MetricSnapshot getSnapshot() {
        return snapshot;
    }
}
