package bob.myxos.main.service;

import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.MetricCatalog;
import bob.myxos.domain.entity.MetricTemplateItem;
import bob.myxos.domain.entity.ThresholdRule;
import bob.myxos.domain.mapper.MetricCatalogMapper;
import bob.myxos.domain.mapper.MetricTemplateItemMapper;
import bob.myxos.domain.mapper.ThresholdActionMapper;
import bob.myxos.domain.mapper.ThresholdRuleMapper;
import bob.myxos.main.dto.ThresholdRuleReq;
import bob.myxos.main.service.impl.ThresholdServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 阈值规则类型化校验测试。 */
class ThresholdServiceTest {

    @Test
    void 枚举阈值只能使用模板已验证的选项() {
        MetricCatalogMapper catalogMapper = mock(MetricCatalogMapper.class);
        MetricTemplateItemMapper itemMapper = mock(MetricTemplateItemMapper.class);
        ThresholdService service = new ThresholdServiceImpl(mock(ThresholdRuleMapper.class),
                mock(ThresholdActionMapper.class), catalogMapper, itemMapper);
        MetricCatalog catalog = new MetricCatalog();
        catalog.setId(3L);
        catalog.setCode("ANDROID_STATUS");
        catalog.setValueType("ENUM");
        catalog.setThresholdEnabled(1);
        MetricTemplateItem item = new MetricTemplateItem();
        item.setMetricCatalogId(3L);
        item.setEnumOptions("[\"RUNNING\",\"STOPPED\"]");
        when(catalogMapper.selectOne(any())).thenReturn(catalog);
        when(itemMapper.selectList(any())).thenReturn(Collections.singletonList(item));

        ThresholdRuleReq req = rule("ANDROID_STATUS", "IN");
        req.setThresholdOptions("[\"RUNNING\",\"INVALID\"]");

        assertThrows(BizException.class, () -> service.create(req));
    }

    @Test
    void 更新持续时长规则应保留请求中的秒数() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ThresholdRule.class);
        ThresholdRuleMapper ruleMapper = mock(ThresholdRuleMapper.class);
        ThresholdService service = new ThresholdServiceImpl(ruleMapper, mock(ThresholdActionMapper.class),
                mock(MetricCatalogMapper.class), mock(MetricTemplateItemMapper.class));
        ThresholdRule existing = new ThresholdRule();
        existing.setId(8L);
        existing.setDeleted(0);
        when(ruleMapper.selectById(8L)).thenReturn(existing);

        ThresholdRuleReq req = rule("CPU_USAGE_PERCENT", "GT");
        req.setMetricCode(null);
        req.setMetricType("CPU_USAGE_PERCENT");
        req.setThresholdValue(new BigDecimal("80"));
        req.setDurationSec(45);

        ThresholdRule result = service.update(8L, req);

        assertEquals(Integer.valueOf(45), result.getDurationSec());
    }

    private ThresholdRuleReq rule(String metricCode, String compareOp) {
        ThresholdRuleReq req = new ThresholdRuleReq();
        req.setName("测试规则");
        req.setMetricCode(metricCode);
        req.setCompareOp(compareOp);
        req.setTriggerMode("DURATION");
        req.setScopeType("ALL");
        return req;
    }
}
