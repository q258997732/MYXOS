package bob.myxos.collector.execute;

import bob.myxos.common.enums.OpTaskStatus;
import bob.myxos.common.enums.OperationCode;
import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.ActionLog;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.OpTask;
import bob.myxos.domain.mapper.ActionLogMapper;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.domain.mapper.OpTaskMapper;
import bob.myxos.mytos.MytosClient;
import bob.myxos.mytos.MytosClientFactory;
import bob.myxos.mytos.dto.MytosBaseResp;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 操作任务 Runner 工厂
 * <p>
 * 根据 {@link OpTask} 创建 Runnable，调用 {@link MytosClient} 在目标设备上执行具体操作。
 * 执行结果更新到 op_task 表，失败时按指数退避策略延迟重试。
 * 执行成功或失败时写入 action_log 表记录详细日志。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpTaskRunnerFactory {

    /** 重试间隔基数（秒）：实际延迟 = BASE * retryCount */
    private static final long RETRY_DELAY_BASE_SEC = 10L;

    private final OpTaskMapper opTaskMapper;
    private final DeviceMapper deviceMapper;
    private final MytosClientFactory clientFactory;
    private final ObjectMapper objectMapper;
    private final ActionLogMapper actionLogMapper;

    /**
     * 创建任务执行 Runnable
     *
     * @param task 已抢占（RUNNING）的任务
     * @return Runnable
     */
    public Runnable create(OpTask task) {
        return () -> runTask(task);
    }

    /**
     * 执行任务主体逻辑
     */
    private void runTask(OpTask task) {
        if (task == null || task.getId() == null) {
            return;
        }
        long startMs = System.currentTimeMillis();
        try {
            Device device = getDevice(task.getDeviceId());
            MytosClient client = clientFactory.create(device.getIp(), device.getPort());
            OperationCode code = parseOperationCode(task.getOperationCode());
            Map<String, Object> params = parseParams(task.getParams());

            MytosBaseResp resp = client.execute(code, params);
            String resultMsg = buildSuccessResultMsg(resp);

            task.setStatus(OpTaskStatus.SUCCESS.name());
            task.setResultMsg(resultMsg);
            task.setFinishedAt(LocalDateTime.now());
            writeActionLog(task, "INFO", "操作成功：" + code + "，" + resultMsg);
            log.info("操作任务执行成功：taskId={}, deviceId={}, op={}",
                    task.getId(), task.getDeviceId(), code);
        } catch (Exception e) {
            log.error("操作任务执行失败：taskId={}", task.getId(), e);
            handleRetry(task, e);
        } finally {
            try {
                opTaskMapper.updateById(task);
            } catch (Exception ex) {
                log.error("更新操作任务失败：taskId={}", task.getId(), ex);
            }
        }
    }

    /**
     * 根据设备 ID 查询设备，不存在则抛出异常
     */
    private Device getDevice(Long deviceId) {
        if (deviceId == null) {
            throw new BizException("任务缺少设备 ID");
        }
        Device device = deviceMapper.selectById(deviceId);
        if (device == null || (device.getDeleted() != null && device.getDeleted() == 1)) {
            throw new BizException("设备不存在：" + deviceId);
        }
        return device;
    }

    /**
     * 解析操作码
     */
    private OperationCode parseOperationCode(String operationCode) {
        if (operationCode == null || operationCode.trim().isEmpty()) {
            throw new BizException("任务缺少操作码");
        }
        try {
            return OperationCode.valueOf(operationCode.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BizException("不支持的操作码：" + operationCode);
        }
    }

    /**
     * 解析 JSON 参数字符串
     */
    private Map<String, Object> parseParams(String paramsJson) {
        if (paramsJson == null || paramsJson.trim().isEmpty()) {
            return null;
        }
        try {
            return objectMapper.readValue(paramsJson, new TypeReference<Map<String, Object>>() {
            });
        } catch (Exception e) {
            throw new BizException("操作参数 JSON 解析失败：" + e.getMessage());
        }
    }

    /**
     * 构建成功结果消息，包含设备返回码、消息与数据摘要
     */
    private String buildSuccessResultMsg(MytosBaseResp resp) {
        if (resp == null) {
            return "执行成功";
        }
        StringBuilder sb = new StringBuilder();
        sb.append("设备返回码：").append(resp.getCode());
        if (resp.getMsg() != null) {
            sb.append("，消息：").append(resp.getMsg());
        }
        try {
            JsonNode tree = objectMapper.valueToTree(resp);
            JsonNode dataNode = tree.get("data");
            if (dataNode != null && !dataNode.isNull()) {
                String dataJson = dataNode.toString();
                if (dataJson.length() > 200) {
                    dataJson = dataJson.substring(0, 200) + "...";
                }
                sb.append("，数据：").append(dataJson);
            }
        } catch (Exception ignored) {
        }
        return sb.toString();
    }

    /**
     * 异常时的重试处理：业务异常直接失败，运行时异常按策略重试
     */
    private void handleRetry(OpTask task, Exception e) {
        // 业务异常（设备不存在、参数错误、操作码不支持等）属于永久性失败，不再重试
        if (e instanceof BizException) {
            task.setStatus(OpTaskStatus.FAILED.name());
            task.setResultMsg(e.getMessage());
            task.setFinishedAt(LocalDateTime.now());
            writeActionLog(task, "ERROR", "操作失败：" + task.getOperationCode() + "，" + e.getMessage());
            return;
        }

        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int maxRetry = task.getMaxRetry() == null ? 0 : task.getMaxRetry();
        if (retryCount >= maxRetry) {
            task.setStatus(OpTaskStatus.FAILED.name());
            task.setResultMsg("重试耗尽：" + e.getMessage());
            task.setFinishedAt(LocalDateTime.now());
            writeActionLog(task, "ERROR", "重试耗尽：" + task.getOperationCode() + "，" + e.getMessage());
        } else {
            int newRetry = retryCount + 1;
            task.setRetryCount(newRetry);
            task.setStatus(OpTaskStatus.PENDING.name());
            task.setScheduledAt(LocalDateTime.now().plusSeconds(RETRY_DELAY_BASE_SEC * newRetry));
            task.setResultMsg("等待第 " + newRetry + " 次重试：" + e.getMessage());
            writeActionLog(task, "WARN", "第 " + newRetry + " 次重试等待：" + task.getOperationCode() + "，" + e.getMessage());
        }
    }

    /**
     * 写入操作日志
     */
    private void writeActionLog(OpTask task, String level, String message) {
        try {
            ActionLog actionLog = new ActionLog();
            actionLog.setTaskId(task.getId());
            actionLog.setDeviceId(task.getDeviceId());
            actionLog.setActionType("OPERATION");
            actionLog.setLogLevel(level);
            actionLog.setMessage(message);
            actionLog.setCreatedAt(LocalDateTime.now());
            actionLogMapper.insert(actionLog);
        } catch (Exception ex) {
            log.warn("写入操作日志失败：taskId={}", task.getId(), ex);
        }
    }
}
