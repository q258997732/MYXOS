package bob.myxos.collector.collector;

import bob.myxos.domain.entity.MetricBinding;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.MetricSnapshot;
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
import java.util.concurrent.atomic.AtomicReference;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;

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

    @Test
    void 队列拒绝后应释放单飞锁并为后续调度退避() {
        MetricBindingScheduler scheduler = scheduler();
        MetricBinding binding = hostBinding();
        doAnswer(invocation -> {
            ((MetricBindingScheduler.RejectedTask) invocation.getArgument(0)).rejected();
            return null;
        }).doNothing().when(executor).execute(any(Runnable.class));

        scheduler.dispatch(Collections.singletonList(binding), statuses("HOST:1", "ONLINE"));
        scheduler.dispatch(Collections.singletonList(binding), statuses("HOST:1", "ONLINE"));

        verify(bindingMapper).markSkipped(any(Long.class), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(executor, org.mockito.Mockito.times(2)).execute(any(Runnable.class));
    }

    @Test
    void 拒绝退避更新异常时应只尝试一次并释放单飞锁() {
        MetricBindingScheduler scheduler = scheduler();
        MetricBinding binding = hostBinding();
        doAnswer(invocation -> {
            ((MetricBindingScheduler.RejectedTask) invocation.getArgument(0)).rejected();
            return null;
        }).doNothing().when(executor).execute(any(Runnable.class));
        doThrow(new RuntimeException("数据库不可用")).when(bindingMapper)
                .markSkipped(any(Long.class), any(LocalDateTime.class), any(LocalDateTime.class));

        scheduler.dispatch(Collections.singletonList(binding), statuses("HOST:1", "ONLINE"));
        scheduler.dispatch(Collections.singletonList(binding), statuses("HOST:1", "ONLINE"));

        verify(bindingMapper, org.mockito.Mockito.times(1))
                .markSkipped(any(Long.class), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(executor, org.mockito.Mockito.times(2)).execute(any(Runnable.class));
    }

    @Test
    void 长耗时采集应以实际完成时刻计算下次执行时间() throws Exception {
        MetricBindingScheduler scheduler = scheduler();
        MetricBinding binding = hostBinding();
        Device device = new Device();
        device.setId(1L);
        MetricExecutionResult result = MetricExecutionResult.of(new MetricSnapshot());
        AtomicReference<LocalDateTime> collectedFinishedAt = new AtomicReference<LocalDateTime>();
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
        when(deviceMapper.selectById(1L)).thenReturn(device);
        doAnswer(invocation -> {
            Thread.sleep(20L);
            collectedFinishedAt.set(LocalDateTime.now());
            return result;
        }).when(boundMetricCollector).collect(device, binding);

        scheduler.dispatch(Collections.singletonList(binding), statuses("HOST:1", "ONLINE"));

        org.mockito.ArgumentCaptor<LocalDateTime> completedAt = org.mockito.ArgumentCaptor.forClass(LocalDateTime.class);
        verify(bindingMapper).markCollected(org.mockito.ArgumentMatchers.eq(10L), completedAt.capture(), any(LocalDateTime.class));
        assertFalse(completedAt.getValue().isBefore(collectedFinishedAt.get()));
    }

    @Test
    void 快照入库失败应退避且不得标记采集完成() {
        MetricBindingScheduler scheduler = scheduler();
        MetricBinding binding = hostBinding();
        Device device = new Device();
        device.setId(1L);
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(executor).execute(any(Runnable.class));
        when(deviceMapper.selectById(1L)).thenReturn(device);
        when(boundMetricCollector.collect(device, binding)).thenReturn(MetricExecutionResult.of(new MetricSnapshot()));
        doThrow(new RuntimeException("数据库不可用")).when(snapshotMapper).insert(any(MetricSnapshot.class));

        scheduler.dispatch(Collections.singletonList(binding), statuses("HOST:1", "ONLINE"));

        verify(bindingMapper, never()).markCollected(any(Long.class), any(LocalDateTime.class), any(LocalDateTime.class));
        verify(bindingMapper).markSkipped(any(Long.class), any(LocalDateTime.class), any(LocalDateTime.class));
    }

    @Test
    void 复合游标回绕时应选择较早到期的低ID绑定() {
        MetricBindingScheduler scheduler = scheduler();
        MetricBinding highId = hostBinding();
        highId.setId(100L);
        highId.setNextCollectAt(LocalDateTime.of(2026, 8, 5, 10, 0));
        MetricBinding newerHighId = hostBinding();
        newerHighId.setId(101L);
        newerHighId.setDeviceId(3L);
        newerHighId.setNextCollectAt(LocalDateTime.of(2026, 8, 5, 10, 1));
        MetricBinding lowId = hostBinding();
        lowId.setId(1L);
        lowId.setDeviceId(2L);
        lowId.setNextCollectAt(LocalDateTime.of(2026, 8, 5, 9, 0));
        when(bindingMapper.selectFirstDue(any(LocalDateTime.class), org.mockito.ArgumentMatchers.eq(100)))
                .thenReturn(Collections.singletonList(highId));
        when(bindingMapper.selectDueAfter(any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq(highId.getNextCollectAt()), org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq(100))).thenReturn(Collections.singletonList(newerHighId));
        when(bindingMapper.selectDueAtOrBefore(any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq(newerHighId.getNextCollectAt()), org.mockito.ArgumentMatchers.eq(101L),
                org.mockito.ArgumentMatchers.eq(100)))
                .thenReturn(Collections.singletonList(lowId));
        when(deviceMapper.selectById(1L)).thenReturn(onlineDevice());
        when(deviceMapper.selectById(2L)).thenReturn(onlineDevice(2L));
        when(deviceMapper.selectById(3L)).thenReturn(onlineDevice(3L));

        scheduler.schedule();
        scheduler.schedule();
        scheduler.schedule();

        verify(bindingMapper).selectDueAtOrBefore(any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq(newerHighId.getNextCollectAt()), org.mockito.ArgumentMatchers.eq(101L),
                org.mockito.ArgumentMatchers.eq(100));
        verify(executor, org.mockito.Mockito.times(3)).execute(any(Runnable.class));
    }

    @Test
    void 复合游标前向查询应按到期时间而非ID推进() {
        MetricBindingScheduler scheduler = scheduler();
        MetricBinding first = hostBinding();
        first.setId(100L);
        first.setNextCollectAt(LocalDateTime.of(2026, 8, 5, 10, 0));
        MetricBinding second = hostBinding();
        second.setId(1L);
        second.setDeviceId(2L);
        second.setNextCollectAt(LocalDateTime.of(2026, 8, 5, 11, 0));
        when(bindingMapper.selectFirstDue(any(LocalDateTime.class), org.mockito.ArgumentMatchers.eq(100)))
                .thenReturn(Collections.singletonList(first));
        when(bindingMapper.selectDueAfter(any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq(first.getNextCollectAt()), org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq(100))).thenReturn(Collections.singletonList(second));
        when(deviceMapper.selectById(1L)).thenReturn(onlineDevice());
        when(deviceMapper.selectById(2L)).thenReturn(onlineDevice(2L));

        scheduler.schedule();
        scheduler.schedule();
        scheduler.schedule();

        verify(bindingMapper).selectDueAfter(any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq(first.getNextCollectAt()), org.mockito.ArgumentMatchers.eq(100L),
                org.mockito.ArgumentMatchers.eq(100));
    }

    @Test
    void 回绕遇到仍在途绑定时不得重复提交() {
        MetricBindingScheduler scheduler = scheduler();
        MetricBinding binding = hostBinding();
        binding.setNextCollectAt(LocalDateTime.of(2026, 8, 5, 10, 0));
        when(bindingMapper.selectFirstDue(any(LocalDateTime.class), org.mockito.ArgumentMatchers.eq(100)))
                .thenReturn(Collections.singletonList(binding));
        when(bindingMapper.selectDueAtOrBefore(any(LocalDateTime.class),
                org.mockito.ArgumentMatchers.eq(binding.getNextCollectAt()), org.mockito.ArgumentMatchers.eq(10L),
                org.mockito.ArgumentMatchers.eq(100))).thenReturn(Collections.singletonList(binding));
        when(deviceMapper.selectById(1L)).thenReturn(onlineDevice());

        scheduler.schedule();
        scheduler.schedule();

        verify(executor, org.mockito.Mockito.times(1)).execute(any(Runnable.class));
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

    private Device onlineDevice() {
        return onlineDevice(1L);
    }

    private Device onlineDevice(Long id) {
        Device device = new Device();
        device.setId(id);
        device.setStatus("ONLINE");
        return device;
    }
}
