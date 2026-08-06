package bob.myxos.main.service;

import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.MetricCatalog;
import bob.myxos.domain.entity.MetricBinding;
import bob.myxos.domain.entity.MetricTemplateItem;
import bob.myxos.domain.entity.ThresholdRule;
import bob.myxos.domain.mapper.MetricCatalogMapper;
import bob.myxos.domain.mapper.MetricBindingMapper;
import bob.myxos.domain.mapper.MetricTemplateItemMapper;
import bob.myxos.domain.mapper.ThresholdActionMapper;
import bob.myxos.domain.mapper.ThresholdRuleMapper;
import bob.myxos.main.dto.ThresholdRuleReq;
import bob.myxos.main.dto.ThresholdMetricOptionExecuteReq;
import bob.myxos.main.service.impl.ThresholdServiceImpl;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.Test;

import java.util.Collections;
import java.util.Arrays;
import java.util.List;
import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/** 阈值规则类型化校验测试。 */
class ThresholdServiceTest {

    @Test
    void 应仅返回作用范围内已启用绑定且支持阈值的指标目录() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), MetricCatalog.class);
        MetricCatalogMapper catalogMapper = mock(MetricCatalogMapper.class);
        MetricBindingMapper bindingMapper = mock(MetricBindingMapper.class);
        ThresholdService service = new ThresholdServiceImpl(mock(ThresholdRuleMapper.class),
                mock(ThresholdActionMapper.class), catalogMapper, mock(MetricTemplateItemMapper.class), bindingMapper,
                mock(bob.myxos.domain.mapper.DeviceMapper.class), mock(DeviceService.class));
        MetricCatalog available = catalog("CPU_USAGE_PERCENT", "NUMBER", 1);
        MetricCatalog disabled = catalog("ANDROID_MODEL", "STRING", 0);
        when(bindingMapper.selectList(any())).thenReturn(Arrays.asList(binding(10L, "CPU_USAGE_PERCENT", "", ""),
                binding(11L, "ANDROID_MODEL", "", "")));
        when(catalogMapper.selectList(any())).thenReturn(Collections.singletonList(available));

        List<MetricCatalog> candidates = service.listMetricCandidates("DEVICE", null, Collections.singletonList(10L));

        assertEquals(Collections.singletonList("CPU_USAGE_PERCENT"), codes(candidates));
        verify(catalogMapper).selectList(org.mockito.ArgumentMatchers.argThat(query ->
                query.getSqlSegment().contains("threshold_enabled")));
    }

    @Test
    void 执行枚举选项必须使用受控命令并匹配应用进程绑定() {
        MetricCatalogMapper catalogMapper = mock(MetricCatalogMapper.class);
        MetricBindingMapper bindingMapper = mock(MetricBindingMapper.class);
        DeviceService deviceService = mock(DeviceService.class);
        ThresholdService service = new ThresholdServiceImpl(mock(ThresholdRuleMapper.class),
                mock(ThresholdActionMapper.class), catalogMapper, mock(MetricTemplateItemMapper.class), bindingMapper,
                mock(bob.myxos.domain.mapper.DeviceMapper.class), deviceService);
        when(catalogMapper.selectOne(any())).thenReturn(catalog("APP_PROCESS_STATE", "ENUM", 1));
        when(bindingMapper.selectOne(any())).thenReturn(
                binding(7L, "APP_PROCESS_STATE", "android-1", "com.example.app"));
        when(deviceService.executeShell(7L, "android-1", "dumpsys activity processes"))
                .thenReturn("ProcessRecord{123 com.example.app/1000}");
        ThresholdMetricOptionExecuteReq req = new ThresholdMetricOptionExecuteReq();
        req.setDeviceId(7L);
        req.setAndroidName("android-1");
        req.setMetricCode("APP_PROCESS_STATE");
        req.setAppPackage("com.example.app");

        assertEquals(Arrays.asList("FOREGROUND", "ACTIVE", "RUNNING", "STOPPED"), service.executeEnumOptions(req));
    }

    @Test
    void 枚举阈值只能使用模板已验证的选项() {
        MetricCatalogMapper catalogMapper = mock(MetricCatalogMapper.class);
        MetricTemplateItemMapper itemMapper = mock(MetricTemplateItemMapper.class);
        ThresholdService service = new ThresholdServiceImpl(mock(ThresholdRuleMapper.class),
                mock(ThresholdActionMapper.class), catalogMapper, itemMapper, mock(MetricBindingMapper.class),
                mock(bob.myxos.domain.mapper.DeviceMapper.class), mock(DeviceService.class));
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
        req.setThresholdText("INVALID");

        ThresholdRule created = service.create(req);
        assertEquals("[\"INVALID\"]", created.getThresholdText());
    }

    @Test
    void 更新持续时长规则应保留请求中的秒数() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), ThresholdRule.class);
        ThresholdRuleMapper ruleMapper = mock(ThresholdRuleMapper.class);
        ThresholdService service = new ThresholdServiceImpl(ruleMapper, mock(ThresholdActionMapper.class),
                mock(MetricCatalogMapper.class), mock(MetricTemplateItemMapper.class), mock(MetricBindingMapper.class),
                mock(bob.myxos.domain.mapper.DeviceMapper.class), mock(DeviceService.class));
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

    private MetricCatalog catalog(String code, String valueType, int thresholdEnabled) {
        MetricCatalog catalog = new MetricCatalog();
        catalog.setCode(code);
        catalog.setValueType(valueType);
        catalog.setThresholdEnabled(thresholdEnabled);
        return catalog;
    }

    private MetricBinding binding(Long deviceId, String metricCode, String androidName, String appPackage) {
        MetricBinding binding = new MetricBinding();
        binding.setDeviceId(deviceId);
        binding.setMetricCode(metricCode);
        binding.setAndroidName(androidName);
        binding.setAppPackage(appPackage);
        binding.setEnabled(1);
        return binding;
    }

    private List<String> codes(List<MetricCatalog> catalogs) {
        java.util.ArrayList<String> codes = new java.util.ArrayList<String>();
        for (MetricCatalog catalog : catalogs) {
            codes.add(catalog.getCode());
        }
        return codes;
    }
}
