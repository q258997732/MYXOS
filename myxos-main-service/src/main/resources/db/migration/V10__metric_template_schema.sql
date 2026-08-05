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
    android_name    VARCHAR(128) NOT NULL DEFAULT '',
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

ALTER TABLE metric_snapshot
    ADD COLUMN IF NOT EXISTS metric_code VARCHAR(64) NULL,
    ADD COLUMN IF NOT EXISTS target_type VARCHAR(32) NULL,
    ADD COLUMN IF NOT EXISTS android_name VARCHAR(128) NOT NULL DEFAULT '';

ALTER TABLE threshold_rule
    ADD COLUMN IF NOT EXISTS metric_code VARCHAR(64) NULL;

UPDATE metric_snapshot SET metric_code = metric_type
WHERE metric_code IS NULL;

UPDATE threshold_rule SET metric_code = metric_type
WHERE metric_code IS NULL;
