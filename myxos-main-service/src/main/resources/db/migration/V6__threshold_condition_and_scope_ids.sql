-- 阈值规则扩展：条件类型、字符判断目标值、多设备作用范围
-- 1. condition_type 区分 数值判断(NUMERIC) / 字符判断(STRING) / 状态触发(NONE)
-- 2. threshold_text 承载字符判断的目标值（如 ANDROID_STATUS 等于 STOPPED）
-- 3. scope_ids 以逗号分隔的设备 ID 列表支持"指定设备"多选，优先于 scope_id
ALTER TABLE threshold_rule
    ADD COLUMN condition_type VARCHAR(16) NOT NULL DEFAULT 'NUMERIC' COMMENT '条件类型：NUMERIC/STRING/NONE' AFTER metric_type,
    ADD COLUMN threshold_text VARCHAR(255) NULL COMMENT '字符判断目标值（condition_type=STRING 时有效）' AFTER threshold_value,
    ADD COLUMN scope_ids VARCHAR(2048) NULL COMMENT '设备 ID 列表（逗号分隔），scope_type=DEVICE 时优先' AFTER scope_id;
