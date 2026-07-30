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
        evaluator = new ThresholdEvaluator(ruleCache, metricSnapshotMapper, alarmEventMapper, executorRegistry);
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
        when(alarmEventMapper.selectFiringByRuleAndDevice(anyLong(), anyLong())).thenReturn(null);
        when(executorRegistry.getExecutor("LOG")).thenReturn(Optional.of(actionExecutor));

        // Act
        evaluator.evaluate(device, Collections.singletonList(snapshot));

        // Assert
        ArgumentCaptor<AlarmEvent> captor = ArgumentCaptor.forClass(AlarmEvent.class);
        verify(alarmEventMapper, times(1)).insert(captor.capture());
        assertEquals("FIRING", captor.getValue().getStatus());
        verify(actionExecutor, times(1)).execute(any(), any(), any(), any());
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
        when(alarmEventMapper.selectFiringByRuleAndDevice(anyLong(), anyLong())).thenReturn(firing);

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
        when(alarmEventMapper.selectFiringByRuleAndDevice(anyLong(), anyLong())).thenReturn(null);

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
        assertTrue(evaluator.matchScope(rule, device));
        device.setId(2L);
        assertFalse(evaluator.matchScope(rule, device));
    }

    @Test
    @DisplayName("GROUP 作用范围只匹配指定分组")
    void matchScopeGroup() {
        // Arrange
        Device device = device(1L, 5L);
        ThresholdRule rule = rule("CPU", CompareOp.GT.name(), BigDecimal.ONE, "DURATION", 0, 0, ScopeType.GROUP.name(), 5L);

        // Act & Assert
        assertTrue(evaluator.matchScope(rule, device));
        device.setGroupId(6L);
        assertFalse(evaluator.matchScope(rule, device));
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
