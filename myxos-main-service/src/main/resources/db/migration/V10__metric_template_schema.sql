-- 指标模板、绑定与历史指标编码扩展。

CREATE TABLE IF NOT EXISTS metric_catalog (
                                              id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                              code              VARCHAR(64)  NOT NULL,
                                              name              VARCHAR(128) NOT NULL,
                                              target_type       VARCHAR(32)  NOT NULL,
                                              value_type        VARCHAR(16)  NOT NULL,
                                              category          VARCHAR(32)  NOT NULL,
                                              unit              VARCHAR(32)  NULL,
                                              command_key       VARCHAR(128) NULL,
                                              threshold_enabled TINYINT      NOT NULL DEFAULT 1,
                                              who_created       VARCHAR(64)  NOT NULL DEFAULT '',
                                              when_created      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              who_modified      VARCHAR(64)  NOT NULL DEFAULT '',
                                              when_modified     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                              deleted           TINYINT      NOT NULL DEFAULT 0,
                                              UNIQUE KEY uk_metric_catalog_code (code, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS metric_template (
                                               id            BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                               name          VARCHAR(128) NOT NULL,
                                               target_type   VARCHAR(32)  NOT NULL,
                                               enabled       TINYINT      NOT NULL DEFAULT 1,
                                               who_created   VARCHAR(64)  NOT NULL DEFAULT '',
                                               when_created  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                               who_modified  VARCHAR(64)  NOT NULL DEFAULT '',
                                               when_modified DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                               deleted       TINYINT      NOT NULL DEFAULT 0,
                                               UNIQUE KEY uk_metric_template_name (name, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS metric_template_item (
                                                    id                   BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                                    template_id          BIGINT UNSIGNED NOT NULL,
                                                    metric_catalog_id    BIGINT UNSIGNED NOT NULL,
                                                    enabled              TINYINT         NOT NULL DEFAULT 1,
                                                    default_interval_sec INT             NOT NULL DEFAULT 60,
                                                    enum_options         JSON            NULL,
                                                    who_created          VARCHAR(64)     NOT NULL DEFAULT '',
                                                    when_created         DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                                    who_modified         VARCHAR(64)     NOT NULL DEFAULT '',
                                                    when_modified        DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                                    deleted              TINYINT         NOT NULL DEFAULT 0,
                                                    UNIQUE KEY uk_metric_template_item (template_id, metric_catalog_id, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

CREATE TABLE IF NOT EXISTS metric_binding (
                                              id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
                                              device_id         BIGINT UNSIGNED NOT NULL,
                                              android_name      VARCHAR(128) NOT NULL DEFAULT '',
                                              target_type       VARCHAR(32)  NOT NULL,
                                              metric_code       VARCHAR(64)  NOT NULL,
                                              enabled           TINYINT      NOT NULL DEFAULT 1,
                                              interval_sec      INT          NOT NULL DEFAULT 60,
                                              last_collected_at DATETIME     NULL,
                                              next_collect_at   DATETIME     NULL,
                                              who_created       VARCHAR(64)  NOT NULL DEFAULT '',
                                              when_created      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                              who_modified      VARCHAR(64)  NOT NULL DEFAULT '',
                                              when_modified     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
                                              deleted           TINYINT      NOT NULL DEFAULT 0,
                                              UNIQUE KEY uk_metric_binding_target (device_id, android_name, metric_code, deleted),
                                              INDEX idx_metric_binding_due (enabled, next_collect_at, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- 为 metric_snapshot 表添加列（兼容低版本 MySQL）
-- ============================================

-- 添加 metric_code 列
SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'metric_snapshot'
      AND COLUMN_NAME = 'metric_code'
);

SET @sql_add_metric_code = IF(@col_exists = 0,
                              'ALTER TABLE metric_snapshot ADD COLUMN metric_code VARCHAR(64) NULL',
                              'SELECT 1'
                           );

PREPARE stmt FROM @sql_add_metric_code;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 target_type 列
SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'metric_snapshot'
      AND COLUMN_NAME = 'target_type'
);

SET @sql_add_target_type = IF(@col_exists = 0,
                              'ALTER TABLE metric_snapshot ADD COLUMN target_type VARCHAR(32) NULL',
                              'SELECT 1'
                           );

PREPARE stmt FROM @sql_add_target_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- 添加 android_name 列
SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'metric_snapshot'
      AND COLUMN_NAME = 'android_name'
);

SET @sql_add_android_name = IF(@col_exists = 0,
                               'ALTER TABLE metric_snapshot ADD COLUMN android_name VARCHAR(128) NOT NULL DEFAULT ''''',
                               'SELECT 1'
                            );

PREPARE stmt FROM @sql_add_android_name;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- 为 threshold_rule 表添加列
-- ============================================

SET @col_exists = (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'threshold_rule'
      AND COLUMN_NAME = 'metric_code'
);

SET @sql_add_threshold = IF(@col_exists = 0,
                            'ALTER TABLE threshold_rule ADD COLUMN metric_code VARCHAR(64) NULL',
                            'SELECT 1'
                         );

PREPARE stmt FROM @sql_add_threshold;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================
-- 更新数据
-- ============================================

-- 先确保列存在，然后更新
UPDATE metric_snapshot SET metric_code = metric_type
WHERE metric_code IS NULL;

UPDATE threshold_rule SET metric_code = metric_type
WHERE metric_code IS NULL;