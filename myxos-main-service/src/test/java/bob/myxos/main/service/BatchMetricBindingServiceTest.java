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
import bob.myxos.main.dto.BatchMetricBindingReq;
import bob.myxos.main.dto.BatchMetricBindingResult;
import bob.myxos.main.dto.MetricBindingReq;
import bob.myxos.main.service.impl.DeviceServiceImpl;
import bob.myxos.mytos.MytosClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BatchMetricBindingServiceTest {

    @Mock private DeviceMapper deviceMapper;
    @Mock private DeviceGroupMapper deviceGroupMapper;
    @Mock private OpTaskMapper opTaskMapper;
    @Mock private MetricSnapshotMapper metricSnapshotMapper;
    @Mock private AlarmEventMapper alarmEventMapper;
    @Mock private ActionLogMapper actionLogMapper;
    @Mock private ThresholdRuleMapper thresholdRuleMapper;
    @Mock private MytosClientFactory mytosClientFactory;
    @Mock private ObjectMapper objectMapper;
    @Mock private MetricBindingMapper metricBindingMapper;
    @Mock private MetricCatalogMapper metricCatalogMapper;
    @Mock private MetricTemplateMapper metricTemplateMapper;
    @Mock private MetricTemplateItemMapper metricTemplateItemMapper;

    @Test
    void 每个批量目标应能携带独立指标项() {
        BatchMetricBindingReq.Target first = new BatchMetricBindingReq.Target();
        first.setItems(Arrays.asList(item("APP_PROCESS_STATE", "com.example.first")));
        BatchMetricBindingReq.Target second = new BatchMetricBindingReq.Target();
        second.setItems(Arrays.asList(item("APP_PROCESS_STATE", "com.example.second")));

        assertEquals("com.example.first", first.getItems().get(0).getAppPackage());
        assertEquals("com.example.second", second.getItems().get(0).getAppPackage());
    }

    @Test
    void 同一目标任一指标失败应回滚且其他目标仍可成功() {
        List<MetricBinding> persisted = new ArrayList<MetricBinding>();
        RollbackRecordingTransactionManager transactionManager = new RollbackRecordingTransactionManager(persisted);
        Device device = new Device();
        device.setDeleted(0);
        when(deviceMapper.selectById(any(Long.class))).thenReturn(device);
        MetricCatalog catalog = new MetricCatalog();
        catalog.setTargetType("ANDROID_INSTANCE");
        when(metricCatalogMapper.selectOne(any())).thenReturn(catalog);
        when(metricBindingMapper.selectOne(any())).thenReturn(null);
        when(metricBindingMapper.insert(any(MetricBinding.class))).thenAnswer(invocation -> {
            persisted.add(invocation.getArgument(0));
            return 1;
        });
        when(metricBindingMapper.selectList(any())).thenReturn(new ArrayList<MetricBinding>());

        DeviceServiceImpl service = new DeviceServiceImpl(deviceMapper, deviceGroupMapper, opTaskMapper,
                metricSnapshotMapper, alarmEventMapper, actionLogMapper, thresholdRuleMapper,
                mytosClientFactory, objectMapper, metricBindingMapper, metricCatalogMapper,
                metricTemplateMapper, metricTemplateItemMapper, transactionManager);
        BatchMetricBindingReq req = new BatchMetricBindingReq();
        req.setTargetType("ANDROID_INSTANCE");
        req.setTargets(Arrays.asList(target(1L, "android-1", item("CPU_USAGE_PERCENT", null), item("APP_PROCESS_STATE", null)),
                target(2L, "android-2", item("APP_PROCESS_STATE", "com.example.second"))));

        BatchMetricBindingResult result = service.saveMetricBindings(req);

        assertEquals(1, result.getSucceeded().size());
        assertEquals(Long.valueOf(2L), result.getSucceeded().get(0).getDeviceId());
        assertEquals(1, result.getFailed().size());
        assertEquals(Long.valueOf(1L), result.getFailed().get(0).getDeviceId());
        assertEquals(1, persisted.size());
        assertEquals(Long.valueOf(2L), persisted.get(0).getDeviceId());
        assertEquals(TransactionDefinition.PROPAGATION_REQUIRES_NEW, transactionManager.getPropagationBehavior());
    }

    @Test
    void 真实数据库中失败目标不应留下部分绑定且其他目标应提交() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                "jdbc:h2:mem:batch_metric_binding;MODE=MySQL;DB_CLOSE_DELAY=-1", "sa", "");
        JdbcTemplate jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP TABLE IF EXISTS metric_binding");
        jdbcTemplate.execute("CREATE TABLE metric_binding (device_id BIGINT, android_name VARCHAR(128), metric_code VARCHAR(64), app_package VARCHAR(255))");
        DataSourceTransactionManager transactionManager = new DataSourceTransactionManager(dataSource);
        Device device = new Device();
        device.setDeleted(0);
        when(deviceMapper.selectById(any(Long.class))).thenReturn(device);
        MetricCatalog catalog = new MetricCatalog();
        catalog.setTargetType("ANDROID_INSTANCE");
        when(metricCatalogMapper.selectOne(any())).thenReturn(catalog);
        when(metricBindingMapper.selectOne(any())).thenReturn(null);
        when(metricBindingMapper.insert(any(MetricBinding.class))).thenAnswer(invocation -> {
            MetricBinding binding = invocation.getArgument(0);
            return jdbcTemplate.update("INSERT INTO metric_binding (device_id, android_name, metric_code, app_package) VALUES (?, ?, ?, ?)",
                    binding.getDeviceId(), binding.getAndroidName(), binding.getMetricCode(), binding.getAppPackage());
        });
        when(metricBindingMapper.selectList(any())).thenReturn(new ArrayList<MetricBinding>());

        DeviceServiceImpl service = service(transactionManager);
        BatchMetricBindingReq req = new BatchMetricBindingReq();
        req.setTargetType("ANDROID_INSTANCE");
        req.setTargets(Arrays.asList(target(1L, "android-1", item("CPU_USAGE_PERCENT", null), item("APP_PROCESS_STATE", null)),
                target(2L, "android-2", item("APP_PROCESS_STATE", "com.example.second"))));

        BatchMetricBindingResult result = service.saveMetricBindings(req);

        assertEquals(1, result.getFailed().size());
        assertEquals(1, result.getSucceeded().size());
        assertEquals(Integer.valueOf(0), jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM metric_binding WHERE device_id = 1", Integer.class));
        assertEquals(Integer.valueOf(1), jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM metric_binding WHERE device_id = 2", Integer.class));
    }

    private DeviceServiceImpl service(DataSourceTransactionManager transactionManager) {
        return new DeviceServiceImpl(deviceMapper, deviceGroupMapper, opTaskMapper,
                metricSnapshotMapper, alarmEventMapper, actionLogMapper, thresholdRuleMapper,
                mytosClientFactory, objectMapper, metricBindingMapper, metricCatalogMapper,
                metricTemplateMapper, metricTemplateItemMapper, transactionManager);
    }

    private BatchMetricBindingReq.Target target(Long deviceId, String androidName, MetricBindingReq.Item... items) {
        BatchMetricBindingReq.Target target = new BatchMetricBindingReq.Target();
        target.setDeviceId(deviceId);
        target.setAndroidName(androidName);
        target.setItems(Arrays.asList(items));
        return target;
    }

    private MetricBindingReq.Item item(String metricCode, String appPackage) {
        MetricBindingReq.Item item = new MetricBindingReq.Item();
        item.setMetricCode(metricCode);
        item.setAppPackage(appPackage);
        item.setEnabled(1);
        item.setIntervalSec(60);
        return item;
    }

    private static class RollbackRecordingTransactionManager extends AbstractPlatformTransactionManager {
        private final List<MetricBinding> persisted;
        private List<MetricBinding> snapshot;
        private int propagationBehavior;

        private RollbackRecordingTransactionManager(List<MetricBinding> persisted) {
            this.persisted = persisted;
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            propagationBehavior = definition.getPropagationBehavior();
            snapshot = new ArrayList<MetricBinding>(persisted);
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            persisted.clear();
            persisted.addAll(snapshot);
        }

        private int getPropagationBehavior() {
            return propagationBehavior;
        }
    }
}
