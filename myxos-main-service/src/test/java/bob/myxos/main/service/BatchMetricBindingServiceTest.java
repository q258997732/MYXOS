package bob.myxos.main.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class BatchMetricBindingServiceTest {

    @Test
    void 应提供按设备和安卓实例表达批量目标的请求类型() {
        assertDoesNotThrow(() -> Class.forName("bob.myxos.main.dto.BatchMetricBindingReq"));
    }
}
