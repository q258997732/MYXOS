-- =====================================================================
-- V4__device_unique_index.sql
-- 为 device 表增加 IP+端口 唯一约束，防止并发发现任务重复插入
-- =====================================================================

DROP PROCEDURE IF EXISTS myxos_add_device_unique_index;

DELIMITER $$

CREATE PROCEDURE myxos_add_device_unique_index()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'device'
          AND column_name = 'active'
    ) THEN
        ALTER TABLE device ADD COLUMN active TINYINT
            GENERATED ALWAYS AS (IF(deleted = 0, 1, NULL)) STORED
            COMMENT '有效记录标记（用于唯一索引）';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'device'
          AND index_name = 'uk_device_ip_port_active'
    ) THEN
        ALTER TABLE device ADD UNIQUE INDEX uk_device_ip_port_active (ip, port, active);
    END IF;
END$$

DELIMITER ;

CALL myxos_add_device_unique_index();

DROP PROCEDURE IF EXISTS myxos_add_device_unique_index;
