package bob.myxos.collector.execute;

import bob.myxos.common.enums.OpTaskStatus;
import bob.myxos.common.enums.OperationCode;
import bob.myxos.domain.entity.OpTask;
import bob.myxos.domain.mapper.OpTaskMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * 操作任务 Runner 工厂
 * <p>
 * 当前阶段为骨架实现：仅对少数操作类型记录成功日志，其余标记为未实现。
 * 后续任务会接入 MytosClient 真实调用。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpTaskRunnerFactory {

    /** 骨架阶段已支持的操作类型 */
    private static final Set<String> SUPPORTED = new HashSet<>(Arrays.asList(
            OperationCode.REBOOT.name(),
            OperationCode.ADB_ON.name(),
            OperationCode.ADB_OFF.name(),
            OperationCode.KEEPALIVE_ON.name(),
            OperationCode.KEEPALIVE_OFF.name()
    ));

    /** 重试间隔基数（秒）：实际延迟 = BASE * retryCount */
    private static final long RETRY_DELAY_BASE_SEC = 10L;

    private final OpTaskMapper opTaskMapper;

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
        try {
            String opCode = task.getOperationCode();
            if (opCode != null && SUPPORTED.contains(opCode)) {
                log.info("骨架阶段执行操作：taskId={}, deviceId={}, op={}",
                        task.getId(), task.getDeviceId(), opCode);
                task.setStatus(OpTaskStatus.SUCCESS.name());
                task.setResultMsg("执行成功（骨架阶段）");
                task.setFinishedAt(LocalDateTime.now());
            } else {
                log.warn("操作类型尚未实现：taskId={}, op={}", task.getId(), opCode);
                task.setStatus(OpTaskStatus.FAILED.name());
                task.setResultMsg("操作类型尚未实现：" + opCode);
                task.setFinishedAt(LocalDateTime.now());
            }
        } catch (Exception e) {
            log.error("操作任务执行异常：taskId={}", task.getId(), e);
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
     * 异常时的重试处理：超过最大重试置 FAILED，否则回到 PENDING 并延迟
     */
    private void handleRetry(OpTask task, Exception e) {
        int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
        int maxRetry = task.getMaxRetry() == null ? 0 : task.getMaxRetry();
        if (retryCount >= maxRetry) {
            task.setStatus(OpTaskStatus.FAILED.name());
            task.setResultMsg("重试耗尽：" + e.getMessage());
            task.setFinishedAt(LocalDateTime.now());
        } else {
            int newRetry = retryCount + 1;
            task.setRetryCount(newRetry);
            task.setStatus(OpTaskStatus.PENDING.name());
            task.setScheduledAt(LocalDateTime.now().plusSeconds(RETRY_DELAY_BASE_SEC * newRetry));
            task.setResultMsg("等待第 " + newRetry + " 次重试：" + e.getMessage());
        }
    }
}
