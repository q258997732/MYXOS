package bob.myxos.collector.collector;

import bob.myxos.domain.entity.Device;
import bob.myxos.domain.entity.MetricBinding;
import bob.myxos.mytos.MytosClient;
import bob.myxos.mytos.MytosClientFactory;
import bob.myxos.mytos.dto.ShellResp;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoundMetricCollectorTest {

    @Mock
    private MytosClientFactory clientFactory;
    @Mock
    private MytosClient client;

    @Test
    void 数值指标解析失败应写入未知值而不是伪造零值() {
        Device device = device();
        ShellResp response = new ShellResp();
        response.setMsg("MemTotal: 100 kB");
        when(clientFactory.create(anyString(), anyInt())).thenReturn(client);
        when(client.shell(anyString(), anyString(), anyString())).thenReturn(response);

        MetricExecutionResult result = new BoundMetricCollector(clientFactory).collect(device,
                binding("MEM_AVAILABLE_KB"));

        assertEquals("UNKNOWN", result.getSnapshot().getMetricValue());
        assertNull(result.getSnapshot().getMetricNum());
    }

    private Device device() {
        Device device = new Device();
        device.setId(1L);
        device.setIp("192.168.1.2");
        device.setPort(8080);
        return device;
    }

    private MetricBinding binding(String code) {
        MetricBinding binding = new MetricBinding();
        binding.setDeviceId(1L);
        binding.setAndroidName("a-1");
        binding.setTargetType("ANDROID_INSTANCE");
        binding.setMetricCode(code);
        return binding;
    }
}
