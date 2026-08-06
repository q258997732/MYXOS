package bob.myxos.main.service;

import bob.myxos.common.exception.BizException;
import bob.myxos.domain.entity.MetricBinding;
import bob.myxos.domain.entity.MetricCatalog;
import bob.myxos.domain.mapper.MetricBindingMapper;
import bob.myxos.domain.mapper.MetricCatalogMapper;
import bob.myxos.domain.mapper.MetricTemplateItemMapper;
import bob.myxos.domain.mapper.MetricTemplateMapper;
import bob.myxos.main.dto.MetricTemplateReq;
import bob.myxos.main.service.impl.MetricTemplateServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricTemplateServiceTest {
    @Mock private MetricCatalogMapper metricCatalogMapper;
    @Mock private MetricTemplateMapper metricTemplateMapper;
    @Mock private MetricTemplateItemMapper metricTemplateItemMapper;
    @Mock private MetricBindingMapper metricBindingMapper;

    @Test
    void 实例绑定频率应优先于主机绑定() {
        MetricTemplateServiceImpl service = service();
        MetricBinding host = binding(1L, "", "CPU_USAGE_PERCENT", 60);
        MetricBinding instance = binding(1L, "android-1", "CPU_USAGE_PERCENT", 15);
        assertEquals(Integer.valueOf(15), service.resolveEffectiveBinding(1L, "android-1",
                "CPU_USAGE_PERCENT", Arrays.asList(host, instance)).getIntervalSec());
    }

    @Test
    void HOST模板应拒绝ANDROID指标() {
        MetricCatalog catalog = new MetricCatalog();
        catalog.setId(9L);
        catalog.setTargetType("ANDROID_INSTANCE");
        catalog.setDeleted(0);
        when(metricCatalogMapper.selectById(9L)).thenReturn(catalog);
        assertThrows(BizException.class, () -> service().create(hostTemplateContaining(9L)));
    }

    @Test
    void 模板不应包含需要应用包名的进程状态指标() {
        MetricCatalog catalog = new MetricCatalog();
        catalog.setId(10L);
        catalog.setCode("APP_PROCESS_STATE");
        catalog.setTargetType("ANDROID_INSTANCE");
        catalog.setDeleted(0);
        when(metricCatalogMapper.selectById(10L)).thenReturn(catalog);

        MetricTemplateReq req = hostTemplateContaining(10L);
        req.setTargetType("ANDROID_INSTANCE");

        assertThrows(BizException.class, () -> service().create(req));
    }

    private MetricTemplateServiceImpl service() {
        return new MetricTemplateServiceImpl(metricCatalogMapper, metricTemplateMapper,
                metricTemplateItemMapper, metricBindingMapper);
    }

    private MetricTemplateReq hostTemplateContaining(Long catalogId) {
        MetricTemplateReq req = new MetricTemplateReq();
        req.setName("主机模板");
        req.setTargetType("HOST");
        req.setEnabled(1);
        MetricTemplateReq.Item item = new MetricTemplateReq.Item();
        item.setMetricCatalogId(catalogId);
        item.setEnabled(1);
        item.setDefaultIntervalSec(60);
        req.setItems(Arrays.asList(item));
        return req;
    }

    private MetricBinding binding(Long deviceId, String androidName, String metricCode, Integer intervalSec) {
        MetricBinding binding = new MetricBinding();
        binding.setDeviceId(deviceId);
        binding.setAndroidName(androidName);
        binding.setMetricCode(metricCode);
        binding.setEnabled(1);
        binding.setIntervalSec(intervalSec);
        return binding;
    }
}
