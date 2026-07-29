package bob.myxos.collector.execute;

import bob.myxos.common.enums.ActionType;
import bob.myxos.domain.entity.ActionLog;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.ThresholdAction;
import bob.myxos.domain.entity.ThresholdRule;
import bob.myxos.domain.mapper.ActionLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * LOG 类型动作执行器
 * <p>
 * 将阈值触发事件以日志形式写入 action_log 表。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class LogActionExecutor implements ActionExecutor {

    private final ActionLogMapper actionLogMapper;

    @Override
    public boolean supports(String actionType) {
        return ActionType.LOG.name().equals(actionType);
    }

    @Override
    public void execute(ThresholdRule rule, ThresholdAction action, Device device, Long alarmId) {
        ActionLog actionLog = new ActionLog();
        actionLog.setAlarmId(alarmId);
        actionLog.setDeviceId(device == null ? null : device.getId());
        actionLog.setActionType(ActionType.LOG.name());
        actionLog.setLogLevel(action.getLogLevel());
        actionLog.setMessage(buildMessage(rule, action, device));
        actionLog.setCreatedAt(LocalDateTime.now());
        actionLogMapper.insert(actionLog);
    }

    /**
     * 构造日志内容
     */
    private String buildMessage(ThresholdRule rule, ThresholdAction action, Device device) {
        String deviceName = device == null ? "unknown" : (device.getName() == null ? String.valueOf(device.getId()) : device.getName());
        return String.format("阈值触发：rule=%s, device=%s, metric=%s, op=%s, threshold=%s",
                rule == null ? "?" : rule.getName(),
                deviceName,
                rule == null ? "?" : rule.getMetricType(),
                rule == null ? "?" : rule.getCompareOp(),
                rule == null || rule.getThresholdValue() == null ? "?" : rule.getThresholdValue().toPlainString());
    }
}
