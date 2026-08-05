-- =====================================================================
-- V9__allow_rediscover_deleted_device.sql
-- 删除阻止逻辑删除设备重新发现的旧唯一索引，并校验新唯一索引仍然存在
-- =====================================================================

DROP PROCEDURE IF EXISTS myxos_allow_rediscover_deleted_device;

DELIMITER $$

CREATE PROCEDURE myxos_allow_rediscover_deleted_device()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE()
          AND table_name = 'device'
          AND column_name = 'active'
          AND LOWER(extra) LIKE '%generated%'
          AND LOWER(
              REPLACE(
                  REPLACE(
                      REPLACE(
                          REPLACE(generation_expression, '`', ''),
                          ' ', ''),
                      CHAR(10), ''),
                  CHAR(13), '')
          ) REGEXP 'if\\(\\(*deleted=0\\)*,1,null\\)'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '缺少或损坏设备逻辑删除生成列 active';
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'device'
          AND index_name = 'uk_device_ip_port_active'
          AND non_unique = 0
        GROUP BY index_name
        HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'ip,port,active'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '缺少或损坏设备逻辑删除唯一索引 uk_device_ip_port_active';
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE()
          AND table_name = 'device'
          AND index_name = 'uk_ip_port'
    ) THEN
        DROP INDEX uk_ip_port ON device;
    END IF;
END$$

DELIMITER ;

CALL myxos_allow_rediscover_deleted_device();

DROP PROCEDURE IF EXISTS myxos_allow_rediscover_deleted_device;
