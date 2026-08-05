package bob.myxos.main.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

/** 验证指标模板迁移脚本的关键兼容性约定。 */
class MetricTemplateMigrationTest {

    @Test
    void 应创建指标模板相关表并保留审计字段() throws IOException {
        String script = readMigration("V10__metric_template_schema.sql");

        assertTrue(script.contains("CREATE TABLE IF NOT EXISTS metric_catalog"));
        assertTrue(script.contains("CREATE TABLE IF NOT EXISTS metric_template"));
        assertTrue(script.contains("CREATE TABLE IF NOT EXISTS metric_template_item"));
        assertTrue(script.contains("CREATE TABLE IF NOT EXISTS metric_binding"));
        assertTrue(script.contains("metric_catalog_id"));
        assertTrue(script.contains("enum_options"));
        assertTrue(script.contains("who_created"));
        assertTrue(script.contains("when_created"));
        assertTrue(script.contains("who_modified"));
        assertTrue(script.contains("when_modified"));
        assertTrue(script.contains("deleted"));
    }

    @Test
    void 应扩展历史指标字段并按指标类型回填编码() throws IOException {
        String script = readMigration("V10__metric_template_schema.sql");

        assertTrue(script.contains("ADD COLUMN metric_code"));
        assertTrue(script.contains("ADD COLUMN target_type"));
        assertTrue(script.contains("ADD COLUMN android_name"));
        assertTrue(script.contains("UPDATE metric_snapshot SET metric_code = metric_type"));
        assertTrue(script.contains("UPDATE threshold_rule SET metric_code = metric_type"));
    }

    @Test
    void V10应保持已提交的绑定频率默认值和索引定义() throws IOException {
        String script = readMigration("V10__metric_template_schema.sql");

        assertTrue(script.contains("android_name      VARCHAR(128) NOT NULL DEFAULT ''"));
        assertTrue(script.contains("interval_sec      INT          NOT NULL DEFAULT 60,"));
        assertTrue(script.contains("UNIQUE KEY uk_metric_binding_target (device_id, android_name, metric_code, deleted)"));
        assertTrue(script.contains("INDEX idx_metric_binding_due (enabled, next_collect_at, deleted)"));
    }

    @Test
    void V11应修复绑定频率并将唯一索引迁移为active键() throws IOException {
        String script = readMigration("V11__metric_template_binding_fixes.sql");

        assertTrue(script.contains("ALTER TABLE metric_binding MODIFY COLUMN interval_sec INT NULL"));
        assertTrue(script.contains("GENERATED ALWAYS AS (IF(deleted = 0, 1, NULL))"));
        assertTrue(script.contains("DROP INDEX uk_metric_catalog_code ON metric_catalog"));
        assertTrue(script.contains("DROP INDEX uk_metric_template_name ON metric_template"));
        assertTrue(script.contains("DROP INDEX uk_metric_template_item ON metric_template_item"));
        assertTrue(script.contains("DROP INDEX uk_metric_binding_target ON metric_binding"));
        assertTrue(script.contains("UNIQUE KEY uk_metric_catalog_code (code, active)"));
        assertTrue(script.contains("UNIQUE KEY uk_metric_template_name (name, active)"));
        assertTrue(script.contains("UNIQUE KEY uk_metric_template_item (template_id, metric_catalog_id, active)"));
        assertTrue(script.contains("UNIQUE KEY uk_metric_binding_target (device_id, android_name, metric_code, active)"));
        assertTrue(script.contains("DROP INDEX idx_metric_binding_due ON metric_binding"));
        assertTrue(script.contains("INDEX idx_metric_binding_due (enabled, deleted, next_collect_at)"));
    }

    private String readMigration(String fileName) throws IOException {
        String resource = "db/migration/" + fileName;
        try (InputStream input = getClass().getClassLoader().getResourceAsStream(resource)) {
            if (input == null) {
                throw new IOException("未找到迁移资源: " + resource);
            }
            byte[] bytes = new byte[0];
            java.io.ByteArrayOutputStream output = new java.io.ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            bytes = output.toByteArray();
            return new String(bytes, StandardCharsets.UTF_8);
        }
    }
}
