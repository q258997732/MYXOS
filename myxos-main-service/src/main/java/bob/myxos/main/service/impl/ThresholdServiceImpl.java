package bob.myxos.main.service.impl;

import bob.myxos.common.enums.CompareOp;
import bob.myxos.common.enums.ConditionType;
import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.ThresholdAction;
import bob.myxos.domain.entity.ThresholdRule;
import bob.myxos.domain.mapper.ThresholdActionMapper;
import bob.myxos.domain.mapper.ThresholdRuleMapper;
import bob.myxos.main.dto.ThresholdRuleReq;
import bob.myxos.main.service.ThresholdService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

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
        normalizeCondition(req);
        ThresholdRule rule = new ThresholdRule();
        rule.setName(req.getName());
        rule.setMetricType(req.getMetricType());
        rule.setConditionType(req.getConditionType());
        rule.setCompareOp(req.getCompareOp());
        rule.setThresholdValue(req.getThresholdValue());
        rule.setThresholdText(req.getThresholdText());
        rule.setTriggerMode(req.getTriggerMode());
        rule.setDurationSec(req.getDurationSec());
        rule.setConsecutiveCount(req.getConsecutiveCount());
        rule.setScopeType(req.getScopeType());
        rule.setScopeId(req.getScopeId());
        rule.setScopeIds(joinScopeIds(req.getScopeIds()));
        rule.setScopeAndroidName(normalizeScopeAndroidName(req));
        rule.setEnabled(1);
        ruleMapper.insert(rule);

        saveActions(rule.getId(), req.getActions());
        return rule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ThresholdRule update(Long id, ThresholdRuleReq req) {
        ThresholdRule existing = requireRule(id);
        normalizeCondition(req);

        // 使用 UpdateWrapper 显式 set：thresholdValue/thresholdText/scopeId/scopeIds/scopeAndroidName
        // 均可为 null，updateById 会忽略 null 字段导致旧值残留（如切换条件类型后旧阈值清不掉）
        ruleMapper.update(null, new LambdaUpdateWrapper<ThresholdRule>()
                .eq(ThresholdRule::getId, id)
                .set(ThresholdRule::getName, req.getName())
                .set(ThresholdRule::getMetricType, req.getMetricType())
                .set(ThresholdRule::getConditionType, req.getConditionType())
                .set(ThresholdRule::getCompareOp, req.getCompareOp())
                .set(ThresholdRule::getThresholdValue, req.getThresholdValue())
                .set(ThresholdRule::getThresholdText, req.getThresholdText())
                .set(ThresholdRule::getTriggerMode, req.getTriggerMode())
                .set(ThresholdRule::getDurationSec, req.getDurationSec())
                .set(ThresholdRule::getConsecutiveCount, req.getConsecutiveCount())
                .set(ThresholdRule::getScopeType, req.getScopeType())
                .set(ThresholdRule::getScopeId, req.getScopeId())
                .set(ThresholdRule::getScopeIds, joinScopeIds(req.getScopeIds()))
                .set(ThresholdRule::getScopeAndroidName, normalizeScopeAndroidName(req)));

        // 逻辑删除旧动作（批量更新）
        ThresholdAction deleted = new ThresholdAction();
        deleted.setDeleted(1);
        actionMapper.update(deleted, new LambdaQueryWrapper<ThresholdAction>()
                .eq(ThresholdAction::getRuleId, id)
                .eq(ThresholdAction::getDeleted, 0));

        // 插入新动作
        saveActions(id, req.getActions());

        existing.setName(req.getName());
        existing.setMetricType(req.getMetricType());
        existing.setConditionType(req.getConditionType());
        existing.setCompareOp(req.getCompareOp());
        existing.setThresholdValue(req.getThresholdValue());
        existing.setThresholdText(req.getThresholdText());
        existing.setTriggerMode(req.getTriggerMode());
        existing.setDurationSec(req.getDurationSec());
        existing.setConsecutiveCount(req.getConsecutiveCount());
        existing.setScopeType(req.getScopeType());
        existing.setScopeId(req.getScopeId());
        existing.setScopeIds(joinScopeIds(req.getScopeIds()));
        existing.setScopeAndroidName(normalizeScopeAndroidName(req));
        return existing;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void toggle(Long id, Integer enabled) {
        ThresholdRule existing = requireRule(id);
        ThresholdRule update = new ThresholdRule();
        update.setId(id);
        int target = enabled != null ? enabled : (existing.getEnabled() != null && existing.getEnabled() == 1 ? 0 : 1);
        update.setEnabled(target);
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
        // 逻辑删除关联动作（批量更新）
        ThresholdAction deleted = new ThresholdAction();
        deleted.setDeleted(1);
        actionMapper.update(deleted, new LambdaQueryWrapper<ThresholdAction>()
                .eq(ThresholdAction::getRuleId, id)
                .eq(ThresholdAction::getDeleted, 0));
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
     * 按条件类型校验并规范化触发条件
     * <ul>
     *   <li>NUMERIC：比较操作限 GT/GTE/LT/LTE/EQ/NE，阈值必填</li>
     *   <li>STRING：比较操作限 EQ/NE/CONTAINS，目标文本必填</li>
     *   <li>NONE：无需条件，统一规范化为 GTE 1（采集侧状态快照以 1/0 表示是否命中）</li>
     * </ul>
     *
     * @param req 规则请求
     */
    private void normalizeCondition(ThresholdRuleReq req) {
        String conditionType = req.getConditionType() == null || req.getConditionType().trim().isEmpty()
                ? ConditionType.NUMERIC.name() : req.getConditionType().trim().toUpperCase();
        ConditionType type;
        try {
            type = ConditionType.valueOf(conditionType);
        } catch (IllegalArgumentException e) {
            throw new BizException("未知的条件类型: " + conditionType);
        }
        switch (type) {
            case NUMERIC:
                if (!isNumericCompareOp(req.getCompareOp())) {
                    throw new BizException("数值判断的比较操作仅支持 GT/GTE/LT/LTE/EQ/NE");
                }
                if (req.getThresholdValue() == null) {
                    throw new BizException("数值判断的阈值不能为空");
                }
                req.setThresholdText(null);
                break;
            case STRING:
                if (!isStringCompareOp(req.getCompareOp())) {
                    throw new BizException("字符判断的比较操作仅支持 EQ/NE/CONTAINS");
                }
                if (req.getThresholdText() == null || req.getThresholdText().trim().isEmpty()) {
                    throw new BizException("字符判断的目标文本不能为空");
                }
                req.setThresholdValue(null);
                break;
            case NONE:
                // 状态类指标（如设备离线）检测到即触发，规范化为数值 1 比较
                req.setCompareOp(CompareOp.GTE.name());
                req.setThresholdValue(BigDecimal.ONE);
                req.setThresholdText(null);
                break;
            default:
                throw new BizException("未知的条件类型: " + conditionType);
        }
        req.setConditionType(conditionType);
    }

    private boolean isNumericCompareOp(String compareOp) {
        return CompareOp.GT.name().equals(compareOp) || CompareOp.GTE.name().equals(compareOp)
                || CompareOp.LT.name().equals(compareOp) || CompareOp.LTE.name().equals(compareOp)
                || CompareOp.EQ.name().equals(compareOp) || CompareOp.NE.name().equals(compareOp);
    }

    private boolean isStringCompareOp(String compareOp) {
        return CompareOp.EQ.name().equals(compareOp) || CompareOp.NE.name().equals(compareOp)
                || CompareOp.CONTAINS.name().equals(compareOp);
    }

    /**
     * 将设备 ID 列表序列化为逗号分隔字符串
     *
     * @param scopeIds 设备 ID 列表
     * @return 逗号分隔字符串，空列表返回 null
     */
    private String joinScopeIds(List<Long> scopeIds) {
        if (scopeIds == null || scopeIds.isEmpty()) {
            return null;
        }
        return scopeIds.stream()
                .filter(id -> id != null)
                .map(String::valueOf)
                .collect(Collectors.joining(","));
    }

    /**
     * 规范化安卓实例名：仅 ANDROID_STATUS 指标保留，其余指标强制置空；空白串按 null 处理
     *
     * @param req 规则请求
     * @return 规范化后的实例名，可能为 null
     */
    private String normalizeScopeAndroidName(ThresholdRuleReq req) {
        if (req.getScopeAndroidName() == null || req.getScopeAndroidName().trim().isEmpty()) {
            return null;
        }
        if (!"ANDROID_STATUS".equals(req.getMetricType())) {
            return null;
        }
        return req.getScopeAndroidName().trim();
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
            // 后端统一覆盖 sort，保证顺序稳定
            action.setSort(sort++);
            actionMapper.insert(action);
        }
    }
}
