package bob.myxos.collector.execute;

import bob.myxos.common.enums.OpTaskStatus;
import bob.myxos.domain.entity.OpTask;
import bob.myxos.domain.mapper.OpTaskMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 操作任务执行调度器
 * <p>
 * 周期性扫描 PENDING 且到期的任务，CAS 抢占后提交到 {@code opTaskExecutor} 线程池执行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OpTaskExecuteJob {

    /** 单批处理上限 */
    private static final int BATCH_LIMIT = 50;

    private final OpTaskMapper opTaskMapper;
    private final OpTaskRunnerFactory runnerFactory;
    private final ThreadPoolTaskExecutor opTaskExecutor;

    /**
     * 每 2 秒扫描一次
     */
    @Scheduled(fixedDelay = 2000)
    public void run() {
        List<OpTask> pending = queryPendingTasks();
        if (pending.isEmpty()) {
            return;
        }
        for (OpTask task : pending) {
            try {
                int updated = opTaskMapper.claimPending(task.getId());
                if (updated <= 0) {
                    continue;
                }
                // 重新读取，确保拿到最新状态（含 startedAt）
                OpTask claimed = opTaskMapper.selectById(task.getId());
                opTaskExecutor.execute(runnerFactory.create(claimed == null ? task : claimed));
            } catch (Exception e) {
                log.error("调度操作任务异常：taskId={}", task.getId(), e);
            }
        }
    }

    /**
     * 查询待执行任务
     */
    private List<OpTask> queryPendingTasks() {
        QueryWrapper<OpTask> query = new QueryWrapper<>();
        query.eq("status", OpTaskStatus.PENDING.name())
                .le("scheduled_at", LocalDateTime.now())
                .orderByAsc("id")
                .last("LIMIT " + BATCH_LIMIT);
        List<OpTask> list = opTaskMapper.selectList(query);
        return list == null ? java.util.Collections.emptyList() : list;
    }
}
