package bob.myxos.common.util;

import java.util.regex.Pattern;

/**
 * 安卓实例状态解析工具
 * <p>
 * 统一归一化 MYTOS 设备端返回的原始状态字符串，避免主服务与采集服务状态不一致。
 */
public final class AndroidStatusParser {

    public static final String RUNNING = "RUNNING";
    public static final String STOPPED = "STOPPED";
    public static final String TRANSITION = "TRANSITION";
    public static final String UNKNOWN = "UNKNOWN";

    /** 匹配独立的 "not running" / "not run" 否定短语 */
    private static final Pattern NOT_RUNNING = Pattern.compile("\\bnot\\s+running\\b");
    private static final Pattern NOT_RUN = Pattern.compile("\\bnot\\s+run\\b");

    /** 匹配独立的 "not" 单词，用于子串 running 排除 */
    private static final Pattern NOT_WORD = Pattern.compile("\\bnot\\b");

    private AndroidStatusParser() {
    }

    /**
     * 将设备端返回的原始状态字符串解析为标准状态码。
     *
     * @param rawStatus 设备端返回的原始状态，可能为 null
     * @return RUNNING / STOPPED / TRANSITION / UNKNOWN
     */
    public static String parse(String rawStatus) {
        if (rawStatus == null) {
            return UNKNOWN;
        }
        String s = rawStatus.trim().toLowerCase().replace('-', ' ').replace('_', ' ');
        if (s.isEmpty()) {
            return UNKNOWN;
        }

        // 明确否定 running 的短语（含连字符/下划线变体及前后缀）
        if (NOT_RUNNING.matcher(s).find() || NOT_RUN.matcher(s).find() || s.equals("notrunning")) {
            return STOPPED;
        }

        // 精确匹配：避免 "not running" 等包含子串的字符串误判
        if (isRunningExact(s)) {
            return RUNNING;
        }
        if (isStoppedExact(s)) {
            return STOPPED;
        }
        if (isTransitionExact(s)) {
            return TRANSITION;
        }

        // 子串匹配（带边界排除）
        if (s.contains("running") && !NOT_WORD.matcher(s).find()) {
            return RUNNING;
        }
        if (containsTransition(s)) {
            return TRANSITION;
        }
        if (s.contains("stopped") || s.contains("exited")) {
            return STOPPED;
        }

        return UNKNOWN;
    }

    private static boolean isRunningExact(String s) {
        return s.equals("running") || s.equals("run") || s.equals("booted")
                || s.equals("online") || s.equals("true") || s.equals("up") || s.equals("active");
    }

    private static boolean isStoppedExact(String s) {
        return s.equals("stopped") || s.equals("stop") || s.equals("offline")
                || s.equals("down") || s.equals("false") || s.equals("exited") || s.equals("inactive");
    }

    private static boolean isTransitionExact(String s) {
        return s.equals("created") || s.equals("paused") || s.equals("restarting") || s.equals("dead");
    }

    private static boolean containsTransition(String s) {
        return s.contains("restarting") || s.contains("starting") || s.contains("stopping");
    }
}
