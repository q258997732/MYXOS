-- 阈值规则与告警扩展
-- 1. threshold_value 允许 NULL：STRING/NONE 条件类型无数值阈值，MyBatis-Plus 插入时省略该列会导致
--    "Field 'threshold_value' doesn't have a default value" 错误
-- 2. threshold_rule 新增 scope_android_name：ANDROID_STATUS 指标可按实例名精确监控，
--    为空表示作用于范围内全部安卓实例
-- 3. alarm_event 新增 android_name：ANDROID_STATUS 触发的告警按"规则+设备+实例"维度去重，
--    避免同主机上一个实例恢复健康实例的告警被误解除
ALTER TABLE threshold_rule
    MODIFY COLUMN threshold_value DECIMAL(18,4) NULL COMMENT '阈值',
    ADD COLUMN scope_android_name VARCHAR(128) NULL COMMENT '安卓实例名（仅 ANDROID_STATUS 指标生效，空表示全部实例）' AFTER scope_ids;

ALTER TABLE alarm_event
    ADD COLUMN android_name VARCHAR(128) NULL COMMENT '安卓实例名（ANDROID_STATUS 触发时记录）' AFTER device_id,
    MODIFY COLUMN threshold_value VARCHAR(255) NOT NULL COMMENT '触发时的阈值';
