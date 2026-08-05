-- scope_android_name 从 VARCHAR(128) 扩容到 VARCHAR(2048)：
-- 实例名称改为下拉多选后以逗号分隔存储多个实例名，128 字符不足以容纳多实例场景
ALTER TABLE threshold_rule
    MODIFY COLUMN scope_android_name VARCHAR(2048) NULL COMMENT '安卓实例名（逗号分隔多实例，仅 ANDROID_STATUS 指标生效，空表示全部实例）';
