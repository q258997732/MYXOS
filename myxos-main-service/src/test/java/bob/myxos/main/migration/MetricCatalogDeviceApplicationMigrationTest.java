package bob.myxos.main.migration;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MetricCatalogDeviceApplicationMigrationTest {

    @Test
    void 应提供目录默认频率并保留既有设备绑定() throws IOException {
        String script = readMigration();

        assertTrue(script.contains("ADD COLUMN default_interval_sec INT NOT NULL DEFAULT 60"));
        assertFalse(script.contains("INSERT INTO metric_binding"));
        assertFalse(script.contains("metric_template_item"));
    }

    private String readMigration() throws IOException {
        try (InputStream input = getClass().getClassLoader()
                .getResourceAsStream("db/migration/V17__metric_catalog_device_application.sql")) {
            if (input == null) {
                throw new IOException("未找到 V17 指标目录设备应用迁移脚本");
            }
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            return new String(output.toByteArray(), StandardCharsets.UTF_8);
        }
    }
}
