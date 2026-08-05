package bob.myxos.main.migration;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 指标模板迁移脚本测试。
 */
class MetricTemplateMigrationTest {

    private static final String 迁移脚本路径 = "src/main/resources/db/migration/"
            + "V10__metric_template_schema.sql";
    private static final String 绑定修复迁移脚本路径 = "src/main/resources/db/migration/"
            + "V11__metric_template_binding_fixes.sql";

    @Test
    void 应创建指标模板相关表并保留审计字段() throws IOException {
        String 脚本内容 = 读取脚本();

        assertTrue(脚本内容.contains("CREATE TABLE IF NOT EXISTS metric_catalog"));
        assertTrue(脚本内容.contains("CREATE TABLE IF NOT EXISTS metric_template"));
        assertTrue(脚本内容.contains("CREATE TABLE IF NOT EXISTS metric_template_item"));
        assertTrue(脚本内容.contains("CREATE TABLE IF NOT EXISTS metric_binding"));
        assertTrue(脚本内容.contains("metric_catalog_id"));
        assertTrue(脚本内容.contains("enum_options"));
        assertTrue(脚本内容.contains("who_created"));
        assertTrue(脚本内容.contains("when_created"));
        assertTrue(脚本内容.contains("who_modified"));
        assertTrue(脚本内容.contains("when_modified"));
        assertTrue(脚本内容.contains("deleted"));
    }

    @Test
    void 应扩展历史指标字段并按metricType回填metricCode() throws IOException {
        String 脚本内容 = 读取脚本();

        assertTrue(脚本内容.contains("ADD COLUMN IF NOT EXISTS metric_code"));
        assertTrue(脚本内容.contains("ADD COLUMN IF NOT EXISTS target_type"));
        assertTrue(脚本内容.contains("ADD COLUMN IF NOT EXISTS android_name"));
        assertTrue(脚本内容.contains("UPDATE metric_snapshot SET metric_code = metric_type"));
        assertTrue(脚本内容.contains("UPDATE threshold_rule SET metric_code = metric_type"));
    }

    @Test
    void V10应保持已提交的绑定频率默认值和索引定义() throws IOException {
        String 脚本内容 = 读取脚本();

        assertTrue(脚本内容.contains("android_name    VARCHAR(128) NOT NULL DEFAULT ''"));
        assertTrue(脚本内容.contains("interval_sec      INT          NOT NULL DEFAULT 60,"));
        assertTrue(脚本内容.contains("UNIQUE KEY uk_metric_binding_target (device_id, android_name, metric_code, deleted)"));
        assertTrue(脚本内容.contains("INDEX idx_metric_binding_due (enabled, next_collect_at, deleted)"));
    }

    @Test
    void V11应修复绑定频率并将唯一索引迁移为active键() throws IOException {
        String 脚本内容 = 读取绑定修复脚本();

        assertTrue(脚本内容.contains("ALTER TABLE metric_binding MODIFY COLUMN interval_sec INT NULL"));
        assertTrue(脚本内容.contains("GENERATED ALWAYS AS (IF(deleted = 0, 1, NULL))"));
        assertTrue(脚本内容.contains("DROP INDEX uk_metric_catalog_code ON metric_catalog"));
        assertTrue(脚本内容.contains("DROP INDEX uk_metric_template_name ON metric_template"));
        assertTrue(脚本内容.contains("DROP INDEX uk_metric_template_item ON metric_template_item"));
        assertTrue(脚本内容.contains("DROP INDEX uk_metric_binding_target ON metric_binding"));
        assertTrue(脚本内容.contains("UNIQUE KEY uk_metric_catalog_code (code, active)"));
        assertTrue(脚本内容.contains("UNIQUE KEY uk_metric_template_name (name, active)"));
        assertTrue(脚本内容.contains("UNIQUE KEY uk_metric_template_item (template_id, metric_catalog_id, active)"));
        assertTrue(脚本内容.contains("UNIQUE KEY uk_metric_binding_target (device_id, android_name, metric_code, active)"));
        assertTrue(脚本内容.contains("DROP INDEX idx_metric_binding_due ON metric_binding"));
        assertTrue(脚本内容.contains("INDEX idx_metric_binding_due (enabled, deleted, next_collect_at)"));
    }

    private String 读取脚本() throws IOException {
        return new String(Files.readAllBytes(Paths.get(迁移脚本路径)), StandardCharsets.UTF_8);
    }

    private String 读取绑定修复脚本() throws IOException {
        return new String(Files.readAllBytes(Paths.get(绑定修复迁移脚本路径)), StandardCharsets.UTF_8);
    }
}
