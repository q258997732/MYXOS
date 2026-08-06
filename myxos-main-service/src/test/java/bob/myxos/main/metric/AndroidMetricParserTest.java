package bob.myxos.main.metric;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AndroidMetricParserTest {

    private final AndroidMetricParser parser = new AndroidMetricParser();

    @Test
    void 应按包名解析应用进程的最高运行状态() {
        String 输出 = "    Proc # 0: fg     T/A/TOP  LCMN  t: 0 21550:com.tencent.wework/u0a93 (top-activity)\n"
                + "    Proc # 4: prcp   F/S/FGS  -CMN  t: 0 788:com.kingsware.rpa/u0a95 (fg-service)\n"
                + "    Proc # 5: prcp   F/S/FGS  ---N  t: 0 985:com.kingsware.rpa:cactusRemoteService/u0a95 (fg-service)\n"
                + "    Proc # 3: svc    b/ /SVC  ----  t: 0 766:com.tencent.wework:pushservice/u0a93 (started-services)\n";

        AndroidMetricParser.AppProcessState 前台 = parser.parseAppProcessState(输出, "com.tencent.wework")
                .orElseThrow(AssertionError::new);
        AndroidMetricParser.AppProcessState 活跃 = parser.parseAppProcessState(输出, "com.kingsware.rpa")
                .orElseThrow(AssertionError::new);
        AndroidMetricParser.AppProcessState 运行 = parser.parseAppProcessState(输出, "com.example.missing")
                .orElseThrow(AssertionError::new);

        assertEquals("FOREGROUND", 前台.getStatus());
        assertEquals(Integer.valueOf(21550), 前台.getPid());
        assertEquals("TOP", 前台.getRawState());
        assertEquals("ACTIVE", 活跃.getStatus());
        assertEquals(Integer.valueOf(788), 活跃.getPid());
        assertEquals("FGS", 活跃.getRawState());
        assertEquals("STOPPED", 运行.getStatus());
    }

    @Test
    void 应从应用进程记录区块解析前台服务状态() {
        String 输出 = "  *APP* UID 10095 ProcessRecord{3686b 788:com.kingsware.rpa/u0a95}\n"
                + "    packageList={com.kingsware.rpa}\n"
                + "    pid=788\n"
                + "    curProcState=4\n"
                + "    mHasForegroundServices=true\n";

        AndroidMetricParser.AppProcessState 状态 = parser.parseAppProcessState(输出, "com.kingsware.rpa")
                .orElseThrow(AssertionError::new);

        assertEquals("ACTIVE", 状态.getStatus());
        assertEquals(Integer.valueOf(788), 状态.getPid());
        assertEquals("FGS", 状态.getRawState());
    }

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
    void CPU字段跨行时应返回空结果() {
        String 输出 = "800%cpu 36%user 0%nice 16%sys\n736%idle 4%iow\n";

        assertFalse(parser.parseCpuUsagePercent(输出).isPresent());
    }

    @Test
    void idle大于总CPU时应返回空结果() {
        String 输出 = "100%cpu 20%user 120%idle 0%iow\n";

        assertFalse(parser.parseCpuUsagePercent(输出).isPresent());
    }

    @Test
    void 总CPU为零且idle为零时应返回零使用率() {
        String 输出 = "0%cpu 0%user 0%idle 0%iow\n";

        assertEquals(new BigDecimal("0.00"), parser.parseCpuUsagePercent(输出).orElseThrow(AssertionError::new));
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
        值类型.put(MetricDefinitionRegistry.APP_PROCESS_STATE, "ENUM");
        值类型.put(MetricDefinitionRegistry.ANDROID_STATUS, "ENUM");
        Set<String> Shell命令键 = new HashSet<String>();
        Shell命令键.add(MetricDefinitionRegistry.ANDROID_VERSION);
        Shell命令键.add(MetricDefinitionRegistry.ANDROID_MODEL);
        Shell命令键.add(MetricDefinitionRegistry.MEM_TOTAL_KB);
        Shell命令键.add(MetricDefinitionRegistry.MEM_AVAILABLE_KB);
        Shell命令键.add(MetricDefinitionRegistry.CPU_USAGE_PERCENT);
        Shell命令键.add(MetricDefinitionRegistry.TASK_TOTAL);
        Shell命令键.add(MetricDefinitionRegistry.RECENT_APPS);
        Shell命令键.add(MetricDefinitionRegistry.APP_PROCESS_STATE);

        assertEquals(9, MetricDefinitionRegistry.definitions().size());
        int Shell命令数量 = 0;
        for (MetricDefinition 定义 : MetricDefinitionRegistry.definitions()) {
            assertEquals("ANDROID_INSTANCE", 定义.getTargetType());
            assertEquals(值类型.get(定义.getCode()), 定义.getValueType());
            if (Shell命令键.contains(定义.getCode())) {
                assertEquals(定义.getCode(), 定义.getCommandKey());
                assertTrue(MetricDefinitionRegistry.findReadOnlyAdbCommand(定义.getCommandKey()).isPresent());
                Shell命令数量++;
            } else {
                assertEquals(MetricDefinitionRegistry.ANDROID_STATUS, 定义.getCode());
                assertEquals(null, 定义.getCommandKey());
            }
        }
        assertEquals(8, Shell命令数量);
        assertFalse(MetricDefinitionRegistry.findReadOnlyAdbCommand(MetricDefinitionRegistry.ANDROID_STATUS)
                .isPresent());
    }
}
