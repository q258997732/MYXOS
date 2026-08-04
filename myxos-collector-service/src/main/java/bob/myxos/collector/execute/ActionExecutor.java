package bob.myxos.collector.execute;

import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.ThresholdAction;
import bob.myxos.domain.entity.ThresholdRule;

/**
 * 阈值动作执行器接口
 * <p>
 * 每个实现类对应一种动作类型（如记录日志、执行操作），
 * 由 {@link ThresholdEvaluator} 在阈值 breached 时调用。
 */
public interface ActionExecutor {

    /**
     * 判断是否支持该动作类型
     *
     * @param actionType 动作类型字符串
     * @return true 表示支持
     */
    boolean supports(String actionType);

    /**
     * 执行动作
     *
     * @param rule        触发动作的规则
     * @param action      动作配置
     * @param device      目标设备
     * @param alarmId     关联告警 ID
     * @param androidName 触发快照对应的安卓实例名，非实例类快照为 null
     */
    void execute(ThresholdRule rule, ThresholdAction action, Device device, Long alarmId, String androidName);
}
