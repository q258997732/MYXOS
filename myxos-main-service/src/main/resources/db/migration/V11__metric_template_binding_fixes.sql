-- 修复指标模板绑定频率继承和逻辑删除后的唯一键冲突。

ALTER TABLE metric_binding MODIFY COLUMN interval_sec INT NULL;

DROP PROCEDURE IF EXISTS myxos_metric_template_binding_fixes;

DELIMITER $$

CREATE PROCEDURE myxos_metric_template_binding_fixes()
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'metric_catalog' AND column_name = 'active'
    ) THEN
        ALTER TABLE metric_catalog
            ADD COLUMN active TINYINT GENERATED ALWAYS AS (IF(deleted = 0, 1, NULL)) STORED;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'metric_template' AND column_name = 'active'
    ) THEN
        ALTER TABLE metric_template
            ADD COLUMN active TINYINT GENERATED ALWAYS AS (IF(deleted = 0, 1, NULL)) STORED;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'metric_template_item' AND column_name = 'active'
    ) THEN
        ALTER TABLE metric_template_item
            ADD COLUMN active TINYINT GENERATED ALWAYS AS (IF(deleted = 0, 1, NULL)) STORED;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_schema = DATABASE() AND table_name = 'metric_binding' AND column_name = 'active'
    ) THEN
        ALTER TABLE metric_binding
            ADD COLUMN active TINYINT GENERATED ALWAYS AS (IF(deleted = 0, 1, NULL)) STORED;
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'metric_catalog'
          AND index_name = 'uk_metric_catalog_code'
        GROUP BY index_name
        HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'code,deleted'
    ) THEN
        DROP INDEX uk_metric_catalog_code ON metric_catalog;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'metric_catalog'
          AND index_name = 'uk_metric_catalog_code'
        GROUP BY index_name
        HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'code,active'
    ) THEN
        ALTER TABLE metric_catalog ADD UNIQUE KEY uk_metric_catalog_code (code, active);
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'metric_template'
          AND index_name = 'uk_metric_template_name'
        GROUP BY index_name
        HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'name,deleted'
    ) THEN
        DROP INDEX uk_metric_template_name ON metric_template;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'metric_template'
          AND index_name = 'uk_metric_template_name'
        GROUP BY index_name
        HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'name,active'
    ) THEN
        ALTER TABLE metric_template ADD UNIQUE KEY uk_metric_template_name (name, active);
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'metric_template_item'
          AND index_name = 'uk_metric_template_item'
        GROUP BY index_name
        HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'template_id,metric_catalog_id,deleted'
    ) THEN
        DROP INDEX uk_metric_template_item ON metric_template_item;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'metric_template_item'
          AND index_name = 'uk_metric_template_item'
        GROUP BY index_name
        HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'template_id,metric_catalog_id,active'
    ) THEN
        ALTER TABLE metric_template_item
            ADD UNIQUE KEY uk_metric_template_item (template_id, metric_catalog_id, active);
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'metric_binding'
          AND index_name = 'uk_metric_binding_target'
        GROUP BY index_name
        HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'device_id,android_name,metric_code,deleted'
    ) THEN
        DROP INDEX uk_metric_binding_target ON metric_binding;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'metric_binding'
          AND index_name = 'uk_metric_binding_target'
        GROUP BY index_name
        HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'device_id,android_name,metric_code,active'
    ) THEN
        ALTER TABLE metric_binding
            ADD UNIQUE KEY uk_metric_binding_target (device_id, android_name, metric_code, active);
    END IF;

    IF EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'metric_binding'
          AND index_name = 'idx_metric_binding_due'
        GROUP BY index_name
        HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'enabled,next_collect_at,deleted'
    ) THEN
        DROP INDEX idx_metric_binding_due ON metric_binding;
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM information_schema.statistics
        WHERE table_schema = DATABASE() AND table_name = 'metric_binding'
          AND index_name = 'idx_metric_binding_due'
        GROUP BY index_name
        HAVING GROUP_CONCAT(column_name ORDER BY seq_in_index) = 'enabled,deleted,next_collect_at'
    ) THEN
        ALTER TABLE metric_binding
            ADD INDEX idx_metric_binding_due (enabled, deleted, next_collect_at);
    END IF;
END$$

DELIMITER ;

CALL myxos_metric_template_binding_fixes();

DROP PROCEDURE IF EXISTS myxos_metric_template_binding_fixes;
