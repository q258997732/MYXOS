package bob.myxos.main.service.impl;

import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.AlarmEvent;
import bob.myxos.domain.mapper.AlarmEventMapper;
import bob.myxos.main.service.AlarmService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 告警事件业务实现
 */
@Service
@RequiredArgsConstructor
public class AlarmServiceImpl implements AlarmService {

    private final AlarmEventMapper alarmEventMapper;

    @Override
    @Transactional(readOnly = true)
    public Page<AlarmEvent> list(String status, Long deviceId, Long page, Long size) {
        LambdaQueryWrapper<AlarmEvent> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(AlarmEvent::getDeleted, 0);
        if (status != null && !status.isEmpty()) {
            wrapper.eq(AlarmEvent::getStatus, status);
        }
        if (deviceId != null) {
            wrapper.eq(AlarmEvent::getDeviceId, deviceId);
        }
        wrapper.orderByDesc(AlarmEvent::getFiredAt);
        return alarmEventMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resolve(Long id) {
        AlarmEvent event = alarmEventMapper.selectById(id);
        if (event == null || (event.getDeleted() != null && event.getDeleted() == 1)) {
            throw new BizException("告警事件不存在");
        }
        if ("RESOLVED".equals(event.getStatus())) {
            return;
        }
        AlarmEvent update = new AlarmEvent();
        update.setId(id);
        update.setStatus("RESOLVED");
        update.setResolvedAt(LocalDateTime.now());
        alarmEventMapper.updateById(update);
    }
}
