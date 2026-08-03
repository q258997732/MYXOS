-- =====================================================================
-- V3__discover_progress.sql
-- 为 discover_task 表增加扫描进度字段，支持前端进度条展示
-- =====================================================================

DROP PROCEDURE IF EXISTS myxos_add_discover_progress_columns;

DELIMITER $$

CREATE PROCEDURE myxos_add_discover_progress_columns()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'discover_task'
          AND column_name = 'total_ip_count'
    ) THEN
        ALTER TABLE discover_task ADD COLUMN total_ip_count INT NOT NULL DEFAULT 0 COMMENT '待扫描 IP 总数';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'discover_task'
          AND column_name = 'scanned_ip_count'
    ) THEN
        ALTER TABLE discover_task ADD COLUMN scanned_ip_count INT NOT NULL DEFAULT 0 COMMENT '已扫描 IP 数量';
    END IF;
END$$

DELIMITER ;

CALL myxos_add_discover_progress_columns();

DROP PROCEDURE IF EXISTS myxos_add_discover_progress_columns;
