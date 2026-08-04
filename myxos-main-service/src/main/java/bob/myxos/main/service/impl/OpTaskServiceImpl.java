package bob.myxos.main.service.impl;

import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.OpTask;
import bob.myxos.domain.mapper.OpTaskMapper;
import bob.myxos.main.service.OpTaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 操作任务业务实现
 */
@Service
@RequiredArgsConstructor
public class OpTaskServiceImpl implements OpTaskService {

    private final OpTaskMapper opTaskMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<OpTask> list(String status, String source, Long page, Long size) {
        LambdaQueryWrapper<OpTask> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(OpTask::getDeleted, 0);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(OpTask::getStatus, status);
        }
        if (source != null && !source.isEmpty()) {
            wrapper.eq(OpTask::getSource, source);
        }
        wrapper.orderByDesc(OpTask::getScheduledAt);
        return opTaskMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    public OpTask getById(Long id) {
        OpTask task = opTaskMapper.selectById(id);
        if (task == null || (task.getDeleted() != null && task.getDeleted() == 1)) {
            throw new BizException("任务不存在");
        }
        return task;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void retry(Long id) {
        OpTask task = getById(id);
        if (!"FAILED".equals(task.getStatus()) && !"SUCCESS".equals(task.getStatus())) {
            throw new BizException("只有失败或成功状态的任务可以重试");
        }
        OpTask update = new OpTask();
        update.setId(id);
        update.setStatus("PENDING");
        update.setRetryCount(0);
        update.setResultMsg(null);
        update.setScheduledAt(LocalDateTime.now());
        update.setStartedAt(null);
        update.setFinishedAt(null);
        opTaskMapper.updateById(update);
    }
}
