package bob.myxos.collector.collector;

import bob.myxos.common.enums.DeviceStatus;
import bob.myxos.common.enums.MetricType;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.MetricSnapshot;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.mytos.MytosClient;
import bob.myxos.mytos.MytosClientFactory;
import bob.myxos.mytos.dto.InfoResp;
import bob.myxos.mytos.dto.VersionResp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
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
    @DisplayName("设备在线时应保存版本指标并更新状态为 ONLINE")
    void runWhenDeviceOnline() {
        // Arrange
        InfoResp info = new InfoResp();
        info.setCode(200);
        VersionResp version = new VersionResp();
        version.setMsg("1.2.3");

        when(clientFactory.create(anyString(), anyInt())).thenReturn(client);
        when(client.info()).thenReturn(info);
        when(client.queryVersion()).thenReturn(version);

        MetricCollector collector = new MetricCollector(device, clientFactory, persistCallback, deviceMapper);

        // Act
        collector.run();

        // Assert
        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
        verify(deviceMapper, times(1)).updateById(deviceCaptor.capture());
        assertEquals(DeviceStatus.ONLINE.name(), deviceCaptor.getValue().getStatus());
        assertEquals("1.2.3", deviceCaptor.getValue().getVersion());
        assertNotNull(deviceCaptor.getValue().getLastSeenAt());

        ArgumentCaptor<List<MetricSnapshot>> snapshotCaptor = ArgumentCaptor.forClass(List.class);
        verify(persistCallback, times(1)).persist(snapshotCaptor.capture());
        List<MetricSnapshot> snapshots = snapshotCaptor.getValue();
        assertEquals(1, snapshots.size());
        assertEquals(MetricType.VERSION.name(), snapshots.get(0).getMetricType());
        assertEquals("1.2.3", snapshots.get(0).getMetricValue());
    }

    @Test
    @DisplayName("设备响应非 200 时应更新状态为 OFFLINE 且不保存指标")
    void runWhenDeviceResponseError() {
        // Arrange
        InfoResp info = new InfoResp();
        info.setCode(500);

        when(clientFactory.create(anyString(), anyInt())).thenReturn(client);
        when(client.info()).thenReturn(info);

        MetricCollector collector = new MetricCollector(device, clientFactory, persistCallback, deviceMapper);

        // Act
        collector.run();

        // Assert
        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
        verify(deviceMapper, times(1)).updateById(deviceCaptor.capture());
        assertEquals(DeviceStatus.OFFLINE.name(), deviceCaptor.getValue().getStatus());
        verify(persistCallback, times(0)).persist(any());
    }

    @Test
    @DisplayName("调用异常时应更新状态为 OFFLINE 且不保存指标")
    void runWhenExceptionThrown() {
        // Arrange
        when(clientFactory.create(anyString(), anyInt())).thenReturn(client);
        when(client.info()).thenThrow(new RuntimeException("connection timeout"));

        MetricCollector collector = new MetricCollector(device, clientFactory, persistCallback, deviceMapper);

        // Act
        collector.run();

        // Assert
        ArgumentCaptor<Device> deviceCaptor = ArgumentCaptor.forClass(Device.class);
        verify(deviceMapper, times(1)).updateById(deviceCaptor.capture());
        assertEquals(DeviceStatus.OFFLINE.name(), deviceCaptor.getValue().getStatus());
        verify(persistCallback, times(0)).persist(any());
    }
}
