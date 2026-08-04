package bob.myxos.collector.execute;

import bob.myxos.common.enums.OperationCode;
import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.OpTask;
import bob.myxos.domain.mapper.ActionLogMapper;
import bob.myxos.domain.mapper.DeviceMapper;
import bob.myxos.domain.mapper.OpTaskMapper;
import bob.myxos.mytos.MytosClient;
import bob.myxos.mytos.MytosClientFactory;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * OpTaskRunnerFactory 单元测试
 */
@ExtendWith(MockitoExtension.class)
class OpTaskRunnerFactoryTest {

    @Mock
    private OpTaskMapper opTaskMapper;
    @Mock
    private DeviceMapper deviceMapper;
    @Mock
    private MytosClientFactory clientFactory;
    @Mock
    private ObjectMapper objectMapper;
    @Mock
    private ActionLogMapper actionLogMapper;

    private OpTaskRunnerFactory factory;

    @BeforeEach
    void setUp() {
        factory = new OpTaskRunnerFactory(opTaskMapper, deviceMapper, clientFactory, objectMapper, actionLogMapper);
    }

    @Test
    @DisplayName("成功执行操作任务后状态应为 SUCCESS")
    void runTaskSuccess() throws Exception {
        // Arrange
        Device device = new Device();
        device.setId(1L);
        device.setIp("192.168.30.2");
        device.setPort(9082);

        OpTask task = new OpTask();
        task.setId(1L);
        task.setDeviceId(1L);
        task.setOperationCode(OperationCode.REBOOT_HOST.name());
        task.setParams(null);
        task.setRetryCount(0);
        task.setMaxRetry(3);

        MytosClient client = mock(MytosClient.class);
        when(deviceMapper.selectById(1L)).thenReturn(device);
        when(clientFactory.create("192.168.30.2", 9082)).thenReturn(client);

        // Act
        factory.create(task).run();

        // Assert
        verify(client, times(1)).execute(OperationCode.REBOOT_HOST, null);
        assertEquals("SUCCESS", task.getStatus());
        verify(opTaskMapper, times(1)).updateById(task);
    }

    @Test
    @DisplayName("设备不存在时任务应标记为 FAILED 且不调用客户端")
    void runTaskFailsWhenDeviceNotFound() {
        // Arrange
        OpTask task = new OpTask();
        task.setId(1L);
        task.setDeviceId(1L);
        task.setOperationCode(OperationCode.REBOOT_HOST.name());
        task.setRetryCount(0);
        task.setMaxRetry(3);

        when(deviceMapper.selectById(1L)).thenReturn(null);

        // Act
        factory.create(task).run();

        // Assert
        assertEquals("FAILED", task.getStatus());
        verify(clientFactory, never()).create(anyString(), anyInt());
        verify(opTaskMapper, times(1)).updateById(task);
    }

    @Test
    @DisplayName("调用失败且未达最大重试时应回到 PENDING 并增加重试次数")
    void runTaskRetryWhenFailed() throws Exception {
        // Arrange
        Device device = new Device();
        device.setId(1L);
        device.setIp("192.168.30.2");
        device.setPort(9082);

        OpTask task = new OpTask();
        task.setId(1L);
        task.setDeviceId(1L);
        task.setOperationCode(OperationCode.REBOOT_HOST.name());
        task.setParams(null);
        task.setRetryCount(0);
        task.setMaxRetry(3);

        MytosClient client = mock(MytosClient.class);
        when(deviceMapper.selectById(1L)).thenReturn(device);
        when(clientFactory.create("192.168.30.2", 9082)).thenReturn(client);
        doThrow(new RuntimeException("device offline")).when(client).execute(any(), any());

        // Act
        factory.create(task).run();

        // Assert
        assertEquals("PENDING", task.getStatus());
        assertEquals(1, task.getRetryCount());
        assertEquals("等待第 1 次重试：device offline", task.getResultMsg());
        verify(opTaskMapper, times(1)).updateById(task);
    }

    @Test
    @DisplayName("调用失败且重试耗尽后任务应标记为 FAILED")
    void runTaskFailedWhenMaxRetryReached() throws Exception {
        // Arrange
        Device device = new Device();
        device.setId(1L);
        device.setIp("192.168.30.2");
        device.setPort(9082);

        OpTask task = new OpTask();
        task.setId(1L);
        task.setDeviceId(1L);
        task.setOperationCode(OperationCode.REBOOT_HOST.name());
        task.setParams(null);
        task.setRetryCount(3);
        task.setMaxRetry(3);

        MytosClient client = mock(MytosClient.class);
        when(deviceMapper.selectById(1L)).thenReturn(device);
        when(clientFactory.create("192.168.30.2", 9082)).thenReturn(client);
        doThrow(new RuntimeException("device offline")).when(client).execute(any(), any());

        // Act
        factory.create(task).run();

        // Assert
        assertEquals("FAILED", task.getStatus());
        assertEquals("重试耗尽：device offline", task.getResultMsg());
        verify(opTaskMapper, times(1)).updateById(task);
    }

    @Test
    @DisplayName("任务参数应被正确解析为 Map 后传入 MytosClient")
    void runTaskParsesParams() throws Exception {
        // Arrange
        Device device = new Device();
        device.setId(1L);
        device.setIp("192.168.30.2");
        device.setPort(9082);

        OpTask task = new OpTask();
        task.setId(1L);
        task.setDeviceId(1L);
        task.setOperationCode("SET_CLIPBOARD");
        task.setParams("{\"text\":\"hello\"}");
        task.setRetryCount(0);
        task.setMaxRetry(3);

        Map<String, Object> params = new HashMap<>();
        params.put("text", "hello");

        MytosClient client = mock(MytosClient.class);
        when(deviceMapper.selectById(1L)).thenReturn(device);
        when(clientFactory.create("192.168.30.2", 9082)).thenReturn(client);
        when(objectMapper.readValue(anyString(), any(com.fasterxml.jackson.core.type.TypeReference.class))).thenReturn(params);

        // Act
        factory.create(task).run();

        // Assert
        verify(client, times(1)).execute(OperationCode.SET_CLIPBOARD, params);
        assertEquals("SUCCESS", task.getStatus());
    }
}
