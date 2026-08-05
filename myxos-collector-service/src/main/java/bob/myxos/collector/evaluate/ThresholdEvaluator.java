package bob.myxos.collector.evaluate;

import bob.myxos.collector.execute.ActionExecutor;
import bob.myxos.collector.execute.ActionExecutorRegistry;
import bob.myxos.common.enums.CompareOp;
import bob.myxos.common.enums.ConditionType;
import bob.myxos.common.enums.ScopeType;
import bob.myxos.domain.entity.AlarmEvent;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.MetricSnapshot;
import bob.myxos.domain.entity.ThresholdAction;
import bob.myxos.domain.entity.ThresholdRule;
import bob.myxos.domain.mapper.AlarmEventMapper;
import bob.myxos.domain.mapper.MetricSnapshotMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
    private final ObjectMapper objectMapper;

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
     * <p>
     * 数值规则使用 {@link MetricSnapshot#getMetricNum()}，
     * 字符规则（thresholdText 非空）使用 {@link MetricSnapshot#getMetricValue()} 进行比较
     */
    private void evaluateOne(Device device, MetricSnapshot snapshot) {
        String metricKey = snapshot.getMetricCode();
        List<RuleCache.RuleWithActions> rules = metricKey == null || metricKey.trim().isEmpty()
                ? ruleCache.getByMetricType(snapshot.getMetricType())
                : ruleCache.getByMetricCode(metricKey);
        if (rules.isEmpty()) {
            return;
        }
        String androidName = extractAndroidName(snapshot);
        for (RuleCache.RuleWithActions rwa : rules) {
            ThresholdRule rule = rwa.getRule();
            try {
                if (!matchScope(rule, device, androidName)) {
                    continue;
                }
                if (!isTextRule(rule) && !isValidNumericSnapshot(snapshot)) {
                    log.debug("跳过不可数值化的指标快照：deviceId={}, metricCode={}, value={}",
                            device.getId(), metricKey, snapshot.getMetricValue());
                    continue;
                }
                boolean breached = isBreached(rule, device, snapshot);
                if (breached) {
                    onBreach(rule, rwa.getActions(), device, displayValue(rule, snapshot), androidName);
                } else {
                    onRecover(rule, device, androidName);
                }
            } catch (Exception e) {
                log.error("规则判定异常：ruleId={}, deviceId={}", rule.getId(), device.getId(), e);
            }
        }
    }

    /**
     * 从快照 extra 中解析安卓实例名（ANDROID_STATUS 快照的 extra 形如 {"name":"容器名"}）
     *
     * @param snapshot 指标快照
     * @return 安卓实例名，非实例类快照返回 null
     */
    private String extractAndroidName(MetricSnapshot snapshot) {
        String extra = snapshot.getExtra();
        if (extra == null || extra.isEmpty()) {
            return null;
        }
        try {
            JsonNode node = objectMapper.readTree(extra);
            JsonNode nameNode = node.get("name");
            return nameNode != null && !nameNode.isNull() ? nameNode.asText() : null;
        } catch (Exception e) {
            log.debug("解析快照 extra 失败：{}", extra);
            return null;
        }
    }

    /**
     * 计算告警展示用指标值：字符规则取字符串值，数值规则取数值文本
     */
    private String displayValue(ThresholdRule rule, MetricSnapshot snapshot) {
        if (isTextRule(rule)) {
            return snapshot.getMetricValue();
        }
        return snapshot.getMetricNum() == null ? null : snapshot.getMetricNum().toPlainString();
    }

    /**
     * 是否为字符判断规则（thresholdText 非空）
     */
    private boolean isStringRule(ThresholdRule rule) {
        return ConditionType.STRING.name().equals(rule.getConditionType())
                || ConditionType.ENUM.name().equals(rule.getConditionType())
                || (rule.getThresholdText() != null && !rule.getThresholdText().isEmpty());
    }

    private boolean isTextRule(ThresholdRule rule) {
        return isStringRule(rule);
    }

    private boolean isValidNumericSnapshot(MetricSnapshot snapshot) {
        return snapshot.getMetricNum() != null && !"UNKNOWN".equalsIgnoreCase(snapshot.getMetricValue());
    }

    /**
     * 判断规则作用范围是否覆盖当前设备与快照
     * <p>
     * scopeType=DEVICE 时优先匹配 scopeIds（逗号分隔多设备），为空则回退匹配 scopeId；
     * 规则配置了 scopeAndroidName（逗号分隔多实例）时，仅匹配这些安卓实例的快照（ANDROID_STATUS 场景）
     *
     * @param rule        规则
     * @param device      设备
     * @param androidName 快照对应的安卓实例名，非实例类快照为 null
     * @return true 表示命中
     */
    boolean matchScope(ThresholdRule rule, Device device, String androidName) {
        // 实例名过滤：规则指定了目标实例名（逗号分隔可多选）时，仅匹配这些实例的快照
        String scopeAndroidName = rule.getScopeAndroidName();
        if (scopeAndroidName != null && !scopeAndroidName.trim().isEmpty()
                && !matchAndroidName(scopeAndroidName, androidName)) {
            return false;
        }
        String scopeType = rule.getScopeType();
        if (ScopeType.ALL.name().equals(scopeType)) {
            return true;
        }
        if (ScopeType.DEVICE.name().equals(scopeType)) {
            if (device.getId() == null) {
                return false;
            }
            String scopeIds = rule.getScopeIds();
            if (scopeIds != null && !scopeIds.trim().isEmpty()) {
                for (String part : scopeIds.split(",")) {
                    try {
                        if (Long.parseLong(part.trim()) == device.getId()) {
                            return true;
                        }
                    } catch (NumberFormatException ignored) {
                        // 忽略非法片段，继续匹配其余 ID
                    }
                }
                return false;
            }
            long scopeId = rule.getScopeId() == null ? 0L : rule.getScopeId();
            return device.getId() == scopeId;
        }
        if (ScopeType.GROUP.name().equals(scopeType)) {
            // scopeId 为 null 时按 0 处理，避免 NPE
            long scopeId = rule.getScopeId() == null ? 0L : rule.getScopeId();
            return device.getGroupId() != null && device.getGroupId() == scopeId;
        }
        return false;
    }

    /**
     * 判断快照实例名是否命中规则配置的实例名集合（逗号分隔，逐项精确匹配）
     *
     * @param scopeAndroidName 规则配置的实例名（单个或逗号分隔多个）
     * @param androidName      快照对应的安卓实例名，可为 null
     * @return true 表示命中
     */
    private boolean matchAndroidName(String scopeAndroidName, String androidName) {
        if (androidName == null) {
            return false;
        }
        for (String part : scopeAndroidName.split(",")) {
            if (part.trim().equals(androidName)) {
                return true;
            }
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
     * 字符串比较（字符判断）
     *
     * @param value  当前字符串值
     * @param target 目标字符串
     * @param op     比较操作（EQ / NE / CONTAINS）
     * @return true 表示满足条件（breach）
     */
    boolean compareText(String value, String target, CompareOp op) {
        if (value == null || target == null || op == null) {
            return false;
        }
        switch (op) {
            case EQ:
                return value.trim().equals(target.trim());
            case NE:
                return !value.trim().equals(target.trim());
            case CONTAINS:
                return value.contains(target);
            case NOT_CONTAINS:
                return !value.contains(target);
            default:
                return false;
        }
    }

    /** 枚举值与 JSON 数组阈值比较。 */
    boolean compareEnum(String value, String optionsJson, CompareOp op) {
        if (value == null || optionsJson == null || op == null) {
            return false;
        }
        try {
            JsonNode options = objectMapper.readTree(optionsJson);
            if (!options.isArray()) {
                return false;
            }
            boolean contained = false;
            for (JsonNode option : options) {
                if (value.trim().equals(option.asText().trim())) {
                    contained = true;
                    break;
                }
            }
            if (op == CompareOp.IN || op == CompareOp.EQ) {
                return contained;
            }
            if (op == CompareOp.NOT_IN || op == CompareOp.NE) {
                return !contained;
            }
            return false;
        } catch (Exception e) {
            log.warn("枚举阈值不是有效 JSON 数组：{}", optionsJson);
            return false;
        }
    }

    /**
     * 根据触发模式判断是否真正 breach
     * <p>
     * 字符规则（thresholdText 非空）按字符串比较，数值规则按 BigDecimal 比较
     */
    private boolean isBreached(ThresholdRule rule, Device device, MetricSnapshot snapshot) {
        CompareOp op = parseCompareOp(rule.getCompareOp());
        if (op == null) {
            log.warn("未知的比较操作：ruleId={}, compareOp={}", rule.getId(), rule.getCompareOp());
            return false;
        }
        boolean stringRule = isStringRule(rule);
        boolean enumRule = ConditionType.ENUM.name().equals(rule.getConditionType())
                || op == CompareOp.IN || op == CompareOp.NOT_IN;
        boolean currentBreach = enumRule
                ? compareEnum(snapshot.getMetricValue(), rule.getThresholdText(), op)
                : stringRule
                ? compareText(snapshot.getMetricValue(), rule.getThresholdText(), op)
                : compare(snapshot.getMetricNum(), rule.getThresholdValue(), op);
        if (!currentBreach) {
            return false;
        }

        String triggerMode = rule.getTriggerMode();
        if (TRIGGER_MODE_DURATION.equals(triggerMode)) {
            int durationSec = rule.getDurationSec() == null ? 0 : rule.getDurationSec();
            if (durationSec <= 0) {
                return true;
            }
            // 时间窗以快照的采集时间为基准而非判定的挂钟时间：
            // 采集一轮涉及多次设备 HTTP 调用，判定可能滞后采集数秒，
            // 用 now() 做基准时短持续时长（如 5 秒）的时间窗会错过当前快照导致永不触发
            LocalDateTime referenceTime = snapshot.getCollectedAt() != null
                    ? snapshot.getCollectedAt() : LocalDateTime.now();
            LocalDateTime startTime = referenceTime.minusSeconds(durationSec);
            List<MetricSnapshot> recent = selectHistory(device, rule, snapshot, startTime, null);
            if (recent == null || recent.isEmpty()) {
                return false;
            }
            return recent.stream().allMatch(s -> enumRule
                    ? compareEnum(s.getMetricValue(), rule.getThresholdText(), op)
                    : stringRule
                    ? compareText(s.getMetricValue(), rule.getThresholdText(), op)
                    : compare(s.getMetricNum(), rule.getThresholdValue(), op));
        }
        if (TRIGGER_MODE_CONSECUTIVE.equals(triggerMode)) {
            int count = rule.getConsecutiveCount() == null ? 0 : rule.getConsecutiveCount();
            if (count <= 0) {
                return true;
            }
            List<MetricSnapshot> recent = selectHistory(device, rule, snapshot, null, count);
            if (recent == null || recent.size() < count) {
                return false;
            }
            return recent.stream().allMatch(s -> enumRule
                    ? compareEnum(s.getMetricValue(), rule.getThresholdText(), op)
                    : stringRule
                    ? compareText(s.getMetricValue(), rule.getThresholdText(), op)
                    : compare(s.getMetricNum(), rule.getThresholdValue(), op));
        }
        // 未识别模式按即时触发处理
        return true;
    }

    /**
     * 查询历史采样：带 extra 的快照（如 ANDROID_STATUS 按实例区分）按 extra 精确过滤，
     * 避免同一设备上不同安卓实例的采样混在一起影响持续时长/连续次数判定
     *
     * @param startTime 持续时长模式的起始时间（CONSECUTIVE 模式传 null）
     * @param limit     连续次数模式的条数上限（DURATION 模式传 null）
     */
    private List<MetricSnapshot> selectHistory(Device device, ThresholdRule rule, MetricSnapshot snapshot,
                                               LocalDateTime startTime, Integer limit) {
        if (snapshot.getMetricCode() != null && !snapshot.getMetricCode().trim().isEmpty()
                && snapshot.getTargetType() != null && !snapshot.getTargetType().trim().isEmpty()) {
            String androidName = snapshot.getAndroidName() == null ? "" : snapshot.getAndroidName();
            return startTime != null
                    ? metricSnapshotMapper.selectRecentByDeviceMetricCodeTargetAndAndroidName(device.getId(),
                    snapshot.getMetricCode(), snapshot.getTargetType(), androidName, startTime)
                    : metricSnapshotMapper.selectLatestByDeviceMetricCodeTargetAndAndroidName(device.getId(),
                    snapshot.getMetricCode(), snapshot.getTargetType(), androidName, limit);
        }
        String extra = snapshot.getExtra();
        boolean hasExtra = extra != null && !extra.isEmpty();
        if (startTime != null) {
            return hasExtra
                    ? metricSnapshotMapper.selectRecentByDeviceTypeAndExtra(
                            device.getId(), rule.getMetricType(), extra, startTime)
                    : metricSnapshotMapper.selectRecentByDeviceAndType(
                            device.getId(), rule.getMetricType(), startTime);
        }
        return hasExtra
                ? metricSnapshotMapper.selectLatestByDeviceTypeAndExtra(
                        device.getId(), rule.getMetricType(), extra, limit)
                : metricSnapshotMapper.selectLatestByDeviceAndType(
                        device.getId(), rule.getMetricType(), limit);
    }

    /**
     * breach 时的处理：插入或更新 FIRING 告警，并执行动作
     * <p>
     * 动作仅在新建 FIRING 告警（状态跃迁）时执行一次；
     * 已处于 FIRING 的告警只刷新指标值与触发时间，避免 OPERATION 类动作
     * （如重启安卓实例）在每个采集周期重复执行造成重启循环
     *
     * @param displayValue 告警展示用指标值（数值文本或字符串值）
     * @param androidName  触发快照对应的安卓实例名，非实例类快照为 null
     */
    private void onBreach(ThresholdRule rule, List<ThresholdAction> actions, Device device,
                          String displayValue, String androidName) {
        AlarmEvent firing = alarmEventMapper.selectFiringByRuleDeviceAndAndroid(
                rule.getId(), device.getId(), androidName);
        LocalDateTime now = LocalDateTime.now();
        if (firing != null) {
            firing.setMetricValue(displayValue);
            // 同步刷新阈值：规则阈值被修改后，持续 FIRING 的告警仍展示旧阈值会误导用户
            firing.setThresholdValue(thresholdDisplay(rule));
            firing.setFiredAt(now);
            alarmEventMapper.updateById(firing);
            return;
        }

        AlarmEvent alarm = new AlarmEvent();
        alarm.setRuleId(rule.getId());
        alarm.setDeviceId(device.getId());
        alarm.setAndroidName(androidName);
        alarm.setMetricType(rule.getMetricType());
        alarm.setMetricValue(displayValue);
        alarm.setThresholdValue(thresholdDisplay(rule));
        alarm.setFiredAt(now);
        alarm.setStatus(ALARM_STATUS_FIRING);
        alarmEventMapper.insert(alarm);
        Long alarmId = alarm.getId();

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
                executorOpt.get().execute(rule, action, device, alarmId, androidName);
            } catch (Exception e) {
                log.error("执行动作异常：actionId={}, ruleId={}, deviceId={}",
                        action.getId(), rule.getId(), device.getId(), e);
            }
        }
    }

    /**
     * 计算告警展示用阈值：数值规则取数值文本，字符规则（thresholdValue 为空）取目标文本
     */
    private String thresholdDisplay(ThresholdRule rule) {
        return rule.getThresholdValue() == null
                ? rule.getThresholdText()
                : rule.getThresholdValue().toPlainString();
    }

    /**
     * 未 breach 时若存在 FIRING 告警则标记为 RESOLVED（按规则+设备+实例维度匹配）
     */
    private void onRecover(ThresholdRule rule, Device device, String androidName) {
        AlarmEvent firing = alarmEventMapper.selectFiringByRuleDeviceAndAndroid(
                rule.getId(), device.getId(), androidName);
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
