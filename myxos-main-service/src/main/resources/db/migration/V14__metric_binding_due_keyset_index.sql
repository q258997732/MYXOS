-- 为绑定到期时间的复合键集分页提供覆盖索引。
UPDATE metric_binding
SET next_collect_at = CURRENT_TIMESTAMP
WHERE enabled = 1 AND deleted = 0 AND next_collect_at IS NULL;

ALTER TABLE metric_binding
    DROP INDEX idx_metric_binding_due,
    ADD INDEX idx_metric_binding_due_keyset (enabled, deleted, next_collect_at, id);
