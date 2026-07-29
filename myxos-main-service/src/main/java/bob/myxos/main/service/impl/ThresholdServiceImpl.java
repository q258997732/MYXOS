package bob.myxos.main.service.impl;

import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.ThresholdAction;
import bob.myxos.domain.entity.ThresholdRule;
import bob.myxos.domain.mapper.ThresholdActionMapper;
import bob.myxos.domain.mapper.ThresholdRuleMapper;
import bob.myxos.main.dto.ThresholdRuleReq;
import bob.myxos.main.service.ThresholdService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

/**
 * 阈值规则业务实现
 */
@Service
@RequiredArgsConstructor
public class ThresholdServiceImpl implements ThresholdService {

    private final ThresholdRuleMapper ruleMapper;
    private final ThresholdActionMapper actionMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ThresholdRule create(ThresholdRuleReq req) {
        ThresholdRule rule = new ThresholdRule();
        rule.setName(req.getName());
        rule.setMetricType(req.getMetricType());
        rule.setCompareOp(req.getCompareOp());
        rule.setThresholdValue(req.getThresholdValue());
        rule.setTriggerMode(req.getTriggerMode());
        rule.setDurationSec(req.getDurationSec());
        rule.setConsecutiveCount(req.getConsecutiveCount());
        rule.setScopeType(req.getScopeType());
        rule.setScopeId(req.getScopeId());
        rule.setEnabled(1);
        ruleMapper.insert(rule);

        saveActions(rule.getId(), req.getActions());
        return rule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ThresholdRule update(Long id, ThresholdRuleReq req) {
        ThresholdRule existing = requireRule(id);

        ThresholdRule update = new ThresholdRule();
        update.setId(id);
        update.setName(req.getName());
        update.setMetricType(req.getMetricType());
        update.setCompareOp(req.getCompareOp());
        update.setThresholdValue(req.getThresholdValue());
        update.setTriggerMode(req.getTriggerMode());
        update.setDurationSec(req.getDurationSec());
        update.setConsecutiveCount(req.getConsecutiveCount());
        update.setScopeType(req.getScopeType());
        update.setScopeId(req.getScopeId());
        ruleMapper.updateById(update);

        // 逻辑删除旧动作
        List<ThresholdAction> oldActions = actionMapper.selectList(
                new LambdaQueryWrapper<ThresholdAction>()
                        .eq(ThresholdAction::getRuleId, id)
                        .eq(ThresholdAction::getDeleted, 0));
        if (oldActions != null && !oldActions.isEmpty()) {
            for (ThresholdAction a : oldActions) {
                actionMapper.deleteById(a.getId());
            }
        }

        // 插入新动作
        saveActions(id, req.getActions());

        existing.setName(req.getName());
        existing.setMetricType(req.getMetricType());
        existing.setCompareOp(req.getCompareOp());
        existing.setThresholdValue(req.getThresholdValue());
        existing.setTriggerMode(req.getTriggerMode());
        existing.setDurationSec(req.getDurationSec());
        existing.setConsecutiveCount(req.getConsecutiveCount());
        existing.setScopeType(req.getScopeType());
        existing.setScopeId(req.getScopeId());
        return existing;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggle(Long id) {
        ThresholdRule existing = requireRule(id);
        ThresholdRule update = new ThresholdRule();
        update.setId(id);
        update.setEnabled(existing.getEnabled() != null && existing.getEnabled() == 1 ? 0 : 1);
        ruleMapper.updateById(update);
    }

    @Override
    @Transactional(readOnly = true)
    public Page<ThresholdRule> list(String metricType, Integer enabled, Long page, Long size) {
        LambdaQueryWrapper<ThresholdRule> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ThresholdRule::getDeleted, 0);
        if (metricType != null && !metricType.isEmpty()) {
            wrapper.eq(ThresholdRule::getMetricType, metricType);
        }
        if (enabled != null) {
            wrapper.eq(ThresholdRule::getEnabled, enabled);
        }
        wrapper.orderByDesc(ThresholdRule::getWhenCreated);
        return ruleMapper.selectPage(new Page<>(page, size), wrapper);
    }

    @Override
    @Transactional(readOnly = true)
    public ThresholdRule detail(Long id) {
        return requireRule(id);
    }

    /**
     * 查询规则的动作列表（按 sort 升序）
     *
     * @param ruleId 规则 ID
     * @return 动作列表
     */
    public List<ThresholdAction> listActions(Long ruleId) {
        List<ThresholdAction> actions = actionMapper.selectList(
                new LambdaQueryWrapper<ThresholdAction>()
                        .eq(ThresholdAction::getRuleId, ruleId)
                        .eq(ThresholdAction::getDeleted, 0)
                        .orderByAsc(ThresholdAction::getSort)
                        .orderByAsc(ThresholdAction::getId));
        return actions == null ? Collections.emptyList() : actions;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireRule(id);
        // 逻辑删除规则
        ruleMapper.deleteById(id);
        // 逻辑删除关联动作
        List<ThresholdAction> actions = actionMapper.selectList(
                new LambdaQueryWrapper<ThresholdAction>()
                        .eq(ThresholdAction::getRuleId, id)
                        .eq(ThresholdAction::getDeleted, 0));
        if (actions != null && !actions.isEmpty()) {
            for (ThresholdAction a : actions) {
                actionMapper.deleteById(a.getId());
            }
        }
    }

    /**
     * 校验规则存在
     *
     * @param id 规则 ID
     * @return 规则实体
     */
    private ThresholdRule requireRule(Long id) {
        ThresholdRule rule = ruleMapper.selectById(id);
        if (rule == null || (rule.getDeleted() != null && rule.getDeleted() == 1)) {
            throw new BizException("阈值规则不存在");
        }
        return rule;
    }

    /**
     * 保存动作列表
     *
     * @param ruleId  规则 ID
     * @param actions 动作请求列表
     */
    private void saveActions(Long ruleId, List<ThresholdRuleReq.ThresholdActionReq> actions) {
        if (actions == null || actions.isEmpty()) {
            return;
        }
        int sort = 0;
        for (ThresholdRuleReq.ThresholdActionReq req : actions) {
            ThresholdAction action = new ThresholdAction();
            action.setRuleId(ruleId);
            action.setActionType(req.getActionType());
            action.setLogLevel(req.getLogLevel());
            action.setOperationCode(req.getOperationCode());
            action.setOperationParams(req.getOperationParams());
            action.setSort(req.getSort() != null ? req.getSort() : sort++);
            actionMapper.insert(action);
        }
    }
}
