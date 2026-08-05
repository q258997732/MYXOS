-- 受控安卓只读 ADB 指标目录初始数据。
DROP PROCEDURE IF EXISTS myxos_seed_metric_catalog;

DELIMITER $$

CREATE PROCEDURE myxos_seed_metric_catalog()
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE table_schema = DATABASE() AND table_name = 'metric_catalog'
    ) THEN
        INSERT INTO metric_catalog (code, name, target_type, value_type, category, unit, command_key, threshold_enabled)
        SELECT 'ANDROID_VERSION', '安卓版本', 'ANDROID', 'STRING', '系统', NULL, 'ANDROID_VERSION', 1
        WHERE NOT EXISTS (SELECT 1 FROM metric_catalog WHERE code = 'ANDROID_VERSION' AND deleted = 0);
        INSERT INTO metric_catalog (code, name, target_type, value_type, category, unit, command_key, threshold_enabled)
        SELECT 'ANDROID_MODEL', '安卓型号', 'ANDROID', 'STRING', '系统', NULL, 'ANDROID_MODEL', 0
        WHERE NOT EXISTS (SELECT 1 FROM metric_catalog WHERE code = 'ANDROID_MODEL' AND deleted = 0);
        INSERT INTO metric_catalog (code, name, target_type, value_type, category, unit, command_key, threshold_enabled)
        SELECT 'MEM_TOTAL_KB', '内存总量', 'ANDROID', 'INTEGER', '内存', 'KB', 'MEM_TOTAL_KB', 1
        WHERE NOT EXISTS (SELECT 1 FROM metric_catalog WHERE code = 'MEM_TOTAL_KB' AND deleted = 0);
        INSERT INTO metric_catalog (code, name, target_type, value_type, category, unit, command_key, threshold_enabled)
        SELECT 'MEM_AVAILABLE_KB', '可用内存', 'ANDROID', 'INTEGER', '内存', 'KB', 'MEM_AVAILABLE_KB', 1
        WHERE NOT EXISTS (SELECT 1 FROM metric_catalog WHERE code = 'MEM_AVAILABLE_KB' AND deleted = 0);
        INSERT INTO metric_catalog (code, name, target_type, value_type, category, unit, command_key, threshold_enabled)
        SELECT 'CPU_USAGE_PERCENT', 'CPU使用率', 'ANDROID', 'DECIMAL', 'CPU', '%', 'CPU_USAGE_PERCENT', 1
        WHERE NOT EXISTS (SELECT 1 FROM metric_catalog WHERE code = 'CPU_USAGE_PERCENT' AND deleted = 0);
        INSERT INTO metric_catalog (code, name, target_type, value_type, category, unit, command_key, threshold_enabled)
        SELECT 'TASK_TOTAL', '任务总数', 'ANDROID', 'INTEGER', '进程', '个', 'TASK_TOTAL', 1
        WHERE NOT EXISTS (SELECT 1 FROM metric_catalog WHERE code = 'TASK_TOTAL' AND deleted = 0);
        INSERT INTO metric_catalog (code, name, target_type, value_type, category, unit, command_key, threshold_enabled)
        SELECT 'RECENT_APPS', '最近应用', 'ANDROID', 'STRING', '应用', NULL, 'RECENT_APPS', 0
        WHERE NOT EXISTS (SELECT 1 FROM metric_catalog WHERE code = 'RECENT_APPS' AND deleted = 0);
        INSERT INTO metric_catalog (code, name, target_type, value_type, category, unit, command_key, threshold_enabled)
        SELECT 'ANDROID_STATUS', '安卓状态', 'ANDROID', 'ENUM', '状态', NULL, 'ANDROID_STATUS', 0
        WHERE NOT EXISTS (SELECT 1 FROM metric_catalog WHERE code = 'ANDROID_STATUS' AND deleted = 0);
    END IF;
END$$

DELIMITER ;

CALL myxos_seed_metric_catalog();

DROP PROCEDURE IF EXISTS myxos_seed_metric_catalog;
