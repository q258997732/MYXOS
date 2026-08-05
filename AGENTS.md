# AGENTS.md

This file provides guidance to Codex (Codex.ai/code) when working with code in this repository.

## 语言要求

所有回答、文档、代码注释、提交信息必须使用中文。技术不可翻译处（类名、方法名、变量名、URL 路径、配置键名）可保留英文。

## 项目状态

当前仓库是单模块 Spring Boot 项目（Spring Boot 4.1.1-SNAPSHOT / Java 17），仅包含一个空的启动类 `bob.myxos.MyxosApplication`。项目已规划改造为 JDK 1.8 + Spring Boot 2.7.x 的多模块 Maven 系统，详见 `docs/superpowers/plans/2026-07-29-mytos-monitor.md`。

## 目标架构

改造后采用以下五模块结构：

- `myxos-common`：公共枚举、统一响应、工具类
- `myxos-domain`：实体、Mapper、MyBatis-Plus 配置、审计字段填充
- `myxos-mytos-client`：MYTOS 设备 HTTP API 客户端（OkHttp）
- `myxos-main-service`：Web 服务、JWT 鉴权、管理 API、前端静态资源
- `myxos-collector-service`：定时采集、阈值判定、动作执行、数据清理

主服务与采集服务通过共享 MySQL 数据库通信；主服务端口 8080，采集服务端口 8081。

## 常用命令

### 当前单模块项目

```bash
# 全量构建
mvn clean package

# 跳过测试构建
mvn clean package -DskipTests

# 运行所有测试
mvn test

# 运行单个测试类
mvn test -Dtest=MyxosApplicationTests

# 运行单个测试方法
mvn test -Dtest=MyxosApplicationTests#contextLoads

# 启动当前应用
mvn spring-boot:run
```

### 多模块改造后

```bash
# 编译并安装 common 到本地仓库
mvn -pl myxos-common clean install -DskipTests

# 启动主服务
mvn -pl myxos-main-service spring-boot:run -DskipTests

# 启动采集服务
mvn -pl myxos-collector-service spring-boot:run -DskipTests

# 打包所有模块
mvn clean package -DskipTests
```

## 数据库约定

- 使用 MySQL 8.0，字符集 utf8mb4。
- 所有表必须包含 `who_created`、`when_created`、`who_modified`、`when_modified` 四个审计字段。
- 使用 MyBatis-Plus 的 `@TableLogic` 实现逻辑删除。
- Flyway 迁移脚本放在 `myxos-main-service/src/main/resources/db/migration/`。

## 安全约定

- JWT 密钥通过 `MYXOS_JWT_SECRET` 环境变量注入。
- 主服务与采集服务内部通信通过 `MYXOS_INTERNAL_TOKEN` 环境变量注入的共享令牌保护。
- 数据库密码通过环境变量注入，禁止写入源码。

## 提交规范

提交信息使用中文，格式 `<类型>: <中文描述>`。类型：`feat`、`fix`、`refactor`、`docs`、`test`、`chore`、`perf`、`ci`。

示例：`feat(auth): 实现 JWT 登录与登出功能`

## MYTOS 监控/运维接口映射

> 详细说明见 `docs/superpowers/plans/2026-08-03-mytos-api-mapping.md`。以下为首期确认接入的接口汇总。

### 接口选择原则

- 优先使用带版本前缀的接口：`/host_api/v1/`、`/dc_api/v1/`、`/and_api/v1/`。
- `host_ip` / `ip` 对应监控系统中的 `device.ip`，`name` 对应安卓实例名称。

### 用户明确功能

| 功能 | 接口 | 方法 |
|------|------|------|
| 主机存活/健康检查 | `/host_api/v1/healthcheck/{host_ip}` | POST |
| 重启主机 | `/host_api/v1/reboot_host/{host_ip}` | GET |
| 获取安卓实例启动状态 | `/and_api/v1/get_android_boot_status/{ip}/{name}` | GET |
| 获取所有安卓实例列表 | `/dc_api/v1/list/{ip}` | GET |
| 获取指定安卓实例详情 | `/dc_api/v1/get_android_detail/{ip}/{name}` | GET |
| 启动安卓实例 | `/dc_api/v1/run/{ip}/{name}` | GET |
| 停止安卓实例 | `/dc_api/v1/stop/{ip}/{name}` | GET |
| 重启安卓实例 | `/dc_api/v1/reboot/{ip}/{name}` | GET |

### 日常监控（只读）

| 功能 | 接口 | 方法 |
|------|------|------|
| 获取指定主机系统信息 | `/host_api/v1/get_systeminfo/{host_ip}` | GET |
| 获取 3588 本机信息 | `/host_api/v1/systeminfo` | GET |
| 获取主机硬件配置 | `/host_api/v1/get_hardware_cfg` | GET |
| 获取主机版本 | `/dc_api/v1/get_host_ver/{ip}` | GET |
| 获取网络对象明细 | `/dc_api/v1/get_network_detail/{ip}` | GET |
| 获取局域网在线设备 | `/host_api/v1/query_myt` | GET |

### 运维核心（容器生命周期）

| 功能 | 接口 | 方法 | OperationCode |
|------|------|------|--------------|
| 运行安卓容器 | `/dc_api/v1/run/{ip}/{name}` | GET | `RUN_ANDROID` |
| 停止安卓容器 | `/dc_api/v1/stop/{ip}/{name}` | GET | `STOP_ANDROID` |
| 重启安卓容器 | `/dc_api/v1/reboot/{ip}/{name}` | GET | `REBOOT_ANDROID` |
| 重置安卓容器 | `/dc_api/v1/reset/{ip}/{name}` | GET | `RESET_ANDROID` |
| 重命名容器 | `/dc_api/v1/rename/{ip}/{old_name}/{new_name}` | GET | `RENAME_ANDROID` |

### 手动操作面板

| 功能 | 接口 | 方法 | OperationCode | 说明 |
|------|------|------|--------------|------|
| 设备截图（临时查看） | `/and_api/v1/screenshots/{ip}/{name}/{level}` | GET | `SCREENSHOT` | 仅临时展示，不保存、不进 `op_task` |
| 执行 Adb 命令 | `/and_api/v1/shell/{ip}/{name}` | POST | `SHELL_ADB` | 请求体为 shell 命令 |
| 设置剪贴板 | `/and_api/v1/clipboard_set/{ip}/{name}` | GET | `SET_CLIPBOARD` | `text` 查询参数 |
| 获取剪贴板 | `/and_api/v1/clipboard_get/{ip}/{name}` | GET | `GET_CLIPBOARD` | 查询类 |
| 设置系统语言 | `/and_api/v1/set_Language/{ip}/{name}/{country}/{lanuage}` | GET | `SET_LANGUAGE` | `country`、`lanuage` 路径参数 |
| IP 智能定位 | `/and_api/v1/set_ipLocation/{ip}/{name}/{lanuage}` | GET | `REFRESH_LOCATION` | 对应原 `REFRESH_LOC` |

### OperationCode 枚举（首期）

```java
public enum OperationCode {
    REBOOT_HOST,
    RUN_ANDROID,
    STOP_ANDROID,
    REBOOT_ANDROID,
    RESET_ANDROID,
    RENAME_ANDROID,
    SET_CLIPBOARD,
    GET_CLIPBOARD,
    SET_LANGUAGE,
    REFRESH_LOCATION,
    SCREENSHOT,
    SHELL_ADB
}
```

### 自动发现流程

1. 用户提交 CIDR + 端口范围（`discover_task`）。
2. 采集服务对每个 `ip:port` 调用 `POST /host_api/v1/healthcheck/{host_ip}` 探测主机存活。
3. 存活主机再调用 `GET /host_api/v1/get_systeminfo/{host_ip}` 与 `GET /dc_api/v1/get_host_ver/{ip}` 补充信息。
4. 写入 `device` 表（`source=DISCOVERED`）。
5. 后续定时采集周期性调用 `/dc_api/v1/list/{ip}` 刷新安卓实例清单。

## 参考文档

- `docs/superpowers/plans/2026-07-29-mytos-monitor.md`：完整实施计划
- `docs/superpowers/plans/2026-07-29-system-design.md`：系统详细设计（页面、API、流程、状态机）
- `docs/superpowers/plans/2026-08-03-mytos-api-mapping.md`：MYTOS 监控/运维接口映射
