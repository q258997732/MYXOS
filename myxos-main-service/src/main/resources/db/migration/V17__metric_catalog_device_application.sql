-- 指标目录成为默认配置来源；既有设备绑定保持不变。
ALTER TABLE metric_catalog
    ADD COLUMN default_interval_sec INT NOT NULL DEFAULT 60 AFTER threshold_enabled;

UPDATE metric_catalog
SET default_interval_sec = 60
WHERE default_interval_sec IS NULL;
