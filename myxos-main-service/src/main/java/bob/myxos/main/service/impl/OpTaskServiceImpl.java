package bob.myxos.main.service.impl;

import bob.myxos.domain.entity.OpTask;
import bob.myxos.domain.mapper.OpTaskMapper;
import bob.myxos.main.service.OpTaskService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

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
}
