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
    private static final Pattern CPU_TOTAL_PATTERN = Pattern.compile("(?im)^\\s*(\\d+(?:\\.\\d+)?)%cpu\\b");
    private static final Pattern CPU_IDLE_PATTERN = Pattern.compile("(?i)(\\d+(?:\\.\\d+)?)%idle\\b");

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
        Matcher totalMatcher = CPU_TOTAL_PATTERN.matcher(output);
        Matcher idleMatcher = CPU_IDLE_PATTERN.matcher(output);
        if (!totalMatcher.find() || !idleMatcher.find()) {
            return Optional.empty();
        }
        try {
            BigDecimal total = new BigDecimal(totalMatcher.group(1));
            BigDecimal idle = new BigDecimal(idleMatcher.group(1));
            if (total.compareTo(BigDecimal.ZERO) <= 0) {
                return Optional.empty();
            }
            return Optional.of(total.subtract(idle)
                    .multiply(BigDecimal.valueOf(100))
                    .divide(total, 2, RoundingMode.HALF_UP));
        } catch (NumberFormatException ignored) {
            return Optional.empty();
        }
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
