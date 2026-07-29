# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

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

## 参考文档

- `docs/superpowers/plans/2026-07-29-mytos-monitor.md`：完整实施计划
- `docs/superpowers/plans/2026-07-29-system-design.md`：系统详细设计（页面、API、流程、状态机）
- `MYTOS API 接口文档.md`：设备端 HTTP API 参考
