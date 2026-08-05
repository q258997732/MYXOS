-- 统一 V12 已插入安卓实例指标的目标类型和值类型。
UPDATE metric_catalog
SET target_type = 'ANDROID_INSTANCE',
    value_type = CASE
        WHEN code IN ('MEM_TOTAL_KB', 'MEM_AVAILABLE_KB', 'CPU_USAGE_PERCENT', 'TASK_TOTAL') THEN 'NUMBER'
        WHEN code IN ('ANDROID_VERSION', 'ANDROID_MODEL', 'RECENT_APPS') THEN 'STRING'
        WHEN code = 'ANDROID_STATUS' THEN 'ENUM'
        ELSE value_type
    END
WHERE code IN (
    'ANDROID_VERSION',
    'ANDROID_MODEL',
    'MEM_TOTAL_KB',
    'MEM_AVAILABLE_KB',
    'CPU_USAGE_PERCENT',
    'TASK_TOTAL',
    'RECENT_APPS',
    'ANDROID_STATUS'
)
  AND deleted = 0;
