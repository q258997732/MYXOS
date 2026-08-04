package bob.myxos.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 安卓实例状态解析工具测试
 */
@DisplayName("安卓状态解析工具测试")
class AndroidStatusParserTest {

    @ParameterizedTest
    @CsvSource({
            "running, RUNNING",
            "RUNNING, RUNNING",
            "run, RUNNING",
            "booted, RUNNING",
            "online, RUNNING",
            "true, RUNNING",
            "up, RUNNING",
            "active, RUNNING",
            "stopped, STOPPED",
            "STOPPED, STOPPED",
            "stop, STOPPED",
            "offline, STOPPED",
            "down, STOPPED",
            "false, STOPPED",
            "exited, STOPPED",
            "inactive, STOPPED",
            "not running, STOPPED",
            "not_running, STOPPED",
            "not-running, STOPPED",
            "notrunning, STOPPED",
            "Status: running, RUNNING",
            "state: not running, STOPPED",
            "running not, UNKNOWN",
            "knot running, RUNNING",
            "created, TRANSITION",
            "paused, TRANSITION",
            "restarting, TRANSITION",
            "dead, TRANSITION",
            "unknown, UNKNOWN",
            "foobar, UNKNOWN",
            "' ', UNKNOWN",
            "'', UNKNOWN",
            ", UNKNOWN"
    })
    void parse_should_return_expected_status(String input, String expected) {
        assertEquals(expected, AndroidStatusParser.parse(input));
    }
}
