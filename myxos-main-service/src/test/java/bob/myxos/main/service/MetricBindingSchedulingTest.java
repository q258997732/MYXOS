package bob.myxos.main.service;

import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.MetricBinding;
import bob.myxos.domain.entity.MetricCatalog;
import bob.myxos.domain.mapper.ActionLogMapper;
import bob.myxos.domain.mapper.AlarmEventMapper;
import bob.myxos.domain.mapper.DeviceGroupMapper;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.domain.mapper.MetricBindingMapper;
import bob.myxos.domain.mapper.MetricCatalogMapper;
import bob.myxos.domain.mapper.MetricSnapshotMapper;
import bob.myxos.domain.mapper.MetricTemplateItemMapper;
import bob.myxos.domain.mapper.MetricTemplateMapper;
import bob.myxos.domain.mapper.OpTaskMapper;
import bob.myxos.domain.mapper.ThresholdRuleMapper;
import bob.myxos.main.dto.MetricBindingReq;
import bob.myxos.main.service.impl.DeviceServiceImpl;
import bob.myxos.mytos.MytosClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricBindingSchedulingTest {

    @Mock private DeviceMapper deviceMapper;
    @Mock private DeviceGroupMapper deviceGroupMapper;
    @Mock private OpTaskMapper opTaskMapper;
    @Mock private MetricSnapshotMapper metricSnapshotMapper;
    @Mock private AlarmEventMapper alarmEventMapper;
    @Mock private ActionLogMapper actionLogMapper;
    @Mock private ThresholdRuleMapper thresholdRuleMapper;
    @Mock private MytosClientFactory clientFactory;
    @Mock private ObjectMapper objectMapper;
    @Mock private MetricBindingMapper bindingMapper;
    @Mock private MetricCatalogMapper catalogMapper;
    @Mock private MetricTemplateMapper templateMapper;
    @Mock private MetricTemplateItemMapper templateItemMapper;

    @Test
    void 新建启用绑定应立即到期() {
        prepareForSave(null);

        service().saveMetricBindings(1L, null, request(1));

        ArgumentCaptor<MetricBinding> captor = ArgumentCaptor.forClass(MetricBinding.class);
        verify(bindingMapper).insert(captor.capture());
        assertEquals(Integer.valueOf(1), captor.getValue().getEnabled());
        assertFalse(captor.getValue().getNextCollectAt().isAfter(LocalDateTime.now()));
    }

    @Test
    void 禁用绑定重新启用时空计划应立即到期() {
        MetricBinding existing = new MetricBinding();
        existing.setId(9L);
        existing.setEnabled(0);
        existing.setNextCollectAt(null);
        prepareForSave(existing);

        service().saveMetricBindings(1L, null, request(1));

        ArgumentCaptor<MetricBinding> captor = ArgumentCaptor.forClass(MetricBinding.class);
        verify(bindingMapper).updateById(captor.capture());
        assertEquals(Integer.valueOf(1), captor.getValue().getEnabled());
        assertFalse(captor.getValue().getNextCollectAt().isAfter(LocalDateTime.now()));
    }

    @Test
    void 重新启用时已有计划不得被覆盖() {
        LocalDateTime planned = LocalDateTime.now().plusHours(1);
        MetricBinding existing = new MetricBinding();
        existing.setId(9L);
        existing.setEnabled(0);
        existing.setNextCollectAt(planned);
        prepareForSave(existing);

        service().saveMetricBindings(1L, null, request(1));

        ArgumentCaptor<MetricBinding> captor = ArgumentCaptor.forClass(MetricBinding.class);
        verify(bindingMapper).updateById(captor.capture());
        assertEquals(planned, captor.getValue().getNextCollectAt());
    }

    private DeviceServiceImpl service() {
        return new DeviceServiceImpl(deviceMapper, deviceGroupMapper, opTaskMapper, metricSnapshotMapper,
                alarmEventMapper, actionLogMapper, thresholdRuleMapper, clientFactory, objectMapper,
                bindingMapper, catalogMapper, templateMapper, templateItemMapper);
    }

    private void prepareForSave(MetricBinding existing) {
        Device device = new Device();
        device.setId(1L);
        when(deviceMapper.selectById(1L)).thenReturn(device);
        MetricCatalog catalog = new MetricCatalog();
        catalog.setCode("CPU");
        catalog.setTargetType("HOST");
        catalog.setDeleted(0);
        when(catalogMapper.selectOne(any())).thenReturn(catalog);
        when(bindingMapper.selectOne(any())).thenReturn(existing);
        when(bindingMapper.selectList(any())).thenReturn(Collections.<MetricBinding>emptyList());
    }

    private MetricBindingReq request(int enabled) {
        MetricBindingReq.Item item = new MetricBindingReq.Item();
        item.setMetricCode("CPU");
        item.setEnabled(enabled);
        item.setIntervalSec(60);
        MetricBindingReq request = new MetricBindingReq();
        request.setItems(Collections.singletonList(item));
        return request;
    }
}
