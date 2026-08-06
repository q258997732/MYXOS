package bob.myxos.main.metric;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 解析受控只读 ADB 命令的完整输出。
 */
public final class AndroidMetricParser {

    private static final Pattern MEM_TOTAL_PATTERN = Pattern.compile("(?m)^MemTotal:\\s*(\\d+)\\s*kB\\s*$");
    private static final Pattern MEM_AVAILABLE_PATTERN = Pattern.compile("(?m)^MemAvailable:\\s*(\\d+)\\s*kB\\s*$");
    private static final Pattern TASK_TOTAL_PATTERN = Pattern.compile("(?im)^Tasks:\\s*(\\d+)\\s+total(?:,|\\s|$)");
    private static final Pattern CPU_USAGE_PATTERN = Pattern.compile(
            "(?im)^\\s*(\\d+(?:\\.\\d+)?)%cpu\\b[^\\r\\n]*?(\\d+(?:\\.\\d+)?)%idle\\b");
    private static final Pattern PROCESS_LINE_PATTERN = Pattern.compile(
            "(?m)^\\s*(?:PERS\\s+#\\s*\\d+|Proc\\s+#\\s*\\d+):.*?\\s"
                    + "(?:[A-Z]/[A-Z]/)?(TOP|BTOP|FGS|BFGS|IMPF|IMPB|SVC|PER|CACHED)\\s+.*?\\s"
                    + "(\\d+):([A-Za-z0-9_.$]+)(?::[A-Za-z0-9_.$]+)?/u[0-9A-Za-z]+\\b");

    public Optional<Long> parseMemTotalKb(String output) {
        return parseLong(MEM_TOTAL_PATTERN, output);
    }

    public Optional<Long> parseMemAvailableKb(String output) {
        return parseLong(MEM_AVAILABLE_PATTERN, output);
    }

    public Optional<Integer> parseTaskTotal(String output) {
        if (output == null) {
            return Optional.empty();
        }
        Matcher matcher = TASK_TOTAL_PATTERN.matcher(output);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Integer.valueOf(matcher.group(1)));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    public Optional<BigDecimal> parseCpuUsagePercent(String output) {
        if (output == null) {
            return Optional.empty();
        }
        Matcher matcher = CPU_USAGE_PATTERN.matcher(output);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            BigDecimal total = new BigDecimal(matcher.group(1));
            BigDecimal idle = new BigDecimal(matcher.group(2));
            if (total.compareTo(BigDecimal.ZERO) < 0 || idle.compareTo(BigDecimal.ZERO) < 0
                    || idle.compareTo(total) > 0) {
                return Optional.empty();
            }
            if (total.compareTo(BigDecimal.ZERO) == 0) {
                return Optional.of(BigDecimal.ZERO.setScale(2));
            }
            BigDecimal usage = total.subtract(idle)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(total, 2, RoundingMode.HALF_UP);
            if (usage.compareTo(BigDecimal.ZERO) < 0 || usage.compareTo(BigDecimal.valueOf(100)) > 0) {
                return Optional.empty();
            }
            return Optional.of(usage);
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }

    /** 按包名解析进程状态，同一包存在多个进程时返回最高状态。 */
    public Optional<AppProcessState> parseAppProcessState(String output, String packageName) {
        if (packageName == null || packageName.trim().isEmpty()) {
            return Optional.empty();
        }
        AppProcessState best = new AppProcessState("STOPPED", null, null);
        if (output == null) {
            return Optional.of(best);
        }
        Matcher matcher = PROCESS_LINE_PATTERN.matcher(output);
        while (matcher.find()) {
            if (!packageName.trim().equals(matcher.group(3))) {
                continue;
            }
            String rawState = matcher.group(1);
            AppProcessState candidate = new AppProcessState(normalizeProcessState(rawState),
                    Integer.valueOf(matcher.group(2)), rawState);
            if (stateRank(candidate.status) > stateRank(best.status)) {
                best = candidate;
            }
        }
        return Optional.of(best);
    }

    private String normalizeProcessState(String rawState) {
        if ("TOP".equals(rawState) || "BTOP".equals(rawState)) {
            return "FOREGROUND";
        }
        if ("PER".equals(rawState) || "FGS".equals(rawState) || "BFGS".equals(rawState)
                || "IMPF".equals(rawState) || "IMPB".equals(rawState)) {
            return "ACTIVE";
        }
        return "RUNNING";
    }

    private int stateRank(String state) {
        if ("FOREGROUND".equals(state)) return 3;
        if ("ACTIVE".equals(state)) return 2;
        if ("RUNNING".equals(state)) return 1;
        return 0;
    }

    public static final class AppProcessState {
        private final String status;
        private final Integer pid;
        private final String rawState;

        AppProcessState(String status, Integer pid, String rawState) {
            this.status = status;
            this.pid = pid;
            this.rawState = rawState;
        }

        public String getStatus() { return status; }
        public Integer getPid() { return pid; }
        public String getRawState() { return rawState; }
    }

    private Optional<Long> parseLong(Pattern pattern, String output) {
        if (output == null) {
            return Optional.empty();
        }
        Matcher matcher = pattern.matcher(output);
        if (!matcher.find()) {
            return Optional.empty();
        }
        try {
            return Optional.of(Long.valueOf(matcher.group(1)));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
    }
}
