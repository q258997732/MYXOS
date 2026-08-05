package bob.myxos.main.service.impl;

import bob.myxos.common.enums.ActionType;
import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.ActionLog;
import bob.myxos.domain.entity.AlarmEvent;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.ThresholdRule;
import bob.myxos.domain.mapper.ActionLogMapper;
import bob.myxos.domain.mapper.AlarmEventMapper;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.domain.mapper.ThresholdRuleMapper;
import bob.myxos.main.service.AlarmService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 告警事件业务实现
 */
@Service
@RequiredArgsConstructor
public class AlarmServiceImpl implements AlarmService {

    private final AlarmEventMapper alarmEventMapper;
    private final DeviceMapper deviceMapper;
    private final ThresholdRuleMapper thresholdRuleMapper;
    private final ActionLogMapper actionLogMapper;

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
        Page<AlarmEvent> result = alarmEventMapper.selectPage(new Page<>(page, size), wrapper);
        fillDisplayFields(result.getRecords());
        return result;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void clear() {
        // BaseMapper.delete 触发 @TableLogic 逻辑删除；isNotNull 条件避免全表无条件更新被拦截
        alarmEventMapper.delete(new LambdaQueryWrapper<AlarmEvent>()
                .isNotNull(AlarmEvent::getId));
    }

    /**
     * 批量填充展示字段：设备名称、规则名称、告警级别（取该告警最新一条 LOG 动作的日志级别）
     *
     * @param records 当前页告警记录
     */
    private void fillDisplayFields(List<AlarmEvent> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        Set<Long> deviceIds = records.stream().map(AlarmEvent::getDeviceId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> deviceNameMap = deviceIds.isEmpty()
                ? Collections.emptyMap()
                : deviceMapper.selectBatchIds(deviceIds).stream()
                        .collect(Collectors.toMap(Device::getId,
                                d -> d.getName() != null && !d.getName().isEmpty() ? d.getName() : d.getIp()));

        Set<Long> ruleIds = records.stream().map(AlarmEvent::getRuleId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> ruleNameMap = ruleIds.isEmpty()
                ? Collections.emptyMap()
                : thresholdRuleMapper.selectBatchIds(ruleIds).stream()
                        .collect(Collectors.toMap(ThresholdRule::getId, ThresholdRule::getName));

        Set<Long> alarmIds = records.stream().map(AlarmEvent::getId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<Long, String> levelMap = new HashMap<>();
        if (!alarmIds.isEmpty()) {
            List<ActionLog> logs = actionLogMapper.selectList(new LambdaQueryWrapper<ActionLog>()
                    .in(ActionLog::getAlarmId, alarmIds)
                    .eq(ActionLog::getActionType, ActionType.LOG.name())
                    .orderByDesc(ActionLog::getId));
            // 按 id 倒序遍历，putIfAbsent 保留每条告警最新一条 LOG 级别
            for (ActionLog actionLog : logs) {
                levelMap.putIfAbsent(actionLog.getAlarmId(), actionLog.getLogLevel());
            }
        }

        for (AlarmEvent record : records) {
            record.setDeviceName(deviceNameMap.get(record.getDeviceId()));
            record.setRuleName(ruleNameMap.get(record.getRuleId()));
            record.setLevel(levelMap.get(record.getId()));
        }
    }
}
