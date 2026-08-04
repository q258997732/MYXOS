package bob.myxos.collector.execute;

import bob.myxos.common.enums.ActionType;
import bob.myxos.common.enums.OpTaskStatus;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.OpTask;
import bob.myxos.domain.entity.ThresholdAction;
import bob.myxos.domain.entity.ThresholdRule;
import bob.myxos.domain.mapper.OpTaskMapper;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

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
    /** 操作参数中的实例名占位符：执行时替换为触发告警的安卓实例名 */
    private static final String NAME_PLACEHOLDER = "${name}";

    private final OpTaskMapper opTaskMapper;
    private final ObjectMapper objectMapper;

    @Override
    public boolean supports(String actionType) {
        return ActionType.OPERATION.name().equals(actionType);
    }

    @Override
    public void execute(ThresholdRule rule, ThresholdAction action, Device device, Long alarmId, String androidName) {
        OpTask task = new OpTask();
        task.setDeviceId(device == null ? null : device.getId());
        task.setOperationCode(action.getOperationCode());
        task.setParams(resolveParams(action.getOperationParams(), androidName));
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

    /**
     * 解析动作参数：当触发快照带有安卓实例名时，若参数中 name 缺失、为空或为占位符
     * ${name}，则自动填入触发的实例名，实现"哪个实例状态异常就对哪个实例执行操作"
     *
     * @param operationParams 动作配置的原始参数 JSON
     * @param androidName     触发快照对应的安卓实例名，可为 null
     * @return 注入实例名后的参数 JSON
     */
    private String resolveParams(String operationParams, String androidName) {
        if (androidName == null || androidName.isEmpty()) {
            return operationParams;
        }
        try {
            Map<String, Object> params = operationParams == null || operationParams.trim().isEmpty()
                    ? new HashMap<>()
                    : objectMapper.readValue(operationParams, new TypeReference<Map<String, Object>>() {
                    });
            Object name = params.get("name");
            if (name == null || name.toString().trim().isEmpty()
                    || NAME_PLACEHOLDER.equals(name.toString().trim())) {
                params.put("name", androidName);
            }
            return objectMapper.writeValueAsString(params);
        } catch (Exception e) {
            log.warn("操作参数注入实例名失败，使用原参数：{}", operationParams, e);
            return operationParams;
        }
    }
}
