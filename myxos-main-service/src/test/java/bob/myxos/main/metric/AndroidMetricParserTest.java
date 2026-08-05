package bob.myxos.main.metric;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

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
}
