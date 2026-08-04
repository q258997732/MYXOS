-- 1) 清理 device_group 中重复的名称（保留 id 最小的那条）
DELETE g1 FROM device_group g1
INNER JOIN device_group g2
  ON g1.name = g2.name
  AND g1.parent_id = g2.parent_id
  AND g1.id > g2.id
WHERE g1.deleted = 0 AND g2.deleted = 0;

-- 2) 添加唯一索引，避免再次出现重复默认分组
ALTER TABLE device_group ADD UNIQUE INDEX uk_group_name_parent (name, parent_id);

-- 3) 为发现任务增加详情字段（用于记录逐 IP 结果）
ALTER TABLE discover_task ADD COLUMN detail TEXT NULL COMMENT '逐 IP 发现结果 JSON';
