package bob.myxos.collector.evaluate;

import bob.myxos.collector.execute.ActionExecutor;
import bob.myxos.collector.execute.ActionExecutorRegistry;
import bob.myxos.common.enums.CompareOp;
import bob.myxos.common.enums.ScopeType;
import bob.myxos.domain.entity.AlarmEvent;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.MetricSnapshot;
import bob.myxos.domain.entity.ThresholdAction;
import bob.myxos.domain.entity.ThresholdRule;
import bob.myxos.domain.mapper.AlarmEventMapper;
import bob.myxos.domain.mapper.MetricSnapshotMapper;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ThresholdEvaluator 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ThresholdEvaluatorTest {

    @Mock
    private RuleCache ruleCache;
    @Mock
    private MetricSnapshotMapper metricSnapshotMapper;
    @Mock
    private AlarmEventMapper alarmEventMapper;
    @Mock
    private ActionExecutorRegistry executorRegistry;
    @Mock
    private ActionExecutor actionExecutor;

    private ThresholdEvaluator evaluator;

    @BeforeEach
    void setUp() {
        evaluator = new ThresholdEvaluator(ruleCache, metricSnapshotMapper, alarmEventMapper,
                executorRegistry, new ObjectMapper());
    }

    @Test
    @DisplayName("当前值大于阈值且触发模式为即时触发时应产生告警")
    void evaluateShouldFireAlarmWhenBreached() {
        // Arrange
        Device device = device(1L, 1L);
        ThresholdRule rule = rule("CPU", CompareOp.GT.name(), new BigDecimal("80"), "DURATION", 0, 0, ScopeType.ALL.name(), null);
        ThresholdAction action = new ThresholdAction();
        action.setActionType("LOG");
        action.setLogLevel("WARN");
        RuleCache.RuleWithActions rwa = new RuleCache.RuleWithActions(rule, Collections.singletonList(action));

        MetricSnapshot snapshot = snapshot(1L, "CPU", new BigDecimal("85"));

        when(ruleCache.getByMetricType("CPU")).thenReturn(Collections.singletonList(rwa));
        when(alarmEventMapper.selectFiringByRuleDeviceAndAndroid(anyLong(), anyLong(), any())).thenReturn(null);
        when(executorRegistry.getExecutor("LOG")).thenReturn(Optional.of(actionExecutor));

        // Act
        evaluator.evaluate(device, Collections.singletonList(snapshot));

        // Assert
        ArgumentCaptor<AlarmEvent> captor = ArgumentCaptor.forClass(AlarmEvent.class);
        verify(alarmEventMapper, times(1)).insert(captor.capture());
        assertEquals("FIRING", captor.getValue().getStatus());
        verify(actionExecutor, times(1)).execute(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("当前值未超过阈值时应将已有 FIRING 告警标记为 RESOLVED")
    void evaluateShouldResolveAlarmWhenNotBreached() {
        // Arrange
        Device device = device(1L, 1L);
        ThresholdRule rule = rule("CPU", CompareOp.GT.name(), new BigDecimal("80"), "DURATION", 0, 0, ScopeType.ALL.name(), null);
        RuleCache.RuleWithActions rwa = new RuleCache.RuleWithActions(rule, Collections.emptyList());

        MetricSnapshot snapshot = snapshot(1L, "CPU", new BigDecimal("70"));
        AlarmEvent firing = new AlarmEvent();
        firing.setStatus("FIRING");

        when(ruleCache.getByMetricType("CPU")).thenReturn(Collections.singletonList(rwa));
        when(alarmEventMapper.selectFiringByRuleDeviceAndAndroid(anyLong(), anyLong(), any())).thenReturn(firing);

        // Act
        evaluator.evaluate(device, Collections.singletonList(snapshot));

        // Assert
        assertEquals("RESOLVED", firing.getStatus());
        assertTrue(firing.getResolvedAt() != null);
        verify(alarmEventMapper, times(1)).updateById(firing);
    }

    @Test
    @DisplayName("DURATION 模式应查询时间段内的采样并全部 breach 才触发")
    void durationModeRequiresAllSamplesBreached() {
        // Arrange
        Device device = device(1L, 1L);
        ThresholdRule rule = rule("CPU", CompareOp.GT.name(), new BigDecimal("80"), "DURATION", 60, 0, ScopeType.ALL.name(), null);
        RuleCache.RuleWithActions rwa = new RuleCache.RuleWithActions(rule, Collections.emptyList());
        MetricSnapshot snapshot = snapshot(1L, "CPU", new BigDecimal("85"));

        MetricSnapshot old = snapshot(1L, "CPU", new BigDecimal("90"));

        when(ruleCache.getByMetricType("CPU")).thenReturn(Collections.singletonList(rwa));
        when(metricSnapshotMapper.selectRecentByDeviceAndType(anyLong(), anyString(), any(LocalDateTime.class)))
                .thenReturn(Collections.singletonList(old));
        when(alarmEventMapper.selectFiringByRuleDeviceAndAndroid(anyLong(), anyLong(), any())).thenReturn(null);

        // Act
        evaluator.evaluate(device, Collections.singletonList(snapshot));

        // Assert
        verify(alarmEventMapper, times(1)).insert(any(AlarmEvent.class));
    }

    @Test
    @DisplayName("CONSECUTIVE 模式采样数量不足时不触发")
    void consecutiveModeNotFiredWhenInsufficientSamples() {
        // Arrange
        Device device = device(1L, 1L);
        ThresholdRule rule = rule("CPU", CompareOp.GT.name(), new BigDecimal("80"), "CONSECUTIVE", 0, 3, ScopeType.ALL.name(), null);
        RuleCache.RuleWithActions rwa = new RuleCache.RuleWithActions(rule, Collections.emptyList());
        MetricSnapshot snapshot = snapshot(1L, "CPU", new BigDecimal("85"));

        when(ruleCache.getByMetricType("CPU")).thenReturn(Collections.singletonList(rwa));
        when(metricSnapshotMapper.selectLatestByDeviceAndType(anyLong(), anyString(), anyInt()))
                .thenReturn(Collections.singletonList(snapshot));

        // Act
        evaluator.evaluate(device, Collections.singletonList(snapshot));

        // Assert
        verify(alarmEventMapper, never()).insert(any(AlarmEvent.class));
    }

    @Test
    @DisplayName("DEVICE 作用范围只匹配指定设备")
    void matchScopeDevice() {
        // Arrange
        Device device = device(1L, 1L);
        ThresholdRule rule = rule("CPU", CompareOp.GT.name(), BigDecimal.ONE, "DURATION", 0, 0, ScopeType.DEVICE.name(), 1L);

        // Act & Assert
        assertTrue(evaluator.matchScope(rule, device, null));
        device.setId(2L);
        assertFalse(evaluator.matchScope(rule, device, null));
    }

    @Test
    @DisplayName("GROUP 作用范围只匹配指定分组")
    void matchScopeGroup() {
        // Arrange
        Device device = device(1L, 5L);
        ThresholdRule rule = rule("CPU", CompareOp.GT.name(), BigDecimal.ONE, "DURATION", 0, 0, ScopeType.GROUP.name(), 5L);

        // Act & Assert
        assertTrue(evaluator.matchScope(rule, device, null));
        device.setGroupId(6L);
        assertFalse(evaluator.matchScope(rule, device, null));
    }

    @Test
    @DisplayName("比较运算应正确判断各种操作符")
    void compareOperations() {
        assertTrue(evaluator.compare(new BigDecimal("10"), new BigDecimal("5"), CompareOp.GT));
        assertTrue(evaluator.compare(new BigDecimal("5"), new BigDecimal("5"), CompareOp.GTE));
        assertTrue(evaluator.compare(new BigDecimal("3"), new BigDecimal("5"), CompareOp.LT));
        assertTrue(evaluator.compare(new BigDecimal("5"), new BigDecimal("5"), CompareOp.LTE));
        assertTrue(evaluator.compare(new BigDecimal("5"), new BigDecimal("5"), CompareOp.EQ));
        assertTrue(evaluator.compare(new BigDecimal("4"), new BigDecimal("5"), CompareOp.NE));
    }

    @Test
    @DisplayName("字符串比较应支持等于、不等于与包含")
    void compareTextOperations() {
        assertTrue(evaluator.compareText("STOPPED", "STOPPED", CompareOp.EQ));
        assertFalse(evaluator.compareText("RUNNING", "STOPPED", CompareOp.EQ));
        assertTrue(evaluator.compareText("RUNNING", "STOPPED", CompareOp.NE));
        assertTrue(evaluator.compareText("not running", "running", CompareOp.CONTAINS));
        assertFalse(evaluator.compareText("running", "stop", CompareOp.CONTAINS));
        assertFalse(evaluator.compareText(null, "STOPPED", CompareOp.EQ));
    }

    @Test
    @DisplayName("字符判断规则应比较快照的字符串值并触发告警")
    void evaluateShouldFireAlarmForStringRule() {
        // Arrange
        Device device = device(1L, 1L);
        ThresholdRule rule = rule("ANDROID_STATUS", CompareOp.EQ.name(), null, "DURATION", 0, 0, ScopeType.ALL.name(), null);
        rule.setThresholdText("STOPPED");
        RuleCache.RuleWithActions rwa = new RuleCache.RuleWithActions(rule, Collections.emptyList());

        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.setDeviceId(1L);
        snapshot.setMetricType("ANDROID_STATUS");
        snapshot.setMetricValue("STOPPED");

        when(ruleCache.getByMetricType("ANDROID_STATUS")).thenReturn(Collections.singletonList(rwa));
        when(alarmEventMapper.selectFiringByRuleDeviceAndAndroid(anyLong(), anyLong(), any())).thenReturn(null);

        // Act
        evaluator.evaluate(device, Collections.singletonList(snapshot));

        // Assert
        ArgumentCaptor<AlarmEvent> captor = ArgumentCaptor.forClass(AlarmEvent.class);
        verify(alarmEventMapper, times(1)).insert(captor.capture());
        assertEquals("STOPPED", captor.getValue().getMetricValue());
        assertEquals("STOPPED", captor.getValue().getThresholdValue());
    }

    @Test
    @DisplayName("字符判断规则在数值快照为空时也能判定")
    void stringRuleSkipsNumericNullCheck() {
        // Arrange：ANDROID_STATUS 快照没有 metricNum，字符规则不应被跳过
        Device device = device(1L, 1L);
        ThresholdRule rule = rule("ANDROID_STATUS", CompareOp.EQ.name(), null, "DURATION", 0, 0, ScopeType.ALL.name(), null);
        rule.setThresholdText("RUNNING");
        RuleCache.RuleWithActions rwa = new RuleCache.RuleWithActions(rule, Collections.emptyList());

        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.setDeviceId(1L);
        snapshot.setMetricType("ANDROID_STATUS");
        snapshot.setMetricValue("STOPPED");

        AlarmEvent firing = new AlarmEvent();
        firing.setStatus("FIRING");

        when(ruleCache.getByMetricType("ANDROID_STATUS")).thenReturn(Collections.singletonList(rwa));
        when(alarmEventMapper.selectFiringByRuleDeviceAndAndroid(anyLong(), anyLong(), any())).thenReturn(firing);

        // Act：规则期望 RUNNING 而实际 STOPPED → 不 breach → 已有告警应恢复
        evaluator.evaluate(device, Collections.singletonList(snapshot));

        // Assert
        assertEquals("RESOLVED", firing.getStatus());
    }

    @Test
    @DisplayName("DEVICE 作用范围应匹配 scopeIds 中的任一设备")
    void matchScopeDeviceIds() {
        // Arrange
        ThresholdRule rule = rule("CPU", CompareOp.GT.name(), BigDecimal.ONE, "DURATION", 0, 0, ScopeType.DEVICE.name(), null);
        rule.setScopeIds("3, 5,7");

        // Act & Assert
        assertTrue(evaluator.matchScope(rule, device(5L, 1L), null));
        assertFalse(evaluator.matchScope(rule, device(2L, 1L), null));
    }

    @Test
    @DisplayName("DEVICE 作用范围在 scopeIds 为空时回退匹配 scopeId")
    void matchScopeDeviceFallbackToScopeId() {
        // Arrange
        ThresholdRule rule = rule("CPU", CompareOp.GT.name(), BigDecimal.ONE, "DURATION", 0, 0, ScopeType.DEVICE.name(), 1L);

        // Act & Assert
        assertTrue(evaluator.matchScope(rule, device(1L, 1L), null));
        assertFalse(evaluator.matchScope(rule, device(2L, 1L), null));
    }

    @Test
    @DisplayName("指定安卓实例名的规则只匹配同名实例的快照")
    void matchScopeAndroidName() {
        // Arrange
        ThresholdRule rule = rule("ANDROID_STATUS", CompareOp.NE.name(), null, "DURATION", 0, 0, ScopeType.DEVICE.name(), 1L);
        rule.setScopeAndroidName("container_01");

        // Act & Assert：设备匹配且实例名一致才命中
        assertTrue(evaluator.matchScope(rule, device(1L, 1L), "container_01"));
        assertFalse(evaluator.matchScope(rule, device(1L, 1L), "container_02"));
        assertFalse(evaluator.matchScope(rule, device(1L, 1L), null));
        assertFalse(evaluator.matchScope(rule, device(2L, 1L), "container_01"));
    }

    @Test
    @DisplayName("实例名配置为逗号分隔多值时匹配任一实例")
    void matchScopeAndroidNameMultiple() {
        // Arrange
        ThresholdRule rule = rule("ANDROID_STATUS", CompareOp.NE.name(), null, "DURATION", 0, 0, ScopeType.DEVICE.name(), 1L);
        rule.setScopeAndroidName("c1, c2 ,c3");

        // Act & Assert：任一配置实例命中，其余实例与 null 不命中
        assertTrue(evaluator.matchScope(rule, device(1L, 1L), "c1"));
        assertTrue(evaluator.matchScope(rule, device(1L, 1L), "c2"));
        assertTrue(evaluator.matchScope(rule, device(1L, 1L), "c3"));
        assertFalse(evaluator.matchScope(rule, device(1L, 1L), "c4"));
        assertFalse(evaluator.matchScope(rule, device(1L, 1L), null));
    }

    @Test
    @DisplayName("告警已处于 FIRING 时不重复执行动作，仅刷新告警")
    void actionsNotRepeatedWhileFiring() {
        // Arrange：CPU 超标且已存在 FIRING 告警
        Device device = device(1L, 1L);
        ThresholdRule rule = rule("CPU", CompareOp.GT.name(), new BigDecimal("80"), "DURATION", 0, 0, ScopeType.ALL.name(), null);
        ThresholdAction action = new ThresholdAction();
        action.setActionType("OPERATION");
        action.setOperationCode("REBOOT_HOST");
        RuleCache.RuleWithActions rwa = new RuleCache.RuleWithActions(rule, Collections.singletonList(action));

        MetricSnapshot snapshot = snapshot(1L, "CPU", new BigDecimal("85"));
        AlarmEvent firing = new AlarmEvent();
        firing.setId(9L);
        firing.setStatus("FIRING");

        when(ruleCache.getByMetricType("CPU")).thenReturn(Collections.singletonList(rwa));
        when(alarmEventMapper.selectFiringByRuleDeviceAndAndroid(anyLong(), anyLong(), any())).thenReturn(firing);

        // Act
        evaluator.evaluate(device, Collections.singletonList(snapshot));

        // Assert：仅更新告警时间，不新建告警也不执行动作（避免每个采集周期重复重启）
        verify(alarmEventMapper, times(1)).updateById(firing);
        verify(alarmEventMapper, never()).insert(any(AlarmEvent.class));
        verify(actionExecutor, never()).execute(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("ANDROID_STATUS 连续次数判定按实例 extra 过滤历史采样")
    void consecutiveModeFiltersHistoryByExtra() {
        // Arrange：规则连续 2 次 NE RUNNING 触发；快照带 extra（实例 container_01）
        Device device = device(1L, 1L);
        ThresholdRule rule = rule("ANDROID_STATUS", CompareOp.NE.name(), null, "CONSECUTIVE", 0, 2, ScopeType.ALL.name(), null);
        rule.setThresholdText("RUNNING");
        RuleCache.RuleWithActions rwa = new RuleCache.RuleWithActions(rule, Collections.emptyList());

        String extra = "{\"name\":\"container_01\"}";
        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.setDeviceId(1L);
        snapshot.setMetricType("ANDROID_STATUS");
        snapshot.setMetricValue("STOPPED");
        snapshot.setExtra(extra);

        MetricSnapshot older = new MetricSnapshot();
        older.setDeviceId(1L);
        older.setMetricType("ANDROID_STATUS");
        older.setMetricValue("STOPPED");
        older.setExtra(extra);

        when(ruleCache.getByMetricType("ANDROID_STATUS")).thenReturn(Collections.singletonList(rwa));
        when(metricSnapshotMapper.selectLatestByDeviceTypeAndExtra(anyLong(), anyString(), anyString(), anyInt()))
                .thenReturn(java.util.Arrays.asList(snapshot, older));
        when(alarmEventMapper.selectFiringByRuleDeviceAndAndroid(anyLong(), anyLong(), any())).thenReturn(null);

        // Act
        evaluator.evaluate(device, Collections.singletonList(snapshot));

        // Assert：使用了按 extra 过滤的查询，且告警记录实例名
        verify(metricSnapshotMapper, times(1))
                .selectLatestByDeviceTypeAndExtra(anyLong(), anyString(), anyString(), anyInt());
        verify(metricSnapshotMapper, never()).selectLatestByDeviceAndType(anyLong(), anyString(), anyInt());
        ArgumentCaptor<AlarmEvent> captor = ArgumentCaptor.forClass(AlarmEvent.class);
        verify(alarmEventMapper, times(1)).insert(captor.capture());
        assertEquals("container_01", captor.getValue().getAndroidName());
    }

    private Device device(Long id, Long groupId) {
        Device device = new Device();
        device.setId(id);
        device.setGroupId(groupId);
        return device;
    }

    private ThresholdRule rule(String metricType, String compareOp, BigDecimal threshold,
                               String triggerMode, int durationSec, int consecutiveCount,
                               String scopeType, Long scopeId) {
        ThresholdRule rule = new ThresholdRule();
        rule.setId(1L);
        rule.setMetricType(metricType);
        rule.setCompareOp(compareOp);
        rule.setThresholdValue(threshold);
        rule.setTriggerMode(triggerMode);
        rule.setDurationSec(durationSec);
        rule.setConsecutiveCount(consecutiveCount);
        rule.setScopeType(scopeType);
        rule.setScopeId(scopeId);
        return rule;
    }

    private MetricSnapshot snapshot(Long deviceId, String metricType, BigDecimal value) {
        MetricSnapshot snapshot = new MetricSnapshot();
        snapshot.setDeviceId(deviceId);
        snapshot.setMetricType(metricType);
        snapshot.setMetricNum(value);
        return snapshot;
    }
}
