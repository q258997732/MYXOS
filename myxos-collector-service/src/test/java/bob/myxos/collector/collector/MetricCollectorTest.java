package bob.myxos.collector.collector;

import bob.myxos.common.enums.DeviceStatus;
import bob.myxos.common.enums.MetricType;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.MetricSnapshot;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.mytos.MytosClient;
import bob.myxos.mytos.MytosClientFactory;
import bob.myxos.mytos.dto.AndroidDetailResp;
import bob.myxos.mytos.dto.AndroidListResp;
import bob.myxos.mytos.dto.BootStatusResp;
import bob.myxos.mytos.dto.HealthResp;
import bob.myxos.mytos.dto.HostVerResp;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.TextNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * MetricCollector 单元测试
 */
@ExtendWith(MockitoExtension.class)
class MetricCollectorTest {

    @Mock
    private MytosClientFactory clientFactory;
    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private MetricCollector.MetricPersistCallback persistCallback;
    @Mock
    private MytosClient client;

    private Device device;

    @BeforeEach
    void setUp() {
        device = new Device();
        device.setId(1L);
        device.setIp("192.168.30.2");
        device.setPort(9082);
    }

    @Test
    @DisplayName("设备在线时应保存版本与状态指标并更新状态为 ONLINE")
    void runWhenDeviceOnline() {
        // Arrange
        HealthResp health = new HealthResp();
        health.setCode(200);
        HostVerResp version = new HostVerResp();
        version.setData("1.2.3");

        when(clientFactory.create(anyString(), anyInt())).thenReturn(client);
        when(client.healthcheck(anyString())).thenReturn(health);
        when(client.getHostVer(anyString())).thenReturn(version);

        MetricCollector collector = new MetricCollector(device, clientFactory, persistCallback, deviceMapper);

        // Act
        collector.run();

        // Assert
        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
        verify(deviceMapper, times(1)).updateById(deviceCaptor.capture());
        assertEquals(DeviceStatus.ONLINE.name(), deviceCaptor.getValue().getStatus());
        assertEquals("1.2.3", deviceCaptor.getValue().getVersion());
        assertNotNull(deviceCaptor.getValue().getLastSeenAt());

        Map<String, MetricSnapshot> byType = captureSnapshotsByType();
        // VERSION + ANDROID_ONLINE + ANDROID_OFFLINE + ONLINE + OFFLINE
        assertEquals(5, byType.size());
        assertEquals("1.2.3", byType.get(MetricType.VERSION.name()).getMetricValue());
        assertEquals(BigDecimal.ONE, byType.get(MetricType.ONLINE.name()).getMetricNum());
        assertEquals(BigDecimal.ZERO, byType.get(MetricType.OFFLINE.name()).getMetricNum());
        assertEquals(BigDecimal.ZERO, byType.get(MetricType.ANDROID_ONLINE.name()).getMetricNum());
        assertEquals(BigDecimal.ZERO, byType.get(MetricType.ANDROID_OFFLINE.name()).getMetricNum());
    }

    @Test
    @DisplayName("设备在线且有安卓实例时应统计实例在线/离线数量")
    void runWhenOnlineWithAndroids() throws Exception {
        // Arrange
        HealthResp health = new HealthResp();
        health.setCode(200);
        HostVerResp version = new HostVerResp();
        version.setData("1.2.3");
        AndroidListResp listResp = new AndroidListResp();
        listResp.setData(new ObjectMapper().readTree("[\"c1\",\"c2\"]"));

        when(clientFactory.create(anyString(), anyInt())).thenReturn(client);
        when(client.healthcheck(anyString())).thenReturn(health);
        when(client.getHostVer(anyString())).thenReturn(version);
        when(client.listAndroid(anyString())).thenReturn(listResp);
        when(client.getAndroidBootStatus(anyString(), anyString())).thenAnswer(inv -> {
            BootStatusResp resp = new BootStatusResp();
            resp.setData(TextNode.valueOf("c1".equals(inv.getArgument(1)) ? "running" : "stopped"));
            return resp;
        });

        MetricCollector collector = new MetricCollector(device, clientFactory, persistCallback, deviceMapper);

        // Act
        collector.run();

        // Assert
        Map<String, MetricSnapshot> byType = captureSnapshotsByType();
        assertEquals(BigDecimal.ONE, byType.get(MetricType.ANDROID_ONLINE.name()).getMetricNum());
        assertEquals(BigDecimal.ONE, byType.get(MetricType.ANDROID_OFFLINE.name()).getMetricNum());
    }

    @Test
    @DisplayName("设备响应非 200 时应更新状态为 OFFLINE 并产出离线状态快照")
    void runWhenDeviceResponseError() {
        // Arrange
        HealthResp health = new HealthResp();
        health.setCode(500);

        when(clientFactory.create(anyString(), anyInt())).thenReturn(client);
        when(client.healthcheck(anyString())).thenReturn(health);

        MetricCollector collector = new MetricCollector(device, clientFactory, persistCallback, deviceMapper);

        // Act
        collector.run();

        // Assert
        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
        verify(deviceMapper, times(1)).updateById(deviceCaptor.capture());
        assertEquals(DeviceStatus.OFFLINE.name(), deviceCaptor.getValue().getStatus());

        Map<String, MetricSnapshot> byType = captureSnapshotsByType();
        assertEquals(4, byType.size());
        assertEquals(BigDecimal.ONE, byType.get(MetricType.OFFLINE.name()).getMetricNum());
        assertEquals(BigDecimal.ZERO, byType.get(MetricType.ONLINE.name()).getMetricNum());
    }

    @Test
    @DisplayName("调用异常时应更新状态为 OFFLINE 并产出离线状态快照")
    void runWhenExceptionThrown() {
        // Arrange
        when(clientFactory.create(anyString(), anyInt())).thenReturn(client);
        when(client.healthcheck(anyString())).thenThrow(new RuntimeException("connection timeout"));

        MetricCollector collector = new MetricCollector(device, clientFactory, persistCallback, deviceMapper);

        // Act
        collector.run();

        // Assert
        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
        verify(deviceMapper, times(1)).updateById(deviceCaptor.capture());
        assertEquals(DeviceStatus.OFFLINE.name(), deviceCaptor.getValue().getStatus());

        Map<String, MetricSnapshot> byType = captureSnapshotsByType();
        assertEquals(BigDecimal.ONE, byType.get(MetricType.OFFLINE.name()).getMetricNum());
    }

    @Test
    @DisplayName("实例详情接口返回有效状态时应优先于启动状态接口")
    void runWhenDetailStatusAvailable() throws Exception {
        // Arrange
        HealthResp health = new HealthResp();
        health.setCode(200);
        HostVerResp version = new HostVerResp();
        version.setData("1.2.3");
        AndroidListResp listResp = new AndroidListResp();
        listResp.setData(new ObjectMapper().readTree("[\"c1\",\"c2\"]"));

        when(clientFactory.create(anyString(), anyInt())).thenReturn(client);
        when(client.healthcheck(anyString())).thenReturn(health);
        when(client.getHostVer(anyString())).thenReturn(version);
        when(client.listAndroid(anyString())).thenReturn(listResp);
        // 详情接口：c1 运行中，c2 已停止；启动状态接口未 stub（返回 null），验证详情优先且无需回退
        when(client.getAndroidDetail(anyString(), anyString())).thenAnswer(inv -> {
            AndroidDetailResp resp = new AndroidDetailResp();
            resp.setCode(200);
            String status = "c1".equals(inv.getArgument(1)) ? "running" : "stopped";
            resp.setData(new ObjectMapper().readTree("{\"status\":\"" + status + "\"}"));
            return resp;
        });

        MetricCollector collector = new MetricCollector(device, clientFactory, persistCallback, deviceMapper);

        // Act
        collector.run();

        // Assert
        Map<String, MetricSnapshot> byType = captureSnapshotsByType();
        assertEquals(BigDecimal.ONE, byType.get(MetricType.ANDROID_ONLINE.name()).getMetricNum());
        assertEquals(BigDecimal.ONE, byType.get(MetricType.ANDROID_OFFLINE.name()).getMetricNum());
    }

    /**
     * 捕获持久化回调收到的快照并按指标类型索引
     */
    private Map<String, MetricSnapshot> captureSnapshotsByType() {
        ArgumentCaptor<List<MetricSnapshot>> snapshotCaptor = ArgumentCaptor.forClass(List.class);
        verify(persistCallback, times(1)).persist(snapshotCaptor.capture());
        return snapshotCaptor.getValue().stream()
                .collect(Collectors.toMap(MetricSnapshot::getMetricType, Function.identity(), (a, b) -> a));
    }
}
