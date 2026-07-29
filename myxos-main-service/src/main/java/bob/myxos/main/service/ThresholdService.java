package bob.myxos.main.service;

import bob.myxos.domain.entity.ThresholdRule;
import bob.myxos.main.dto.ThresholdRuleReq;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;

/**
 * 阈值规则业务接口
 */
public interface ThresholdService {

    /**
     * 创建阈值规则及其动作
     *
     * @param req 创建请求
     * @return 已创建的规则
     */
    ThresholdRule create(ThresholdRuleReq req);

    /**
     * 更新阈值规则及其动作（旧动作逻辑删除，新动作全量重建）
     *
     * @param id  规则 ID
     * @param req 更新请求
     * @return 更新后的规则
     */
    ThresholdRule update(Long id, ThresholdRuleReq req);

    /**
     * 切换启用状态（1 ↔ 0）
     *
     * @param id 规则 ID
     */
    void toggle(Long id);

    /**
     * 分页查询规则
     *
     * @param metricType 指标类型（可选）
     * @param enabled    启用状态（可选）
     * @param page       当前页
     * @param size       每页大小
     * @return 分页结果
     */
    Page<ThresholdRule> list(String metricType, Integer enabled, Long page, Long size);

    /**
     * 查询规则详情（含动作列表，按 sort 升序）
     *
     * @param id 规则 ID
     * @return 规则实体（动作通过扩展字段返回）
     */
    ThresholdRule detail(Long id);

    /**
     * 删除规则及其动作（逻辑删除）
     *
     * @param id 规则 ID
     */
    void delete(Long id);
}
