package bob.myxos.collector.evaluate;

import bob.myxos.domain.entity.ThresholdAction;
import bob.myxos.domain.entity.ThresholdRule;
import bob.myxos.domain.mapper.ThresholdActionMapper;
import bob.myxos.domain.mapper.ThresholdRuleMapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 阈值规则缓存
 * <p>
 * 加载所有启用的阈值规则及其动作，按 metricType 分组缓存，
 * 避免每次评估都查询数据库。提供 {@link #refresh()} 用于手动或定时刷新。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RuleCache {

    private final ThresholdRuleMapper ruleMapper;
    private final ThresholdActionMapper actionMapper;

    /** metricCode（历史规则回退 metricType） -> 规则与动作列表 */
    private final Map<String, List<RuleWithActions>> cache = new ConcurrentHashMap<>();

    /**
     * 启动时加载一次
     */
    @PostConstruct
    public void init() {
        refresh();
    }

    /**
     * 定时刷新：主服务对阈值规则的增删改通过共享数据库生效，
     * 采集服务按固定间隔重新加载，避免重启后才生效
     */
    @Scheduled(fixedDelayString = "${myxos.collector.rule-refresh-ms:60000}")
    public void scheduledRefresh() {
        try {
            refresh();
        } catch (Exception e) {
            log.error("定时刷新阈值规则缓存失败", e);
        }
    }

    /**
     * 重新加载所有启用规则及其动作
     */
    public synchronized void refresh() {
        QueryWrapper<ThresholdRule> ruleQuery = new QueryWrapper<>();
        ruleQuery.eq("enabled", 1);
        List<ThresholdRule> rules = ruleMapper.selectList(ruleQuery);

        Map<String, List<RuleWithActions>> newCache = new HashMap<>();
        if (rules != null && !rules.isEmpty()) {
            List<Long> ruleIds = rules.stream().map(ThresholdRule::getId).collect(Collectors.toList());
            QueryWrapper<ThresholdAction> actionQuery = new QueryWrapper<>();
            actionQuery.in("rule_id", ruleIds).orderByAsc("sort");
            List<ThresholdAction> actions = actionMapper.selectList(actionQuery);
            Map<Long, List<ThresholdAction>> actionsByRule = actions == null
                    ? Collections.emptyMap()
                    : actions.stream().collect(Collectors.groupingBy(ThresholdAction::getRuleId));

            for (ThresholdRule rule : rules) {
                RuleWithActions rwa = new RuleWithActions();
                rwa.rule = rule;
                rwa.actions = actionsByRule.getOrDefault(rule.getId(), Collections.emptyList());
                String metricKey = rule.getMetricCode() == null || rule.getMetricCode().trim().isEmpty()
                        ? rule.getMetricType() : rule.getMetricCode();
                newCache.computeIfAbsent(metricKey, k -> new ArrayList<>()).add(rwa);
            }
        }

        cache.clear();
        cache.putAll(newCache);
        log.info("阈值规则缓存已刷新：规则数={}，指标类型数={}",
                rules == null ? 0 : rules.size(), cache.size());
    }

    /**
     * 按指标类型获取规则与动作列表
     *
     * @param metricType 指标类型
     * @return 规则列表，不存在返回空列表
     */
    public List<RuleWithActions> getByMetricType(String metricType) {
        return cache.getOrDefault(metricType, Collections.emptyList());
    }

    /** 按稳定指标编码读取规则。 */
    public List<RuleWithActions> getByMetricCode(String metricCode) {
        return cache.getOrDefault(metricCode, Collections.emptyList());
    }

    /**
     * 规则与其动作的组合
     */
    @Getter
    public static class RuleWithActions {
        /** 规则 */
        private ThresholdRule rule;
        /** 该规则下的动作（按 sort 升序） */
        private List<ThresholdAction> actions;

        public RuleWithActions() {
        }

        public RuleWithActions(ThresholdRule rule, List<ThresholdAction> actions) {
            this.rule = rule;
            this.actions = actions;
        }
    }
}
