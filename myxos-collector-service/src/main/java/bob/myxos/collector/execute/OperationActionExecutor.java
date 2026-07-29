package bob.myxos.collector.execute;

import bob.myxos.common.enums.ActionType;
import bob.myxos.common.enums.OpTaskStatus;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.OpTask;
import bob.myxos.domain.entity.ThresholdAction;
import bob.myxos.domain.entity.ThresholdRule;
import bob.myxos.domain.mapper.OpTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

/**
 * OPERATION 类型动作执行器
 * <p>
 * 向 op_task 表插入一条 PENDING 任务，由 {@link OpTaskExecuteJob} 异步调度执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OperationActionExecutor implements ActionExecutor {

    /** 任务来源：自动触发 */
    private static final String SOURCE_AUTO = "AUTO";
    /** 默认最大重试次数 */
    private static final int DEFAULT_MAX_RETRY = 3;

    private final OpTaskMapper opTaskMapper;

    @Override
    public boolean supports(String actionType) {
        return ActionType.OPERATION.name().equals(actionType);
    }

    @Override
    public void execute(ThresholdRule rule, ThresholdAction action, Device device, Long alarmId) {
        OpTask task = new OpTask();
        task.setDeviceId(device == null ? null : device.getId());
        task.setOperationCode(action.getOperationCode());
        task.setParams(action.getOperationParams());
        task.setSource(SOURCE_AUTO);
        task.setSourceRefId(alarmId);
        task.setStatus(OpTaskStatus.PENDING.name());
        task.setRetryCount(0);
        task.setMaxRetry(DEFAULT_MAX_RETRY);
        task.setScheduledAt(LocalDateTime.now());
        opTaskMapper.insert(task);
        log.info("已创建操作任务：taskId={}, deviceId={}, operationCode={}, alarmId={}",
                task.getId(), task.getDeviceId(), task.getOperationCode(), alarmId);
    }
}
