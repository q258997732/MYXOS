package bob.myxos.main.metric;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AndroidMetricParserTest {

    private final AndroidMetricParser parser = new AndroidMetricParser();

    @Test
    void 应从完整top输出解析CPU使用率() {
        String 输出 = "Tasks: 99 total, 1 running, 98 sleeping, 0 stopped, 0 zombie\n"
                + "800%cpu 36%user 20%nice 40%sys 736%idle 4%iow 0%irq 0%sirq 0%host\n";

        assertEquals(new BigDecimal("8.00"), parser.parseCpuUsagePercent(输出).orElseThrow(AssertionError::new));
    }

    @Test
    void 应从完整top输出解析任务总数() {
        String 输出 = "Tasks: 99 total, 1 running, 98 sleeping, 0 stopped, 0 zombie\n"
                + "800%cpu 36%user 20%nice 40%sys 736%idle 4%iow 0%irq 0%sirq 0%host\n";

        assertEquals(Integer.valueOf(99), parser.parseTaskTotal(输出).orElseThrow(AssertionError::new));
    }

    @Test
    void 缺少MemAvailable时应返回空结果() {
        String 输出 = "MemTotal:        8027632 kB\nMemFree:          124000 kB\n";

        assertFalse(parser.parseMemAvailableKb(输出).isPresent());
    }

    @Test
    void 指标定义应与共享枚举值保持一致() {
        Map<String, String> 值类型 = new HashMap<String, String>();
        值类型.put(MetricDefinitionRegistry.ANDROID_VERSION, "STRING");
        值类型.put(MetricDefinitionRegistry.ANDROID_MODEL, "STRING");
        值类型.put(MetricDefinitionRegistry.MEM_TOTAL_KB, "NUMBER");
        值类型.put(MetricDefinitionRegistry.MEM_AVAILABLE_KB, "NUMBER");
        值类型.put(MetricDefinitionRegistry.CPU_USAGE_PERCENT, "NUMBER");
        值类型.put(MetricDefinitionRegistry.TASK_TOTAL, "NUMBER");
        值类型.put(MetricDefinitionRegistry.RECENT_APPS, "STRING");
        值类型.put(MetricDefinitionRegistry.ANDROID_STATUS, "ENUM");

        assertEquals(8, MetricDefinitionRegistry.definitions().size());
        for (MetricDefinition 定义 : MetricDefinitionRegistry.definitions()) {
            assertEquals("ANDROID_INSTANCE", 定义.getTargetType());
            assertEquals(值类型.get(定义.getCode()), 定义.getValueType());
            assertTrue(定义.getCommandKey() != null && !定义.getCommandKey().isEmpty());
        }
    }
}
