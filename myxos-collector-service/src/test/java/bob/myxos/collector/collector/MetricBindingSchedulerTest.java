package bob.myxos.collector.collector;

import bob.myxos.domain.entity.MetricBinding;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.domain.mapper.MetricBindingMapper;
import bob.myxos.domain.mapper.MetricSnapshotMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class MetricBindingSchedulerTest {

    @Mock
    private MetricBindingMapper bindingMapper;
    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private MetricSnapshotMapper snapshotMapper;
    @Mock
    private BoundMetricCollector boundMetricCollector;
    @Mock
    private ThreadPoolTaskExecutor executor;

    @Test
    void 非运行中的安卓实例不应提交采集任务并更新下次时间() {
        MetricBindingScheduler scheduler = scheduler();
        MetricBinding binding = androidBinding("a-1");

        scheduler.dispatch(Collections.singletonList(binding), statuses("ANDROID:1:a-1", "STOPPED"));

        verify(executor, never()).execute(any(Runnable.class));
        verify(bindingMapper).markSkipped(any(Long.class), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void 同一目标已有执行任务时不应重复提交() {
        MetricBindingScheduler scheduler = scheduler();
        MetricBinding binding = hostBinding();
        scheduler.markTargetRunning("HOST:1");

        scheduler.dispatch(Collections.singletonList(binding), statuses("HOST:1", "ONLINE"));

        verify(executor, never()).execute(any(Runnable.class));
    }

    private MetricBindingScheduler scheduler() {
        return new MetricBindingScheduler(bindingMapper, deviceMapper, snapshotMapper,
                boundMetricCollector, executor, 100, 5);
    }

    private MetricBinding hostBinding() {
        MetricBinding binding = new MetricBinding();
        binding.setId(10L);
        binding.setDeviceId(1L);
        binding.setTargetType("HOST");
        binding.setMetricCode("CPU");
        binding.setIntervalSec(60);
        return binding;
    }

    private MetricBinding androidBinding(String name) {
        MetricBinding binding = hostBinding();
        binding.setTargetType("ANDROID_INSTANCE");
        binding.setAndroidName(name);
        binding.setMetricCode("ANDROID_VERSION");
        return binding;
    }

    private Map<String, String> statuses(String key, String status) {
        Map<String, String> statuses = new HashMap<String, String>();
        statuses.put(key, status);
        return statuses;
    }
}
