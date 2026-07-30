-- =====================================================================
-- V2__seed.sql
-- MYXOS 种子数据脚本：默认分组、默认管理员、系统配置
-- 说明：本脚本仅在空库首次迁移时执行一次，请勿重复执行
-- =====================================================================

-- ---------------------------------------------------------------------
-- 默认分组：所有未指定分组的设备归属此分组
-- ---------------------------------------------------------------------
INSERT IGNORE INTO device_group (name, parent_id, remark, who_created, who_modified)
VALUES ('默认分组', 0, '系统自动创建', 'system', 'system');

-- ---------------------------------------------------------------------
-- 默认管理员账号：admin / admin123
-- 密码使用 BCrypt 加密，哈希值由 BCryptPasswordEncoder.encode("admin123") 生成
-- 生产环境部署后请立即修改默认密码
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_user (username, password, nickname, role, status, who_created, who_modified)
VALUES ('admin', '$2a$10$A3PXA5nmMaT2hj../QIYSujqYJ5W3LhPntTZAQMox0KfzrnuOM44K', '管理员', 'ADMIN', 1, 'system', 'system');

-- ---------------------------------------------------------------------
-- 系统配置：采集、保留、清理周期
-- 注意：主服务与采集服务内部通信令牌通过环境变量 MYXOS_INTERNAL_TOKEN 注入，
--       不写入数据库，避免密钥泄露和双服务启动不一致问题。
-- ---------------------------------------------------------------------
INSERT IGNORE INTO sys_config (config_key, config_value, description, who_created, who_modified) VALUES
('collect.interval.sec',   '30',                          '采集间隔（秒）',                       'system', 'system'),
('metric.retention.days',  '7',                           '指标数据保留天数',                     'system', 'system'),
('log.retention.days',     '30',                          '日志保留天数',                         'system', 'system'),
('alarm.retention.days',   '90',                          '告警保留天数',                         'system', 'system'),
('cleanup.cron',           '0 0 3 * * ?',                 '数据清理定时表达式（每天凌晨 3 点）', 'system', 'system');
