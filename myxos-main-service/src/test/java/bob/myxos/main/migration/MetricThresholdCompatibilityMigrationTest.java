package bob.myxos.main.migration;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证已执行目录迁移的阈值兼容修复。 */
class MetricThresholdCompatibilityMigrationTest {

    @Test
    void 应启用安卓状态阈值并扩展枚举阈值文本列() throws Exception {
        String script = new String(Files.readAllBytes(Paths.get("src/main/resources/db/migration/"
                + "V15__enable_android_status_threshold_and_expand_threshold_text.sql")), StandardCharsets.UTF_8);

        assertTrue(script.contains("UPDATE metric_catalog"));
        assertTrue(script.contains("code = 'ANDROID_STATUS'"));
        assertTrue(script.contains("threshold_enabled = 1"));
        assertTrue(script.contains("ALTER TABLE threshold_rule MODIFY COLUMN threshold_text TEXT"));
    }
}
