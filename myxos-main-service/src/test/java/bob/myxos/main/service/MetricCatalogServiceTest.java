package bob.myxos.main.service;

import bob.myxos.domain.entity.MetricCatalog;
import bob.myxos.domain.mapper.MetricCatalogMapper;
import bob.myxos.main.dto.MetricCatalogUpdateReq;
import bob.myxos.main.service.impl.MetricCatalogServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MetricCatalogServiceTest {
    @Mock private MetricCatalogMapper metricCatalogMapper;

    @Test
    void 应更新默认频率而不允许修改受控定义() {
        MetricCatalog catalog = new MetricCatalog();
        catalog.setId(1L);
        catalog.setCode("CPU_USAGE_PERCENT");
        catalog.setTargetType("ANDROID_INSTANCE");
        catalog.setDefaultIntervalSec(60);
        catalog.setDeleted(0);
        when(metricCatalogMapper.selectById(1L)).thenReturn(catalog);

        MetricCatalogUpdateReq req = new MetricCatalogUpdateReq();
        req.setDefaultIntervalSec(120);
        MetricCatalog updated = new MetricCatalogServiceImpl(metricCatalogMapper).update(1L, req);

        ArgumentCaptor<MetricCatalog> captor = ArgumentCaptor.forClass(MetricCatalog.class);
        verify(metricCatalogMapper).updateById(captor.capture());
        assertEquals(Integer.valueOf(120), updated.getDefaultIntervalSec());
        assertEquals("CPU_USAGE_PERCENT", captor.getValue().getCode());
        assertEquals("ANDROID_INSTANCE", captor.getValue().getTargetType());
    }

    @Test
    void 不存在的目录项不可更新() {
        when(metricCatalogMapper.selectById(9L)).thenReturn(null);
        MetricCatalogUpdateReq req = new MetricCatalogUpdateReq();
        req.setDefaultIntervalSec(60);
        assertThrows(RuntimeException.class, () -> new MetricCatalogServiceImpl(metricCatalogMapper).update(9L, req));
    }
}
