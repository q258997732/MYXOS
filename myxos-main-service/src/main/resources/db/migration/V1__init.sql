-- =====================================================================
-- V1__init.sql
-- MYXOS 数据库初始化脚本：创建所有业务表
-- 数据库：MySQL 8.0，字符集 utf8mb4，存储引擎 InnoDB
-- 说明：所有表均包含审计字段（who_created / when_created / who_modified /
--       when_modified）以及逻辑删除标记（deleted）
-- =====================================================================

-- ---------------------------------------------------------------------
-- 系统用户表：存储登录账号、BCrypt 密码、角色与状态
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_user (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    username        VARCHAR(64)  NOT NULL COMMENT '登录用户名',
    password        VARCHAR(128) NOT NULL COMMENT 'BCrypt 加密后的密码',
    nickname        VARCHAR(64)  NULL COMMENT '昵称',
    role            VARCHAR(32)  NOT NULL DEFAULT 'OPERATOR' COMMENT '角色：ADMIN / OPERATOR / VIEWER',
    status          TINYINT      NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 禁用',
    last_login_at   DATETIME     NULL COMMENT '最近登录时间',
    who_created     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '创建人',
    when_created    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    who_modified    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '修改人',
    when_modified   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除：0 未删除，1 已删除',
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统用户表';

-- ---------------------------------------------------------------------
-- 设备分组表：支持树形结构，parent_id=0 表示根分组
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device_group (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    name            VARCHAR(128) NOT NULL COMMENT '分组名称',
    parent_id       BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '父分组 ID，0 表示根分组',
    remark          VARCHAR(255) NULL COMMENT '备注',
    who_created     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '创建人',
    when_created    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    who_modified    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '修改人',
    when_modified   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_parent (parent_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备分组表';

-- ---------------------------------------------------------------------
-- 设备表：托管的 MYTOS 设备清单
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS device (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    name            VARCHAR(128) NOT NULL COMMENT '设备名称',
    ip              VARCHAR(64)  NOT NULL COMMENT '设备 IP',
    port            INT          NOT NULL COMMENT '设备端口',
    host_ip         VARCHAR(64)  NULL COMMENT '宿主机 IP（NAT 模式下使用）',
    instance_index  INT          NULL COMMENT '实例序号',
    mode            VARCHAR(16)  NOT NULL DEFAULT 'BRIDGE' COMMENT '模式：BRIDGE / NAT',
    model           VARCHAR(16)  NULL COMMENT '设备型号',
    group_id        BIGINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '所属分组 ID',
    status          VARCHAR(16)  NOT NULL DEFAULT 'UNKNOWN' COMMENT '状态：ONLINE / OFFLINE / UNKNOWN / DISABLED',
    version         VARCHAR(64)  NULL COMMENT '设备版本号',
    last_seen_at    DATETIME     NULL COMMENT '最近在线时间',
    source          VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL / DISCOVERED',
    remark          VARCHAR(255) NULL COMMENT '备注',
    who_created     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '创建人',
    when_created    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    who_modified    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '修改人',
    when_modified   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_ip_port (ip, port),
    INDEX idx_group (group_id),
    INDEX idx_status (status),
    INDEX idx_host_ip (host_ip)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设备表';

-- ---------------------------------------------------------------------
-- 指标快照表（高容量表）：定期清理，按 collected_at 建立索引
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS metric_snapshot (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    device_id       BIGINT UNSIGNED NOT NULL COMMENT '设备 ID',
    metric_type     VARCHAR(32)  NOT NULL COMMENT '指标类型：CPU / MEM / DISK / NET_RX / NET_TX / TEMP / UPTIME / VERSION / CUSTOM',
    metric_value    VARCHAR(255) NOT NULL COMMENT '指标原始值（字符串形式）',
    metric_num      DECIMAL(18,4) NULL COMMENT '指标数值（可数值化时填充）',
    extra           JSON         NULL COMMENT '扩展信息（JSON）',
    collected_at    DATETIME     NOT NULL COMMENT '采集时间',
    who_created     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '创建人',
    when_created    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    who_modified    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '修改人',
    when_modified   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_device_time (device_id, collected_at),
    INDEX idx_type_time (metric_type, collected_at),
    INDEX idx_collected_at (collected_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='指标快照表';

-- ---------------------------------------------------------------------
-- 阈值规则表：定义触发条件、作用范围与启用状态
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS threshold_rule (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    name              VARCHAR(128) NOT NULL COMMENT '规则名称',
    metric_type       VARCHAR(32)  NOT NULL COMMENT '指标类型',
    compare_op        VARCHAR(8)   NOT NULL COMMENT '比较操作：GT / GTE / LT / LTE / EQ / NE',
    threshold_value   DECIMAL(18,4) NOT NULL COMMENT '阈值',
    trigger_mode      VARCHAR(16)  NOT NULL DEFAULT 'DURATION' COMMENT '触发模式：DURATION（持续时长）/ CONSECUTIVE（连续次数）',
    duration_sec      INT          NOT NULL DEFAULT 0 COMMENT '持续秒数（trigger_mode=DURATION 时有效，0 表示即时触发）',
    consecutive_count INT          NOT NULL DEFAULT 0 COMMENT '连续次数（trigger_mode=CONSECUTIVE 时有效，>=2）',
    scope_type        VARCHAR(16)  NOT NULL DEFAULT 'ALL' COMMENT '作用范围类型：ALL / GROUP / DEVICE',
    scope_id          BIGINT UNSIGNED NULL COMMENT '作用对象 ID（分组 ID 或设备 ID）',
    enabled           TINYINT      NOT NULL DEFAULT 1 COMMENT '是否启用：1 启用，0 禁用',
    who_created       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '创建人',
    when_created      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    who_modified      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '修改人',
    when_modified     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    deleted           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_metric (metric_type, enabled),
    INDEX idx_scope (scope_type, scope_id),
    CONSTRAINT chk_trigger_mode CHECK (trigger_mode IN ('DURATION', 'CONSECUTIVE')),
    CONSTRAINT chk_duration CHECK (trigger_mode <> 'DURATION' OR duration_sec >= 0),
    CONSTRAINT chk_consecutive CHECK (trigger_mode <> 'CONSECUTIVE' OR consecutive_count >= 2)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='阈值规则表';

-- ---------------------------------------------------------------------
-- 阈值触发的动作表：一个规则可配置多个动作
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS threshold_action (
    id                BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    rule_id           BIGINT UNSIGNED NOT NULL COMMENT '所属规则 ID',
    action_type       VARCHAR(32)  NOT NULL COMMENT '动作类型：LOG / OPERATION',
    log_level         VARCHAR(16)  NULL COMMENT '日志级别：DEBUG / INFO / WARN / ERROR',
    operation_code    VARCHAR(64)  NULL COMMENT '操作类型代码',
    operation_params  JSON         NULL COMMENT '操作参数（JSON）',
    sort              INT          NOT NULL DEFAULT 0 COMMENT '同一规则内动作执行顺序',
    who_created       VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '创建人',
    when_created      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    who_modified      VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '修改人',
    when_modified     DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    deleted           TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_rule (rule_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='阈值动作表';

-- ---------------------------------------------------------------------
-- 告警事件表：记录每次阈值触发与恢复
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS alarm_event (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    rule_id         BIGINT UNSIGNED NOT NULL COMMENT '触发规则 ID',
    device_id       BIGINT UNSIGNED NOT NULL COMMENT '设备 ID',
    metric_type     VARCHAR(32)  NOT NULL COMMENT '指标类型',
    metric_value    VARCHAR(255) NOT NULL COMMENT '触发时的指标值',
    threshold_value VARCHAR(64)  NOT NULL COMMENT '触发时的阈值',
    fired_at        DATETIME     NOT NULL COMMENT '触发时间',
    resolved_at     DATETIME     NULL COMMENT '恢复时间',
    status          VARCHAR(16)  NOT NULL DEFAULT 'FIRING' COMMENT '状态：FIRING / RESOLVED',
    who_created     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '创建人',
    when_created    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    who_modified    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '修改人',
    when_modified   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_device_time (device_id, fired_at),
    INDEX idx_rule_status (rule_id, status),
    INDEX idx_status (status),
    INDEX idx_fired_at (fired_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='告警事件表';

-- ---------------------------------------------------------------------
-- 操作任务队列表：手动与自动操作共用，采集服务消费执行
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS op_task (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    device_id       BIGINT UNSIGNED NOT NULL COMMENT '设备 ID',
    operation_code  VARCHAR(64)  NOT NULL COMMENT '操作类型代码',
    params          JSON         NULL COMMENT '操作参数（JSON）',
    source          VARCHAR(16)  NOT NULL DEFAULT 'MANUAL' COMMENT '来源：MANUAL / AUTO',
    source_ref_id   BIGINT UNSIGNED NULL COMMENT '来源引用 ID（如告警 ID）',
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING / RUNNING / SUCCESS / FAILED / TIMEOUT',
    retry_count     INT          NOT NULL DEFAULT 0 COMMENT '已重试次数',
    max_retry       INT          NOT NULL DEFAULT 3 COMMENT '最大重试次数',
    scheduled_at    DATETIME     NOT NULL COMMENT '计划执行时间',
    started_at      DATETIME     NULL COMMENT '开始执行时间',
    finished_at     DATETIME     NULL COMMENT '完成时间',
    result_msg      VARCHAR(1024) NULL COMMENT '执行结果消息',
    who_created     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '创建人',
    when_created    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    who_modified    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '修改人',
    when_modified   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_status_sched (status, scheduled_at),
    INDEX idx_status_started (status, started_at),
    INDEX idx_device (device_id),
    INDEX idx_source_ref (source, source_ref_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作任务表';

-- ---------------------------------------------------------------------
-- 动作与系统日志表：记录阈值动作执行日志与系统事件
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS action_log (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    task_id         BIGINT UNSIGNED NULL COMMENT '关联任务 ID（可空）',
    alarm_id        BIGINT UNSIGNED NULL COMMENT '关联告警 ID（可空）',
    device_id       BIGINT UNSIGNED NOT NULL COMMENT '设备 ID',
    action_type     VARCHAR(32)  NOT NULL COMMENT '动作类型：LOG / OPERATION / SYSTEM',
    log_level       VARCHAR(16)  NULL COMMENT '日志级别：DEBUG / INFO / WARN / ERROR',
    message         TEXT         NULL COMMENT '日志内容',
    created_at      DATETIME     NOT NULL COMMENT '日志产生时间',
    who_created     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '创建人',
    when_created    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    who_modified    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '修改人',
    when_modified   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_device_time (device_id, created_at),
    INDEX idx_task (task_id),
    INDEX idx_alarm (alarm_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='动作日志表';

-- ---------------------------------------------------------------------
-- 网段发现任务表：CIDR 扫描任务记录
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS discover_task (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    cidr            VARCHAR(64)  NOT NULL COMMENT 'CIDR 网段，如 192.168.30.0/24',
    port_from       INT          NOT NULL COMMENT '起始端口',
    port_to         INT          NOT NULL COMMENT '结束端口',
    status          VARCHAR(16)  NOT NULL DEFAULT 'PENDING' COMMENT '状态：PENDING / RUNNING / SUCCESS / FAILED',
    found_count     INT          NOT NULL DEFAULT 0 COMMENT '发现的设备数量',
    started_at      DATETIME     NULL COMMENT '开始时间',
    finished_at     DATETIME     NULL COMMENT '完成时间',
    message         VARCHAR(512) NULL COMMENT '任务消息',
    who_created     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '创建人',
    when_created    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    who_modified    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '修改人',
    when_modified   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    INDEX idx_status (status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='网段发现任务表';

-- ---------------------------------------------------------------------
-- 系统配置表：采集间隔、保留天数、清理 Cron、内部令牌等
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS sys_config (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    config_key      VARCHAR(128) NOT NULL COMMENT '配置键',
    config_value    VARCHAR(1024) NOT NULL COMMENT '配置值',
    description     VARCHAR(255) NULL COMMENT '配置描述',
    who_created     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '创建人',
    when_created    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    who_modified    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '修改人',
    when_modified   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_config_key (config_key)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='系统配置表';

-- ---------------------------------------------------------------------
-- JWT 令牌吊销记录表：用于登出后吊销令牌
-- ---------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS login_token (
    id              BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY COMMENT '主键 ID',
    user_id         BIGINT UNSIGNED NOT NULL COMMENT '用户 ID',
    token_id        VARCHAR(64)  NOT NULL COMMENT '令牌唯一标识（JWT ID）',
    issued_at       DATETIME     NOT NULL COMMENT '签发时间',
    expires_at      DATETIME     NOT NULL COMMENT '过期时间',
    revoked         TINYINT      NOT NULL DEFAULT 0 COMMENT '是否已吊销：0 未吊销，1 已吊销',
    who_created     VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '创建人',
    when_created    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    who_modified    VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '修改人',
    when_modified   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '修改时间',
    deleted         TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    UNIQUE KEY uk_token_id (token_id),
    INDEX idx_user (user_id),
    INDEX idx_expires_at (expires_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='JWT 令牌记录表';
