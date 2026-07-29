# MYTOS 自监控与运维系统详细设计

> 本文档在 `2026-07-29-mytos-monitor.md` 实施计划基础上，进一步细化页面、API、核心流程与状态机，作为编码实现前的最终设计稿。

---

## 一、页面与前端设计

### 1.1 技术栈与布局

- **技术栈**：Vue 3 + Element Plus + Pinia + Vue Router 4 + Axios + ECharts
- **布局**：左侧固定导航菜单 + 顶部用户信息栏 + 右侧内容区
- **主题**：Element Plus 默认主题，设备状态通过颜色标签直观展示
- **响应式**：最小支持 1366×768，表格默认分页 20 条

### 1.2 路由与页面清单

| 路径 | 页面名称 | 权限 | 说明 |
|------|----------|------|------|
| `/login` | 登录页 | 公开 | 用户名、密码、记住登录状态 |
| `/dashboard` | 仪表盘 | 登录 | 设备总数、在线/离线数、今日告警、最近任务 |
| `/devices` | 设备列表 | 登录 | 汇总设备状态，支持下钻 |
| `/devices/:id` | 设备详情 | 登录 | 详情、指标图表、手动操作、最近告警/日志 |
| `/thresholds` | 阈值规则 | 登录 | 规则列表、启用/禁用、编辑入口 |
| `/thresholds/edit` | 新增规则 | ADMIN/OPERATOR | 表单录入阈值与动作 |
| `/thresholds/edit/:id` | 编辑规则 | ADMIN/OPERATOR | 回填已有规则与动作 |
| `/hosting` | 设备托管 | ADMIN/OPERATOR | 手动添加 IP、CIDR 网段发现、发现任务 |
| `/alarms` | 告警列表 | 登录 | 告警查询、手动恢复 |
| `/logs` | 日志查询 | 登录 | 动作日志与系统日志筛选 |
| `/op-tasks` | 任务队列 | 登录 | 手动/自动操作任务及重试 |
| `/settings` | 系统配置 | ADMIN | 采集间隔、保留天数、清理周期 |
| `/users` | 用户管理 | ADMIN | 仅 ADMIN 可见 |

### 1.3 各页面字段与交互

#### 1.3.1 登录页（`/login`）

| 字段 | 类型 | 说明 |
|------|------|------|
| 用户名 | Input | 必填，长度 2-64 |
| 密码 | Password Input | 必填，长度 6-64 |
| 登录按钮 | Button | 校验通过后调用 `/api/auth/login` |
| 错误提示 | Message | 登录失败显示服务端返回 msg |

交互：登录成功后写入 `localStorage.token`，axios 默认 header 带上 `Authorization: Bearer token`。

#### 1.3.2 仪表盘（`/dashboard`）

| 区域 | 内容 |
|------|------|
| 顶部统计卡片 | 设备总数、在线数、离线数、今日告警数 |
| 设备状态分布 | ECharts 饼图（在线/离线/未知/禁用） |
| 最近告警 | 最近 5 条告警，点击跳转告警列表 |
| 最近操作任务 | 最近 5 条任务状态，点击跳转任务队列 |
| 快捷入口 | 设备列表、阈值规则、设备托管 |

#### 1.3.3 设备列表（`/devices`）

筛选区：
- 分组选择（`device_group` 下拉）
- 状态下拉（ONLINE / OFFLINE / UNKNOWN / DISABLED）
- 关键字搜索（名称/IP）
- 重置 / 查询按钮

表格列：
| 列名 | 说明 |
|------|------|
| 设备名称 | `name`，点击下钻到详情 |
| IP:Port | `ip` + `port` |
| 模式 | `mode`（桥接/NAT） |
| 分组 | `group_name` |
| 状态 | 彩色 Tag（绿=在线、红=离线、灰=未知） |
| 版本 | `version` |
| 最后在线 | `last_seen_at` |
| 告警数 | 当前 FIRING 告警数量徽章 |
| 操作 | 查看、编辑、删除、立即采集 |

分页：底部 Element Plus Pagination，默认 20 条/页。

#### 1.3.4 设备详情（`/devices/:id`）

Tab 页签：
1. **基本信息**：名称、IP、端口、模式、型号、分组、状态、版本、来源、备注
2. **实时指标**：最近 1 小时指标曲线（CPU/MEM/NET/温度等，按 `metric_type` 区分）
3. **手动操作**：操作按钮网格（重启、ADB 开关、保活开关、设置剪贴板、设置代理、截图等）
4. **最近告警**：该设备最近 20 条告警
5. **最近日志**：该设备最近 50 条动作日志
6. **任务记录**：该设备最近 20 条操作任务

手动操作面板：
- 每个操作按钮点击后弹出确认对话框（如需要参数则弹出表单）
- 提交后调用 `POST /api/devices/{id}/ops`，前端提示"任务已提交"
- 任务结果通过"任务记录"Tab 异步刷新查看

#### 1.3.5 阈值规则列表（`/thresholds`）

表格列：
| 列名 | 说明 |
|------|------|
| 规则名称 | `name` |
| 指标类型 | `metric_type` |
| 条件 | `compare_op` + `threshold_value` |
| 触发条件 | 持续时间（秒）或连续 N 次采集 |
| 作用范围 | 全部/分组/设备 + 作用对象名 |
| 启用状态 | Switch 开关 |
| 动作数 | 关联动作数量 |
| 操作 | 编辑、删除、启用/禁用 |

#### 1.3.6 阈值规则编辑（`/thresholds/edit/:id?`）

表单字段：
| 字段 | 类型 | 说明 |
|------|------|------|
| 规则名称 | Input | 必填 |
| 指标类型 | Select | CPU / MEM / DISK / NET_RX / NET_TX / TEMP / CUSTOM |
| 比较操作 | Select | > / >= / < / <= / = / != |
| 阈值 | Number Input | 必填，保留 4 位小数 |
| 触发模式 | Select | DURATION（持续时间）/ CONSECUTIVE（连续次数） |
| 持续时间 | Number Input | 单位秒，触发模式=DURATION 时有效，0 表示即时触发 |
| 连续次数 | Number Input | 触发模式=CONSECUTIVE 时有效，≥2 |
| 作用范围类型 | Select | ALL / GROUP / DEVICE |
| 作用对象 | Select | 根据范围类型加载分组或设备 |

**触发模式说明**：
- `DURATION`：查询最近 `duration_sec` 秒内所有采样是否持续 breach
- `CONSECUTIVE`：查询最近 `consecutive_count` 次采样是否全部 breach

动作配置区（可添加多条）：
| 字段 | 说明 |
|------|------|
| 动作类型 | 记录日志 / 执行操作 |
| 日志级别 | DEBUG / INFO / WARN / ERROR（仅日志动作） |
| 操作类型 | REBOOT / ADB_ON / ADB_OFF / KEEPALIVE_ON / KEEPALIVE_OFF 等（仅操作动作） |
| 操作参数 | JSON 字符串，根据操作类型动态提示（可选） |
| 排序 | 同一规则内动作执行顺序 |

动作可拖动排序或上下移动，至少配置一条动作。所有操作类型必须在后端 `OperationCode` 枚举和 `MytosClient` 中显式实现，前端不开放自定义 HTTP 调用。

#### 1.3.7 设备托管（`/hosting`）

左右分栏：
- 左侧：手动添加表单
  - IP / 端口 / 模式 / 分组 / 名称（可选，留空自动从设备 info 读取）
  - 保存按钮
- 右侧：网段发现
  - CIDR 输入（如 `192.168.30.0/24`）
  - 起始端口 / 结束端口
  - 开始扫描按钮
  - 发现任务列表（状态、CIDR、端口范围、发现数量、时间）

#### 1.3.8 告警列表（`/alarms`）

筛选：状态、设备、时间范围
表格列：触发时间、设备、规则、指标值、阈值、状态、操作（恢复）

#### 1.3.9 日志查询（`/logs`）

筛选：设备、动作类型、日志级别、时间范围
表格列：时间、设备、动作类型、日志级别、消息

#### 1.3.10 任务队列（`/op-tasks`）

表格列：任务 ID、设备、操作类型、来源（手动/自动）、状态、计划时间、完成时间、结果、操作（重试）

#### 1.3.11 系统配置（`/settings`）

| 配置项 | 键名 | 默认值 | 说明 |
|--------|------|--------|------|
| 采集间隔秒 | `collect.interval.sec` | 30 | 采集服务调度周期 |
| 指标保留天数 | `metric.retention.days` | 7 | metric_snapshot 保留天数 |
| 日志保留天数 | `log.retention.days` | 30 | action_log 保留天数 |
| 告警保留天数 | `alarm.retention.days` | 90 | alarm_event 保留天数 |
| 清理定时 | `cleanup.cron` | `0 0 3 * * ?` | 数据清理 Cron |
| 内部令牌 | `internal.token` | 随机生成 | 主/采服务通信令牌 |

---

## 二、REST API 接口设计

### 2.1 统一响应格式

```json
{
  "code": 200,
  "msg": "ok",
  "data": {}
}
```

分页响应：
```json
{
  "code": 200,
  "msg": "ok",
  "data": {
    "total": 100,
    "pages": 5,
    "current": 1,
    "size": 20,
    "records": []
  }
}
```

### 2.2 认证模块（`/api/auth`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 登录，返回 JWT |
| POST | `/api/auth/logout` | 登出，吊销当前令牌 |
| GET | `/api/auth/me` | 获取当前登录用户信息 |

### 2.3 设备分组（`/api/device-groups`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/device-groups` | 分组树形列表 |
| POST | `/api/device-groups` | 新增分组 |
| PUT | `/api/device-groups/{id}` | 修改分组 |
| DELETE | `/api/device-groups/{id}` | 删除分组 |

### 2.4 设备管理（`/api/devices`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/devices` | 分页列表（groupId / status / keyword / page / size） |
| POST | `/api/devices` | 手动创建设备 |
| GET | `/api/devices/{id}` | 设备详情 |
| PUT | `/api/devices/{id}` | 修改设备 |
| DELETE | `/api/devices/{id}` | 删除设备 |
| POST | `/api/devices/{id}/collect` | 触发一次立即采集 |
| POST | `/api/devices/{id}/ops` | 下发手动操作任务 |
| GET | `/api/devices/{id}/metrics` | 查询该设备历史指标（metricType / start / end） |
| GET | `/api/devices/{id}/alarms` | 查询该设备最近告警 |
| GET | `/api/devices/{id}/logs` | 查询该设备最近日志 |
| GET | `/api/devices/{id}/tasks` | 查询该设备最近任务 |

### 2.5 阈值规则（`/api/thresholds`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/thresholds` | 分页/列表 |
| POST | `/api/thresholds` | 创建规则（含 actions） |
| GET | `/api/thresholds/{id}` | 规则详情（含 actions） |
| PUT | `/api/thresholds/{id}` | 更新规则（含 actions） |
| DELETE | `/api/thresholds/{id}` | 删除规则 |
| POST | `/api/thresholds/{id}/toggle` | 启用/禁用切换 |

### 2.6 告警（`/api/alarms`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/alarms` | 分页列表（status / deviceId / start / end / page / size） |
| POST | `/api/alarms/{id}/resolve` | 手动恢复告警 |

### 2.7 日志（`/api/logs`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/logs` | 分页列表（deviceId / actionType / logLevel / start / end / page / size） |

### 2.8 操作任务（`/api/op-tasks`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/op-tasks` | 分页列表（deviceId / status / source / page / size） |
| POST | `/api/op-tasks/{id}/retry` | 重试失败任务 |

### 2.9 设备发现（`/api/discover`）

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/discover/scan` | 提交 CIDR 扫描任务 |
| GET | `/api/discover/tasks` | 扫描任务列表 |
| GET | `/api/discover/tasks/{id}` | 任务详情 |

### 2.10 系统配置（`/api/sys-config`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/sys-config` | 所有配置列表 |
| PUT | `/api/sys-config/{key}` | 更新单个配置 |

### 2.11 用户管理（`/api/users`）

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | `/api/users` | 用户列表 |
| POST | `/api/users` | 新增用户 |
| PUT | `/api/users/{id}` | 修改用户 |
| POST | `/api/users/{id}/reset-password` | 重置密码为默认密码 |
| POST | `/api/users/{id}/toggle-status` | 启用/禁用用户 |
| POST | `/api/users/me/password` | 当前登录用户修改自身密码（oldPassword / newPassword） |

### 2.12 WebSocket 实时告警

| 端点 | 说明 |
|------|------|
| `ws://host:8080/ws/alarm` | STOMP over SockJS/WebSocket 接入点 |
| 订阅 `/topic/alarms` | 接收所有新增 FIRING 告警 |
| 订阅 `/topic/alarms/{deviceId}` | 接收指定设备的新增 FIRING 告警 |

推送消息格式：
```json
{
  "type": "ALARM_FIRED",
  "alarmId": 1001,
  "deviceId": 12,
  "deviceName": "测试设备",
  "ruleId": 5,
  "ruleName": "CPU 过高",
  "metricType": "CPU",
  "metricValue": "85.5",
  "thresholdValue": "80",
  "firedAt": "2026-07-29T14:30:00"
}
```

历史告警查询仍通过 `GET /api/alarms` 分页获取。

## 三、核心流程设计

### 3.1 采集流程

```
定时触发（MetricCollectJob.collect）
    │
    ▼
查询所有未禁用的 device 列表
    │
    ▼
对每台设备尝试 putIfAbsent 进入 inFlight 集合（防止同一周期重复执行）
    │
    ▼
提交到 metricCollectExecutor 线程池
    │
    ▼
MetricCollector.run
    │
    ├── 调用 /info 与 /queryversion
    │
    ├── 更新 device.status / version / last_seen_at
    │
    └── 将指标写入 metric_snapshot（批量保存）
                │
                ▼
        ThresholdEvaluator.evaluate（异步或同步判定）
```

关键约束：
- 线程池核心 8、最大 32、队列 500，拒绝策略 CallerRunsPolicy
- 单台设备同一采集周期只执行一次
- 采集超时 10 秒，连接超时 3 秒

### 3.2 阈值判定流程

```
收到一组 MetricSnapshot
    │
    ▼
按 metric_type 从 RuleCache 获取有效规则
    │
    ▼
逐条匹配规则
    ├── 检查作用范围（ALL / GROUP / DEVICE）
    │
    ├── 检查触发模式
    │   ├── DURATION：查询最近 duration_sec 内是否持续 breach
    │   └── CONSECUTIVE：查询最近 consecutive_count 次采样是否全部 breach
    │
    ├── 比较运算（GT/GTE/LT/LTE/EQ/NE）
    │
    ├── breach = true
    │   ├── 生成或更新 AlarmEvent（FIRING）
    │   ├── 通过 WebSocket 推送实时告警到前端
    │   └── 遍历 ThresholdAction 执行
    │       ├── LOG：写入 action_log
    │       └── OPERATION：写入 op_task（source=AUTO）
    │
    └── breach = false
        └── 若存在 FIRING 告警，则标记为 RESOLVED
```

触发模式判定实现：
- `DURATION`：查询 `metric_snapshot` 中该设备该指标最近 `duration_sec` 秒的记录，全部 breach 则触发
- `CONSECUTIVE`：查询 `metric_snapshot` 中该设备该指标按时间倒序最近 `consecutive_count` 条记录，全部 breach 则触发
- 任一记录未 breach，则不触发

### 3.3 动作执行流程

```
OpTaskExecuteJob（每 2 秒调度）
    │
    ▼
查询 status=PENDING 且 scheduled_at<=now 的任务，LIMIT 50
    │
    ▼
对每条任务执行 claimPending（CAS 更新为 RUNNING）
    │
    ▼
提交到 opTaskExecutor 线程池
    │
    ▼
OpTaskRunner 根据 operation_code 调用 MytosClient 对应方法
    │
    ▼
更新任务状态
    ├── 成功：SUCCESS + result_msg
    └── 失败：retry_count < max_retry ? PENDING（延迟重试） : FAILED
```

关键约束：
- 线程池核心 4、最大 16、队列 200
- 手动任务与自动任务共用同一队列和线程池
- 重试延迟按指数退避：10s × retry_count

### 3.4 设备发现流程

```
用户在 UI 提交 CIDR + 端口范围
    │
    ▼
主服务写入 discover_task（status=PENDING）
    │
    ▼
采集服务 DeviceDiscoveryJob 每 5 秒轮询
    │
    ▼
扫描该 CIDR 下每个 IP 与端口
    │
    ▼
调用 /info 探测
    │
    ▼
成功响应则插入 device（source=DISCOVERED，避免重复 IP:Port）
    │
    ▼
更新 discover_task 状态、发现数量、完成时间
```

扫描性能约束：
- 扫描线程池固定 16 线程
- 仅支持 `/22` 及以上网段（最大 1022 个 IP）
- 端口范围建议不超过 10 个端口

### 3.5 数据清理流程

```
DataCleanupJob（按 cleanup.cron 调度，默认每天 3 点）
    │
    ▼
读取 retention 配置
    │
    ▼
计算截止时间
    │
    ▼
分批删除（每次 LIMIT 5000，循环直到无记录）
    ├── metric_snapshot.collected_at < deadline
    ├── action_log.created_at < deadline
    └── alarm_event.fired_at < deadline
```

---

## 四、状态机

### 4.1 设备状态（`device.status`）

```
        UNKNOWN
       /    |    \
   ONLINE  OFFLINE  DISABLED
       \    |    /
        （可互相转换）
```

- `UNKNOWN`：初始状态或从未成功采集
- `ONLINE`：最近一次采集成功
- `OFFLINE`：最近一次采集失败或超时
- `DISABLED`：人工禁用，不参与采集

### 4.2 告警状态（`alarm_event.status`）

```
FIRING ──resolve──▶ RESOLVED
```

- `FIRING`：阈值条件持续满足
- `RESOLVED`：阈值条件已恢复正常或人工恢复

### 4.3 操作任务状态（`op_task.status`）

```
PENDING ──claim──▶ RUNNING ──success──▶ SUCCESS
   │                    │
   │                    └──failure──▶ PENDING（重试）──▶ FAILED
   │
   └──timeout/重试耗尽────────────────▶ FAILED
```

---

## 五、关键设计决策

### 5.1 线程池隔离

| 线程池 | 用途 | 配置 |
|--------|------|------|
| `metricCollectExecutor` | 设备指标采集 | core=8, max=32, queue=500 |
| `opTaskExecutor` | 操作任务执行 | core=4, max=16, queue=200 |
| 发现扫描线程池 | 网段扫描 | 固定 16 线程 |

目的：避免大量采集任务阻塞手动/自动操作，避免扫描任务耗尽采集线程。

### 5.2 共享数据库通信

主服务与采集服务不直接调用，而是通过共享 MySQL 表协作：
- `device`：主服务写入，采集服务读取和更新状态
- `threshold_rule` / `threshold_action`：主服务写入，采集服务 RuleCache 加载
- `op_task`：主服务和采集服务均可写入，采集服务执行
- `discover_task`：主服务写入，采集服务执行
- `sys_config`：主服务维护，采集服务读取

优势：无需额外消息队列，降低部署复杂度。
注意：采集服务需定期刷新 RuleCache 和 SysConfig 缓存。

### 5.3 幂等性

- 设备发现：IP + Port 唯一索引，重复发现自动跳过
- 采集任务：inFlight 集合保证同一周期内同一设备只执行一次
- 操作任务：claimPending CAS 更新保证同一任务只被一个线程领取

### 5.4 安全性

- 前端静态资源和 API 共用 8080 端口，API 前缀 `/api`
- JWT 在 Header `Authorization: Bearer token` 中传递
- 除登录、静态资源、首页外，其余请求均需要 JWT
- 主/采服务内部接口（如有）校验 `MYXOS_INTERNAL_TOKEN`
- 数据库密码、JWT 密钥、内部令牌均通过环境变量注入

---

## 六、已确认事项

| 序号 | 问题 | 结论 |
|------|------|------|
| 1 | 设备详情页是否嵌入实时投屏播放器？ | **不需要**，详情页仅展示指标、操作、告警、日志、任务 |
| 2 | 阈值规则是否支持"连续 N 次采集触发"？ | **需要**，已增加 `trigger_mode`（DURATION / CONSECUTIVE）字段 |
| 3 | 是否支持自定义 HTTP 调用动作？ | **不需要**，所有操作类型必须在后端 `OperationCode` 和 `MytosClient` 中显式实现 |
| 4 | 用户是否支持修改自身密码？ | **支持**，已增加 `POST /api/users/me/password` 接口 |
| 5 | 告警是否通过 WebSocket 实时推送？ | **需要**，新增 FIRING 告警通过 WebSocket 推送，历史告警仍通过 `GET /api/alarms` 查询 |

以上设计已确认，可进入 `2026-07-29-mytos-monitor.md` 实施阶段。
