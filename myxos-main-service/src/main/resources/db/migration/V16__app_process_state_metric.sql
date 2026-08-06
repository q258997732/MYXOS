-- 应用进程状态按“安卓实例 + 应用包名”独立采集、展示与判定。
ALTER TABLE metric_binding
    ADD COLUMN app_package VARCHAR(255) NOT NULL DEFAULT '' AFTER metric_code;

ALTER TABLE metric_binding
    DROP INDEX uk_metric_binding_target,
    ADD UNIQUE KEY uk_metric_binding_target (device_id, android_name, metric_code, app_package, deleted);

ALTER TABLE metric_snapshot
    ADD COLUMN app_package VARCHAR(255) NOT NULL DEFAULT '' AFTER android_name,
    ADD INDEX idx_metric_snapshot_app_history (device_id, metric_code, target_type, android_name, app_package, collected_at);

ALTER TABLE threshold_rule
    ADD COLUMN scope_app_package VARCHAR(255) NULL AFTER scope_android_name;

INSERT INTO metric_catalog (code, name, target_type, value_type, category, unit, command_key, threshold_enabled)
SELECT 'APP_PROCESS_STATE', '应用进程状态', 'ANDROID_INSTANCE', 'ENUM', '应用', NULL, 'APP_PROCESS_STATE', 1
WHERE NOT EXISTS (
    SELECT 1 FROM metric_catalog WHERE code = 'APP_PROCESS_STATE' AND deleted = 0
);
