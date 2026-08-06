package bob.myxos.main.service.impl;

import bob.myxos.common.enums.CompareOp;
import bob.myxos.common.enums.ConditionType;
import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.ThresholdAction;
import bob.myxos.domain.entity.ThresholdRule;
import bob.myxos.domain.entity.MetricCatalog;
import bob.myxos.domain.entity.MetricBinding;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.MetricTemplateItem;
import bob.myxos.domain.mapper.MetricCatalogMapper;
import bob.myxos.domain.mapper.MetricBindingMapper;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.domain.mapper.MetricTemplateItemMapper;
import bob.myxos.domain.mapper.ThresholdActionMapper;
import bob.myxos.domain.mapper.ThresholdRuleMapper;
import bob.myxos.main.dto.ThresholdRuleReq;
import bob.myxos.main.dto.ThresholdMetricOptionExecuteReq;
import bob.myxos.main.metric.MetricDefinitionRegistry;
import bob.myxos.main.metric.MetricDefinition;
import bob.myxos.main.metric.AndroidMetricParser;
import bob.myxos.main.service.DeviceService;
import bob.myxos.main.service.ThresholdService;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 阈值规则业务实现
 */
@Service
@RequiredArgsConstructor
public class ThresholdServiceImpl implements ThresholdService {

    private final ThresholdRuleMapper ruleMapper;
    private final ThresholdActionMapper actionMapper;
    private final MetricCatalogMapper catalogMapper;
    private final MetricTemplateItemMapper templateItemMapper;
    private final MetricBindingMapper bindingMapper;
    private final DeviceMapper deviceMapper;
    private final DeviceService deviceService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AndroidMetricParser androidMetricParser = new AndroidMetricParser();

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ThresholdRule create(ThresholdRuleReq req) {
        MetricCatalog catalog = resolveCatalog(req);
        normalizeCondition(req, catalog);
        validateAppProcessBinding(req, catalog);
        ThresholdRule rule = new ThresholdRule();
        rule.setName(req.getName());
        rule.setMetricType(resolveMetricType(req));
        rule.setMetricCode(resolveMetricCode(req));
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
        rule.setScopeAppPackage(normalizeScopeAppPackage(req));
        rule.setEnabled(1);
        ruleMapper.insert(rule);

        saveActions(rule.getId(), req.getActions());
        return rule;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ThresholdRule update(Long id, ThresholdRuleReq req) {
        ThresholdRule existing = requireRule(id);
        MetricCatalog catalog = resolveCatalog(req);
        normalizeCondition(req, catalog);
        validateAppProcessBinding(req, catalog);
        boolean durationMode = "DURATION".equals(req.getTriggerMode());
        boolean consecutiveMode = "CONSECUTIVE".equals(req.getTriggerMode());

        // 使用 UpdateWrapper 显式 set：thresholdValue/thresholdText/scopeId/scopeIds/scopeAndroidName
        // 均可为 null，updateById 会忽略 null 字段导致旧值残留（如切换条件类型后旧阈值清不掉）
        ruleMapper.update(null, new LambdaUpdateWrapper<ThresholdRule>()
                .eq(ThresholdRule::getId, id)
                .set(ThresholdRule::getName, req.getName())
                .set(ThresholdRule::getMetricType, resolveMetricType(req))
                .set(ThresholdRule::getMetricCode, resolveMetricCode(req))
                .set(ThresholdRule::getConditionType, req.getConditionType())
                .set(ThresholdRule::getCompareOp, req.getCompareOp())
                .set(ThresholdRule::getThresholdValue, req.getThresholdValue())
                .set(ThresholdRule::getThresholdText, req.getThresholdText())
                .set(ThresholdRule::getTriggerMode, req.getTriggerMode())
                .set(!durationMode || req.getDurationSec() != null, ThresholdRule::getDurationSec,
                        durationMode ? req.getDurationSec() : null)
                .set(!consecutiveMode || req.getConsecutiveCount() != null, ThresholdRule::getConsecutiveCount,
                        consecutiveMode ? req.getConsecutiveCount() : null)
                .set(ThresholdRule::getScopeType, req.getScopeType())
                .set(ThresholdRule::getScopeId, req.getScopeId())
                .set(ThresholdRule::getScopeIds, joinScopeIds(req.getScopeIds()))
                .set(ThresholdRule::getScopeAndroidName, normalizeScopeAndroidName(req))
                .set(ThresholdRule::getScopeAppPackage, normalizeScopeAppPackage(req)));

        // 逻辑删除旧动作：必须走 BaseMapper.delete，@TableLogic 才会生效生成
        // UPDATE ... SET deleted=1；此前用 update(entity) 设置 deleted=1，
        // MyBatis-Plus 会在 SET 子句中忽略逻辑删除字段，导致旧动作从未被删除而不断累积
        actionMapper.delete(new LambdaQueryWrapper<ThresholdAction>()
                .eq(ThresholdAction::getRuleId, id));

        // 插入新动作
        saveActions(id, req.getActions());

        existing.setName(req.getName());
        existing.setMetricType(resolveMetricType(req));
        existing.setMetricCode(resolveMetricCode(req));
        existing.setConditionType(req.getConditionType());
        existing.setCompareOp(req.getCompareOp());
        existing.setThresholdValue(req.getThresholdValue());
        existing.setThresholdText(req.getThresholdText());
        existing.setTriggerMode(req.getTriggerMode());
        existing.setDurationSec(durationMode && req.getDurationSec() == null
                ? existing.getDurationSec() : (durationMode ? req.getDurationSec() : null));
        existing.setConsecutiveCount(consecutiveMode && req.getConsecutiveCount() == null
                ? existing.getConsecutiveCount() : (consecutiveMode ? req.getConsecutiveCount() : null));
        existing.setScopeType(req.getScopeType());
        existing.setScopeId(req.getScopeId());
        existing.setScopeIds(joinScopeIds(req.getScopeIds()));
        existing.setScopeAndroidName(normalizeScopeAndroidName(req));
        existing.setScopeAppPackage(normalizeScopeAppPackage(req));
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
    @Transactional(readOnly = true)
    public List<String> listEnumOptions(String metricCode) {
        MetricCatalog catalog = requireCatalog(metricCode);
        if (!"ENUM".equals(catalog.getValueType())) {
            throw new BizException("该指标不是枚举类型");
        }
        return MetricDefinitionRegistry.APP_PROCESS_STATE.equals(metricCode)
                ? Arrays.asList("FOREGROUND", "ACTIVE", "RUNNING", "STOPPED")
                : collectEnumOptions(catalog.getId());
    }

    @Override
    @Transactional(readOnly = true)
    public List<MetricCatalog> listMetricCandidates(String scopeType, Long scopeId, List<Long> scopeIds,
                                                     String scopeAndroidName, String scopeAppPackage) {
        Set<Long> deviceIds = resolveScopeDeviceIds(scopeType, scopeId, scopeIds);
        if (deviceIds != null && deviceIds.isEmpty()) {
            return Collections.emptyList();
        }
        LambdaQueryWrapper<MetricBinding> bindingQuery = new LambdaQueryWrapper<MetricBinding>()
                .eq(MetricBinding::getDeleted, 0).eq(MetricBinding::getEnabled, 1);
        if (deviceIds != null) {
            bindingQuery.in(MetricBinding::getDeviceId, deviceIds);
        }
        List<String> androidNames = splitAndroidNames(scopeAndroidName);
        if (!androidNames.isEmpty()) {
            bindingQuery.in(MetricBinding::getAndroidName, androidNames);
        }
        String appPackage = trimToEmpty(scopeAppPackage);
        if (!appPackage.isEmpty()) {
            bindingQuery.eq(MetricBinding::getAppPackage, appPackage);
        }
        List<MetricBinding> bindings = bindingMapper.selectList(bindingQuery);
        if (bindings == null || bindings.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> codes = bindings.stream().map(MetricBinding::getMetricCode)
                .filter(code -> code != null && !code.trim().isEmpty()).collect(Collectors.toSet());
        if (androidNames.isEmpty() || appPackage.isEmpty()) {
            codes.remove(MetricDefinitionRegistry.APP_PROCESS_STATE);
        }
        if (codes.isEmpty()) {
            return Collections.emptyList();
        }
        return catalogMapper.selectList(new LambdaQueryWrapper<MetricCatalog>()
                .in(MetricCatalog::getCode, codes).eq(MetricCatalog::getDeleted, 0)
                .eq(MetricCatalog::getThresholdEnabled, 1).orderByAsc(MetricCatalog::getTargetType)
                .orderByAsc(MetricCatalog::getCategory).orderByAsc(MetricCatalog::getCode));
    }

    @Override
    @Transactional(readOnly = true)
    public List<String> executeEnumOptions(ThresholdMetricOptionExecuteReq req) {
        MetricCatalog catalog = requireCatalog(req.getMetricCode());
        if (!"ENUM".equals(catalog.getValueType())) {
            throw new BizException("该指标不是枚举类型");
        }
        MetricDefinition definition = MetricDefinitionRegistry.findByCode(catalog.getCode())
                .orElseThrow(() -> new BizException("指标未在受控目录中定义"));
        String command = MetricDefinitionRegistry.findReadOnlyAdbCommand(definition.getCommandKey())
                .orElseThrow(() -> new BizException("指标命令未获授权"));
        String androidName = req.getAndroidName().trim();
        String appPackage = req.getAppPackage().trim();
        MetricBinding binding = bindingMapper.selectOne(new LambdaQueryWrapper<MetricBinding>()
                .eq(MetricBinding::getDeviceId, req.getDeviceId()).eq(MetricBinding::getMetricCode, catalog.getCode())
                .eq(MetricBinding::getAndroidName, androidName).eq(MetricBinding::getAppPackage, appPackage)
                .eq(MetricBinding::getEnabled, 1).eq(MetricBinding::getDeleted, 0));
        if (binding == null) {
            throw new BizException("指标未在指定设备、安卓实例和应用包名上启用");
        }
        String output;
        try {
            output = deviceService.executeShell(req.getDeviceId(), androidName, command);
        } catch (RuntimeException e) {
            throw new BizException("受控指标命令执行失败");
        }
        if (output == null || output.trim().isEmpty()) {
            throw new BizException("受控指标命令未返回有效输出");
        }
        if (MetricDefinitionRegistry.APP_PROCESS_STATE.equals(catalog.getCode())) {
            return Collections.singletonList(androidMetricParser.parseAppProcessState(output, appPackage)
                    .orElseThrow(() -> new BizException("应用进程状态输出无法解析")).getStatus());
        }
        throw new BizException("该枚举指标不支持受控即时读取");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        requireRule(id);
        // 逻辑删除规则
        ruleMapper.deleteById(id);
        // 逻辑删除关联动作（BaseMapper.delete 触发 @TableLogic 逻辑删除）
        actionMapper.delete(new LambdaQueryWrapper<ThresholdAction>()
                .eq(ThresholdAction::getRuleId, id));
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
    private void normalizeCondition(ThresholdRuleReq req, MetricCatalog catalog) {
        if (catalog != null) {
            if (catalog.getThresholdEnabled() == null || catalog.getThresholdEnabled() != 1) {
                throw new BizException("该指标不支持配置阈值");
            }
            normalizeCatalogCondition(req, catalog);
            return;
        }
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

    private void normalizeCatalogCondition(ThresholdRuleReq req, MetricCatalog catalog) {
        String valueType = catalog.getValueType();
        if ("NUMBER".equals(valueType)) {
            req.setConditionType(ConditionType.NUMERIC.name());
            if (!isNumericCompareOp(req.getCompareOp()) || req.getThresholdValue() == null) {
                throw new BizException("数值指标需配置有效的数值比较条件");
            }
            req.setThresholdText(null);
            req.setThresholdOptions(null);
            return;
        }
        if ("STRING".equals(valueType)) {
            req.setConditionType(ConditionType.STRING.name());
            if (!isStringCompareOp(req.getCompareOp()) || req.getThresholdText() == null || req.getThresholdText().trim().isEmpty()) {
                throw new BizException("字符串指标需配置有效的文本比较条件");
            }
            req.setThresholdValue(null);
            req.setThresholdOptions(null);
            return;
        }
        if ("ENUM".equals(valueType)) {
            if (!isEnumCompareOp(req.getCompareOp())) {
                throw new BizException("枚举指标仅支持 EQ/NE/IN/NOT_IN");
            }
            List<String> values = parseEnumRequestOptions(req);
            if (values.isEmpty()) {
                throw new BizException("枚举指标必须选择至少一个选项");
            }
            try {
                String thresholdText = objectMapper.writeValueAsString(values);
                req.setThresholdText(thresholdText);
            } catch (Exception e) {
                throw new BizException("枚举阈值序列化失败");
            }
            if (req.getThresholdText().getBytes(StandardCharsets.UTF_8).length > 65535) {
                throw new BizException("枚举阈值序列化后超过存储容量");
            }
            req.setConditionType(ConditionType.ENUM.name());
            req.setThresholdValue(null);
            return;
        }
        throw new BizException("未知的指标值类型: " + valueType);
    }

    private boolean isNumericCompareOp(String compareOp) {
        return CompareOp.GT.name().equals(compareOp) || CompareOp.GTE.name().equals(compareOp)
                || CompareOp.LT.name().equals(compareOp) || CompareOp.LTE.name().equals(compareOp)
                || CompareOp.EQ.name().equals(compareOp) || CompareOp.NE.name().equals(compareOp);
    }

    private boolean isStringCompareOp(String compareOp) {
        return CompareOp.EQ.name().equals(compareOp) || CompareOp.NE.name().equals(compareOp)
                || CompareOp.CONTAINS.name().equals(compareOp) || CompareOp.NOT_CONTAINS.name().equals(compareOp);
    }

    private boolean isEnumCompareOp(String compareOp) {
        return CompareOp.EQ.name().equals(compareOp) || CompareOp.NE.name().equals(compareOp)
                || CompareOp.IN.name().equals(compareOp) || CompareOp.NOT_IN.name().equals(compareOp);
    }

    private MetricCatalog resolveCatalog(ThresholdRuleReq req) {
        String code = resolveMetricCode(req);
        if (code == null) {
            return null;
        }
        MetricCatalog catalog = catalogMapper.selectOne(new LambdaQueryWrapper<MetricCatalog>()
                .eq(MetricCatalog::getCode, code).eq(MetricCatalog::getDeleted, 0));
        if (catalog == null && req.getMetricCode() != null && !req.getMetricCode().trim().isEmpty()) {
            throw new BizException("指标目录不存在");
        }
        return catalog;
    }

    private MetricCatalog requireCatalog(String metricCode) {
        if (metricCode == null || metricCode.trim().isEmpty()) {
            throw new BizException("指标编码不能为空");
        }
        MetricCatalog catalog = catalogMapper.selectOne(new LambdaQueryWrapper<MetricCatalog>()
                .eq(MetricCatalog::getCode, metricCode.trim()).eq(MetricCatalog::getDeleted, 0));
        if (catalog == null) {
            throw new BizException("指标目录不存在");
        }
        return catalog;
    }

    private String resolveMetricCode(ThresholdRuleReq req) {
        if (req.getMetricCode() != null && !req.getMetricCode().trim().isEmpty()) {
            return req.getMetricCode().trim();
        }
        return req.getMetricType() == null || req.getMetricType().trim().isEmpty() ? null : req.getMetricType().trim();
    }

    private String resolveMetricType(ThresholdRuleReq req) {
        String metricCode = resolveMetricCode(req);
        if (metricCode == null) {
            throw new BizException("指标编码不能为空");
        }
        return req.getMetricType() == null || req.getMetricType().trim().isEmpty() ? metricCode : req.getMetricType().trim();
    }

    private List<String> collectEnumOptions(Long catalogId) {
        Set<String> values = new LinkedHashSet<>();
        List<MetricTemplateItem> items = templateItemMapper.selectList(new LambdaQueryWrapper<MetricTemplateItem>()
                .eq(MetricTemplateItem::getMetricCatalogId, catalogId)
                .eq(MetricTemplateItem::getDeleted, 0)
                .eq(MetricTemplateItem::getEnabled, 1));
        if (items != null) {
            for (MetricTemplateItem item : items) {
                values.addAll(parseOptions(item.getEnumOptions()));
            }
        }
        return values.stream().sorted().collect(Collectors.toList());
    }

    private List<String> parseOptions(String json) {
        if (json == null || json.trim().isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<String> values = objectMapper.readValue(json, new TypeReference<List<String>>() { });
            if (values == null) {
                return Collections.emptyList();
            }
            return values.stream().filter(value -> value != null && !value.trim().isEmpty())
                    .map(String::trim).distinct().collect(Collectors.toList());
        } catch (Exception e) {
            throw new BizException("枚举选项必须为 JSON 字符串数组");
        }
    }

    /** 兼容旧版仅提交单个 thresholdText 的枚举规则。 */
    private List<String> parseEnumRequestOptions(ThresholdRuleReq req) {
        if (req.getThresholdOptions() != null && !req.getThresholdOptions().trim().isEmpty()) {
            return parseOptions(req.getThresholdOptions());
        }
        if (req.getThresholdText() != null && !req.getThresholdText().trim().isEmpty()) {
            String text = req.getThresholdText().trim();
            if (text.startsWith("[")) {
                return parseOptions(text);
            }
            return Collections.singletonList(text);
        }
        return Collections.emptyList();
    }

    private Set<Long> resolveScopeDeviceIds(String scopeType, Long scopeId, List<Long> scopeIds) {
        if ("ALL".equals(scopeType)) {
            return null;
        }
        if ("DEVICE".equals(scopeType)) {
            Set<Long> ids = new LinkedHashSet<>();
            if (scopeIds != null) {
                for (Long id : scopeIds) if (id != null) ids.add(id);
            }
            if (ids.isEmpty() && scopeId != null) ids.add(scopeId);
            return ids;
        }
        if ("GROUP".equals(scopeType)) {
            if (scopeId == null) return Collections.emptySet();
            List<Device> devices = deviceMapper.selectList(new LambdaQueryWrapper<Device>()
                    .eq(Device::getGroupId, scopeId).eq(Device::getDeleted, 0));
            if (devices == null) return Collections.emptySet();
            return devices.stream().map(Device::getId).filter(id -> id != null).collect(Collectors.toSet());
        }
        throw new BizException("作用范围类型不合法");
    }

    private void validateAppProcessBinding(ThresholdRuleReq req, MetricCatalog catalog) {
        if (catalog == null || !MetricDefinitionRegistry.APP_PROCESS_STATE.equals(catalog.getCode())) {
            return;
        }
        String appPackage = normalizeScopeAppPackage(req);
        List<String> androidNames = splitAndroidNames(normalizeScopeAndroidName(req));
        if (appPackage == null || appPackage.isEmpty() || androidNames.isEmpty()) {
            throw new BizException("应用进程状态指标必须指定安卓实例和应用包名");
        }
        Set<Long> deviceIds = resolveScopeDeviceIds(req.getScopeType(), req.getScopeId(), req.getScopeIds());
        if (deviceIds != null && deviceIds.isEmpty()) {
            throw new BizException("应用进程状态指标在作用范围内未启用");
        }
        LambdaQueryWrapper<MetricBinding> query = new LambdaQueryWrapper<MetricBinding>()
                .eq(MetricBinding::getMetricCode, catalog.getCode()).eq(MetricBinding::getAppPackage, appPackage)
                .in(MetricBinding::getAndroidName, androidNames).eq(MetricBinding::getEnabled, 1)
                .eq(MetricBinding::getDeleted, 0);
        if (deviceIds != null) {
            query.in(MetricBinding::getDeviceId, deviceIds);
        }
        if (bindingMapper.selectOne(query) == null) {
            throw new BizException("应用进程状态指标未在作用范围、安卓实例和应用包名上启用");
        }
    }

    private List<String> splitAndroidNames(String names) {
        if (names == null || names.trim().isEmpty()) {
            return Collections.emptyList();
        }
        return Arrays.stream(names.split(",")).map(String::trim).filter(name -> !name.isEmpty())
                .distinct().collect(Collectors.toList());
    }

    private String trimToEmpty(String value) {
        return value == null ? "" : value.trim();
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
     * 规范化安卓实例名：空白串按 null 处理。安卓实例指标均可按实例名称限定范围。
     *
     * @param req 规则请求
     * @return 规范化后的实例名，可能为 null
     */
    private String normalizeScopeAndroidName(ThresholdRuleReq req) {
        if (req.getScopeAndroidName() == null || req.getScopeAndroidName().trim().isEmpty()) {
            return null;
        }
        return req.getScopeAndroidName().trim();
    }

    private String normalizeScopeAppPackage(ThresholdRuleReq req) {
        String appPackage = req.getScopeAppPackage() == null ? "" : req.getScopeAppPackage().trim();
        if (MetricDefinitionRegistry.APP_PROCESS_STATE.equals(resolveMetricCode(req)) && appPackage.isEmpty()) {
            throw new BizException("应用进程状态阈值必须填写应用包名");
        }
        return appPackage.isEmpty() ? null : appPackage;
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
