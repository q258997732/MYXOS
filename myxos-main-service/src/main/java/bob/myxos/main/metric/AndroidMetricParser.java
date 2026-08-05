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
