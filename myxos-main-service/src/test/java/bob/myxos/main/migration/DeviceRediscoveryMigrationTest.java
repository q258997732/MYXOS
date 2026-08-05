package bob.myxos.main.migration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 设备重新发现迁移测试。
 */
@DisplayName("设备重新发现迁移测试")
class DeviceRediscoveryMigrationTest {

    private static final String 迁移脚本路径 = "src/main/resources/db/migration/"
            + "V9__allow_rediscover_deleted_device.sql";

    @Test
    @DisplayName("应删除旧索引并保留逻辑删除唯一索引")
    void 应删除旧索引并保留逻辑删除唯一索引() throws IOException {
        String 脚本内容 = new String(Files.readAllBytes(Paths.get(迁移脚本路径)), StandardCharsets.UTF_8);

        assertTrue(脚本内容.contains("DROP INDEX uk_ip_port ON device"));
        assertFalse(脚本内容.contains("DROP INDEX uk_device_ip_port_active ON device"));
        assertTrue(脚本内容.contains("column_name = 'active'"));
        assertTrue(脚本内容.contains("generation_expression"));
        assertTrue(脚本内容.contains("LOWER(extra)"));
        assertTrue(脚本内容.contains("REGEXP"));
        assertTrue(脚本内容.contains("deleted=0"));
        assertTrue(脚本内容.contains("index_name = 'uk_device_ip_port_active'"));
        assertTrue(脚本内容.contains("non_unique = 0"));
        assertTrue(脚本内容.contains("GROUP_CONCAT(column_name ORDER BY seq_in_index)"));
        assertTrue(脚本内容.contains("= 'ip,port,active'"));
        assertTrue(脚本内容.contains("SIGNAL SQLSTATE '45000'"));

        int 删除旧索引位置 = 脚本内容.indexOf("DROP INDEX uk_ip_port ON device");
        int active列校验位置 = 脚本内容.indexOf("column_name = 'active'");
        int 生成列表达式校验位置 = 脚本内容.indexOf("generation_expression");
        int 唯一索引校验位置 = 脚本内容.indexOf("GROUP_CONCAT(column_name ORDER BY seq_in_index)");
        assertTrue(active列校验位置 < 删除旧索引位置);
        assertTrue(生成列表达式校验位置 < 删除旧索引位置);
        assertTrue(唯一索引校验位置 < 删除旧索引位置);
    }
}
