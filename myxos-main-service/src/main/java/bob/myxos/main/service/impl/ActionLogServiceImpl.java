package bob.myxos.main.service.impl;

import bob.myxos.domain.entity.ActionLog;
import bob.myxos.domain.mapper.ActionLogMapper;
import bob.myxos.main.service.ActionLogService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 动作日志业务实现
 */
@Service
@RequiredArgsConstructor
public class ActionLogServiceImpl implements ActionLogService {

    private final ActionLogMapper actionLogMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<ActionLog> list(String actionType, String logLevel, Long page, Long size) {
        LambdaQueryWrapper<ActionLog> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ActionLog::getDeleted, 0);
        if (actionType != null && !actionType.isEmpty()) {
            wrapper.eq(ActionLog::getActionType, actionType);
        }
        if (logLevel != null && !logLevel.isEmpty()) {
            wrapper.eq(ActionLog::getLogLevel, logLevel);
        }
        wrapper.orderByDesc(ActionLog::getCreatedAt);
        return actionLogMapper.selectPage(new Page<>(page, size), wrapper);
    }
}
