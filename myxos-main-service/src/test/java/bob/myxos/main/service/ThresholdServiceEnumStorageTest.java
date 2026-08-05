package bob.myxos.main.service;

import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.MetricCatalog;
import bob.myxos.domain.entity.MetricTemplateItem;
import bob.myxos.domain.mapper.MetricCatalogMapper;
import bob.myxos.domain.mapper.MetricTemplateItemMapper;
import bob.myxos.domain.mapper.ThresholdActionMapper;
import bob.myxos.domain.mapper.ThresholdRuleMapper;
import bob.myxos.main.dto.ThresholdRuleReq;
import bob.myxos.main.service.impl.ThresholdServiceImpl;
import org.junit.jupiter.api.Test;

import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/** 验证枚举阈值写库前的容量保护。 */
class ThresholdServiceEnumStorageTest {

    @Test
    void 枚举阈值序列化结果超过数据库文本容量时应返回业务错误() {
        MetricCatalogMapper catalogMapper = mock(MetricCatalogMapper.class);
        MetricTemplateItemMapper itemMapper = mock(MetricTemplateItemMapper.class);
        ThresholdService service = new ThresholdServiceImpl(mock(ThresholdRuleMapper.class),
                mock(ThresholdActionMapper.class), catalogMapper, itemMapper);
        MetricCatalog catalog = new MetricCatalog();
        catalog.setId(9L);
        catalog.setCode("ANDROID_STATUS");
        catalog.setValueType("ENUM");
        catalog.setThresholdEnabled(1);
        MetricTemplateItem item = new MetricTemplateItem();
        item.setMetricCatalogId(9L);
        item.setEnumOptions(largeEnumOptions());
        when(catalogMapper.selectOne(any())).thenReturn(catalog);
        when(itemMapper.selectList(any())).thenReturn(Collections.singletonList(item));

        ThresholdRuleReq req = new ThresholdRuleReq();
        req.setName("容量保护");
        req.setMetricCode("ANDROID_STATUS");
        req.setCompareOp("IN");
        req.setTriggerMode("DURATION");
        req.setScopeType("ALL");
        req.setThresholdOptions(largeEnumOptions());

        BizException exception = assertThrows(BizException.class, () -> service.create(req));
        assertEquals("枚举阈值序列化后超过存储容量", exception.getMessage());
    }

    private String largeEnumOptions() {
        StringBuilder value = new StringBuilder("[\"");
        for (int i = 0; i < 70000; i++) {
            value.append('a');
        }
        return value.append("\"]").toString();
    }
}
