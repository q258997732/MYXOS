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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 阈值判定器
 * <p>
 * 对一组 {@link MetricSnapshot} 按规则进行阈值判定：
 * <ul>
 *   <li>breach 时插入或更新 FIRING 告警，并执行规则下所有动作</li>
 *   <li>未 breach 时若存在 FIRING 告警则标记为 RESOLVED</li>
 * </ul>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ThresholdEvaluator {

    /** 触发模式：持续时长 */
    private static final String TRIGGER_MODE_DURATION = "DURATION";
    /** 触发模式：连续次数 */
    private static final String TRIGGER_MODE_CONSECUTIVE = "CONSECUTIVE";

    /** 告警状态：触发中 */
    private static final String ALARM_STATUS_FIRING = "FIRING";
    /** 告警状态：已恢复 */
    private static final String ALARM_STATUS_RESOLVED = "RESOLVED";

    private final RuleCache ruleCache;
    private final MetricSnapshotMapper metricSnapshotMapper;
    private final AlarmEventMapper alarmEventMapper;
    private final ActionExecutorRegistry executorRegistry;

    /**
     * 对一台设备的一批指标快照进行阈值判定
     *
     * @param device    设备
     * @param snapshots 该设备当前批次采集到的快照
     */
    public void evaluate(Device device, List<MetricSnapshot> snapshots) {
        if (device == null || snapshots == null || snapshots.isEmpty()) {
            return;
        }
        for (MetricSnapshot snapshot : snapshots) {
            try {
                evaluateOne(device, snapshot);
            } catch (Exception e) {
                log.error("阈值判定异常：deviceId={}, metricType={}", device.getId(), snapshot.getMetricType(), e);
            }
        }
    }

    /**
     * 对单条快照进行判定
     */
    private void evaluateOne(Device device, MetricSnapshot snapshot) {
        List<RuleCache.RuleWithActions> rules = ruleCache.getByMetricType(snapshot.getMetricType());
        if (rules.isEmpty()) {
            return;
        }
        BigDecimal currentValue = snapshot.getMetricNum();
        if (currentValue == null) {
            return;
        }
        for (RuleCache.RuleWithActions rwa : rules) {
            ThresholdRule rule = rwa.getRule();
            try {
                if (!matchScope(rule, device)) {
                    continue;
                }
                boolean breached = isBreached(rule, device, currentValue);
                if (breached) {
                    onBreach(rule, rwa.getActions(), device, currentValue);
                } else {
                    onRecover(rule, device);
                }
            } catch (Exception e) {
                log.error("规则判定异常：ruleId={}, deviceId={}", rule.getId(), device.getId(), e);
            }
        }
    }

    /**
     * 判断规则作用范围是否覆盖当前设备
     *
     * @param rule   规则
     * @param device 设备
     * @return true 表示命中
     */
    boolean matchScope(ThresholdRule rule, Device device) {
        String scopeType = rule.getScopeType();
        if (ScopeType.ALL.name().equals(scopeType)) {
            return true;
        }
        // scopeId 为 null 时按 0 处理，避免 NPE
        long scopeId = rule.getScopeId() == null ? 0L : rule.getScopeId();
        if (ScopeType.DEVICE.name().equals(scopeType)) {
            return device.getId() != null && device.getId() == scopeId;
        }
        if (ScopeType.GROUP.name().equals(scopeType)) {
            return device.getGroupId() != null && device.getGroupId() == scopeId;
        }
        return false;
    }

    /**
     * 比较两个数值
     *
     * @param value     当前值
     * @param threshold 阈值
     * @param op        比较操作
     * @return true 表示满足条件（breach）
     */
    boolean compare(BigDecimal value, BigDecimal threshold, CompareOp op) {
        if (value == null || threshold == null || op == null) {
            return false;
        }
        int cmp = value.compareTo(threshold);
        switch (op) {
            case GT:
                return cmp > 0;
            case GTE:
                return cmp >= 0;
            case LT:
                return cmp < 0;
            case LTE:
                return cmp <= 0;
            case EQ:
                return cmp == 0;
            case NE:
                return cmp != 0;
            default:
                return false;
        }
    }

    /**
     * 根据触发模式判断是否真正 breach
     */
    private boolean isBreached(ThresholdRule rule, Device device, BigDecimal currentValue) {
        CompareOp op = parseCompareOp(rule.getCompareOp());
        if (op == null) {
            log.warn("未知的比较操作：ruleId={}, compareOp={}", rule.getId(), rule.getCompareOp());
            return false;
        }
        BigDecimal threshold = rule.getThresholdValue();
        boolean currentBreach = compare(currentValue, threshold, op);
        if (!currentBreach) {
            return false;
        }

        String triggerMode = rule.getTriggerMode();
        if (TRIGGER_MODE_DURATION.equals(triggerMode)) {
            int durationSec = rule.getDurationSec() == null ? 0 : rule.getDurationSec();
            if (durationSec <= 0) {
                return true;
            }
            LocalDateTime startTime = LocalDateTime.now().minusSeconds(durationSec);
            List<MetricSnapshot> recent = metricSnapshotMapper.selectRecentByDeviceAndType(
                    device.getId(), rule.getMetricType(), startTime);
            if (recent == null || recent.isEmpty()) {
                return false;
            }
            return recent.stream().allMatch(s -> compare(s.getMetricNum(), threshold, op));
        }
        if (TRIGGER_MODE_CONSECUTIVE.equals(triggerMode)) {
            int count = rule.getConsecutiveCount() == null ? 0 : rule.getConsecutiveCount();
            if (count <= 0) {
                return true;
            }
            List<MetricSnapshot> recent = metricSnapshotMapper.selectLatestByDeviceAndType(
                    device.getId(), rule.getMetricType(), count);
            if (recent == null || recent.size() < count) {
                return false;
            }
            return recent.stream().allMatch(s -> compare(s.getMetricNum(), threshold, op));
        }
        // 未识别模式按即时触发处理
        return true;
    }

    /**
     * breach 时的处理：插入或更新 FIRING 告警，并执行动作
     */
    private void onBreach(ThresholdRule rule, List<ThresholdAction> actions, Device device, BigDecimal currentValue) {
        AlarmEvent firing = alarmEventMapper.selectFiringByRuleAndDevice(rule.getId(), device.getId());
        LocalDateTime now = LocalDateTime.now();
        Long alarmId;
        if (firing == null) {
            AlarmEvent alarm = new AlarmEvent();
            alarm.setRuleId(rule.getId());
            alarm.setDeviceId(device.getId());
            alarm.setMetricType(rule.getMetricType());
            alarm.setMetricValue(currentValue == null ? null : currentValue.toPlainString());
            alarm.setThresholdValue(rule.getThresholdValue() == null ? null : rule.getThresholdValue().toPlainString());
            alarm.setFiredAt(now);
            alarm.setStatus(ALARM_STATUS_FIRING);
            alarmEventMapper.insert(alarm);
            alarmId = alarm.getId();
        } else {
            firing.setMetricValue(currentValue == null ? null : currentValue.toPlainString());
            firing.setFiredAt(now);
            alarmEventMapper.updateById(firing);
            alarmId = firing.getId();
        }

        if (actions == null || actions.isEmpty()) {
            return;
        }
        for (ThresholdAction action : actions) {
            try {
                Optional<ActionExecutor> executorOpt = executorRegistry.getExecutor(action.getActionType());
                if (!executorOpt.isPresent()) {
                    log.warn("未找到动作执行器：actionType={}, actionId={}", action.getActionType(), action.getId());
                    continue;
                }
                executorOpt.get().execute(rule, action, device, alarmId);
            } catch (Exception e) {
                log.error("执行动作异常：actionId={}, ruleId={}, deviceId={}",
                        action.getId(), rule.getId(), device.getId(), e);
            }
        }
    }

    /**
     * 未 breach 时若存在 FIRING 告警则标记为 RESOLVED
     */
    private void onRecover(ThresholdRule rule, Device device) {
        AlarmEvent firing = alarmEventMapper.selectFiringByRuleAndDevice(rule.getId(), device.getId());
        if (firing == null) {
            return;
        }
        firing.setStatus(ALARM_STATUS_RESOLVED);
        firing.setResolvedAt(LocalDateTime.now());
        alarmEventMapper.updateById(firing);
    }

    /**
     * 解析比较操作枚举
     */
    private CompareOp parseCompareOp(String compareOp) {
        if (compareOp == null) {
            return null;
        }
        try {
            return CompareOp.valueOf(compareOp.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }
}
