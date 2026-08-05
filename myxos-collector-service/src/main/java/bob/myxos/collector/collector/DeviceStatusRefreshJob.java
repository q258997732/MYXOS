package bob.myxos.collector.collector;

import org.springframework.stereotype.Component;

/**
 * 刷新设备健康和安卓实例状态。
 *
 * 指标采集由 {@link MetricBindingScheduler} 按绑定到期时间负责，本作业不再承担指标调度职责。
 */
@Component
public class DeviceStatusRefreshJob {

    private final MetricCollectJob metricCollectJob;

    public DeviceStatusRefreshJob(MetricCollectJob metricCollectJob) {
        this.metricCollectJob = metricCollectJob;
    }

    public void refresh() {
        metricCollectJob.collect();
    }
}
