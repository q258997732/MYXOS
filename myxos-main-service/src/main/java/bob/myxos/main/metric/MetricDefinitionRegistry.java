package bob.myxos.main.metric;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 安卓只读诊断指标及其受控 ADB 命令目录。
 */
public final class MetricDefinitionRegistry {

    public static final String ANDROID_VERSION = "ANDROID_VERSION";
    public static final String ANDROID_MODEL = "ANDROID_MODEL";
    public static final String MEM_TOTAL_KB = "MEM_TOTAL_KB";
    public static final String MEM_AVAILABLE_KB = "MEM_AVAILABLE_KB";
    public static final String CPU_USAGE_PERCENT = "CPU_USAGE_PERCENT";
    public static final String TASK_TOTAL = "TASK_TOTAL";
    public static final String RECENT_APPS = "RECENT_APPS";
    public static final String ANDROID_STATUS = "ANDROID_STATUS";
    public static final String APP_PROCESS_STATE = "APP_PROCESS_STATE";

    private static final Map<String, String> READ_ONLY_ADB_COMMANDS;
    private static final List<MetricDefinition> DEFINITIONS;

    static {
        Map<String, String> commands = new LinkedHashMap<String, String>();
        commands.put(ANDROID_VERSION, "getprop ro.build.version.release");
        commands.put(ANDROID_MODEL, "getprop ro.product.model");
        commands.put(MEM_TOTAL_KB, "cat /proc/meminfo");
        commands.put(MEM_AVAILABLE_KB, "cat /proc/meminfo");
        commands.put(CPU_USAGE_PERCENT, "top -b -n 1");
        commands.put(TASK_TOTAL, "top -b -n 1");
        commands.put(RECENT_APPS, "dumpsys activity recents");
        commands.put(APP_PROCESS_STATE, "dumpsys activity processes");
        READ_ONLY_ADB_COMMANDS = Collections.unmodifiableMap(commands);

        DEFINITIONS = Collections.unmodifiableList(Arrays.asList(
                definition(ANDROID_VERSION, "安卓版本", "STRING", "系统", null, true),
                definition(ANDROID_MODEL, "安卓型号", "STRING", "系统", null, false),
                definition(MEM_TOTAL_KB, "内存总量", "NUMBER", "内存", "KB", true),
                definition(MEM_AVAILABLE_KB, "可用内存", "NUMBER", "内存", "KB", true),
                definition(CPU_USAGE_PERCENT, "CPU使用率", "NUMBER", "CPU", "%", true),
                definition(TASK_TOTAL, "任务总数", "NUMBER", "进程", "个", true),
                definition(RECENT_APPS, "最近应用", "STRING", "应用", null, false),
                definition(APP_PROCESS_STATE, "应用进程状态", "ENUM", "应用", null, true),
                new MetricDefinition(ANDROID_STATUS, "安卓状态", "ANDROID_INSTANCE", "ENUM", "状态", null,
                        null, false)
        ));
    }

    private MetricDefinitionRegistry() {
    }

    public static List<MetricDefinition> definitions() {
        return DEFINITIONS;
    }

    public static Optional<MetricDefinition> findByCode(String code) {
        for (MetricDefinition definition : DEFINITIONS) {
            if (definition.getCode().equals(code)) {
                return Optional.of(definition);
            }
        }
        return Optional.empty();
    }

    public static Optional<String> findReadOnlyAdbCommand(String commandKey) {
        return Optional.ofNullable(READ_ONLY_ADB_COMMANDS.get(commandKey));
    }

    public enum AndroidStatus {
        RUNNING,
        STOPPED,
        TRANSITION,
        UNKNOWN
    }

    public enum AppProcessStatus {
        FOREGROUND,
        ACTIVE,
        RUNNING,
        STOPPED
    }

    private static MetricDefinition definition(String code, String name, String valueType,
                                               String category, String unit,
                                               boolean thresholdEnabled) {
        return new MetricDefinition(code, name, "ANDROID_INSTANCE", valueType, category, unit, code,
                thresholdEnabled);
    }
}
