package bob.myxos.main.migration;

import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证已执行目录迁移的阈值兼容修复。 */
class MetricThresholdCompatibilityMigrationTest {

    @Test
    void 应启用安卓状态阈值并扩展枚举阈值文本列() throws Exception {
        InputStream input = getClass().getResourceAsStream("/db/migration/"
                + "V15__enable_android_status_threshold_and_expand_threshold_text.sql");
        assertTrue(input != null, "迁移脚本应存在于 classpath");
        String script;
        try (InputStream stream = input) {
            script = new String(readAllBytes(stream), StandardCharsets.UTF_8);
        }

        assertTrue(script.contains("UPDATE metric_catalog"));
        assertTrue(script.contains("code = 'ANDROID_STATUS'"));
        assertTrue(script.contains("threshold_enabled = 1"));
        assertTrue(script.contains("ALTER TABLE threshold_rule MODIFY COLUMN threshold_text TEXT"));
    }

    private byte[] readAllBytes(InputStream input) throws Exception {
        java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
        byte[] buffer = new byte[1024];
        int length;
        while ((length = input.read(buffer)) != -1) {
            output.write(buffer, 0, length);
        }
        return output.toByteArray();
    }
}
