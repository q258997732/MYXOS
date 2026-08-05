-- 修复已执行 V12 中安卓实例状态指标不可配置阈值的问题，并扩展枚举阈值存储容量。
UPDATE metric_catalog
SET threshold_enabled = 1
WHERE code = 'ANDROID_STATUS'
  AND deleted = 0;

ALTER TABLE threshold_rule MODIFY COLUMN threshold_text TEXT NULL;
