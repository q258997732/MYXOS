# MYXOS

MYXOS 是面向 MYTOS 设备的自监控与运维系统，采用 Spring Boot 2.7.x + JDK 1.8 多模块架构，支持设备发现、指标采集、阈值告警、手动/自动操作任务与数据清理。

## 技术栈

- **后端**：Spring Boot 2.7.18、Spring Security、JWT、MyBatis-Plus 3.5.7、Flyway 8.5.13
- **数据库**：MySQL 8.0
- **设备客户端**：OkHttp 4.12.0
- **前端**：Vue 3 + Element Plus + Pinia + Vue Router 4 + Vite
- **构建工具**：Maven 3.8+
- **容器化**：Docker + Docker Compose

## 模块说明

| 模块 | 说明 |
|------|------|
| `myxos-common` | 公共枚举、统一响应、工具类 |
| `myxos-domain` | 实体、Mapper、MyBatis-Plus 配置、审计字段填充 |
| `myxos-mytos-client` | MYTOS 设备 HTTP API 客户端 |
| `myxos-main-service` | Web 服务、JWT 鉴权、管理 API、前端静态资源（端口 8080） |
| `myxos-collector-service` | 定时采集、阈值判定、动作执行、数据清理（端口 8081） |
| `myxos-ui` | Vue 3 管理后台 |

## 快速开始

### 1. 环境要求

- JDK 1.8+
- Maven 3.8+
- MySQL 8.0
- Node.js 18+（单独开发前端时使用）

### 2. 数据库准备

创建数据库：

```sql
CREATE DATABASE myxos CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 3. 配置环境变量

```bash
export MYSQL_HOST=localhost
export MYSQL_USER=root
export MYSQL_PASSWORD="your-mysql-password"
export MYXOS_JWT_SECRET="your-32-bytes-secret-key-here-please"
export MYXOS_INTERNAL_TOKEN="your-internal-token-here"
```

> 生产环境请务必替换为强密钥，且 `MYXOS_JWT_SECRET` 长度不少于 32 字节。

### 4. 本地启动

后端服务模块存在内部 Maven 依赖；首次启动或前端依赖发生变化后，请先在根目录执行以下命令，将全部模块安装到本地仓库并生成前端构建产物：

```bash
mvn clean install -DskipTests
```

启动主服务：

```bash
mvn -pl myxos-main-service spring-boot:run -DskipTests
```

启动采集服务：

```bash
mvn -pl myxos-collector-service spring-boot:run -DskipTests
```

访问管理后台：http://localhost:8080

系统首次启动时会通过 Flyway 初始化管理员账号，初始密码请在首次登录后立刻修改。

前端可以独立开发和验证。在另一个终端中执行：

```bash
cd myxos-ui
npm install
npm run dev
```

Vite 开发服务器默认访问地址为 http://localhost:5173。构建前端并验证构建结果：

```bash
cd myxos-ui
npm run build
```

### 5. 全量打包

```bash
mvn clean package -DskipTests
```

该命令会构建五个后端模块，并通过 `myxos-main-service` 的 `frontend-maven-plugin` 安装 Node.js 和 npm、构建 `myxos-ui`，再将前端产物复制到主服务的静态资源目录。打包后主服务 jar 位于 `myxos-main-service/target/`，采集服务 jar 位于 `myxos-collector-service/target/`。

## Docker 部署

### 构建并启动全部服务

```bash
export MYSQL_PASSWORD="your-mysql-password"
export MYXOS_JWT_SECRET="your-32-bytes-secret-key-here-please"
export MYXOS_INTERNAL_TOKEN="your-internal-token-here"
docker-compose up -d --build
```

> `docker-compose.yml` 中未设置任何默认密码或密钥，缺失必要环境变量时容器会立即失败。

### 查看日志

```bash
docker-compose logs -f main-service
docker-compose logs -f collector-service
```

### 停止服务

```bash
docker-compose down
```

## 测试

运行全部后端单元测试与集成测试：

```bash
mvn test
```

生成 JaCoCo 覆盖率报告：

```bash
mvn verify
```

报告位于各模块 `target/site/jacoco/index.html`。

前端当前未定义独立测试脚本，使用以下命令完成构建验证：

```bash
cd myxos-ui
npm run build
```

## 主要功能

- 设备自动发现与手动托管
- 定时指标采集（CPU、内存、磁盘、网络、温度等）
- 阈值规则与告警管理
- 告警触发自动/手动操作任务（重启、采集、日志记录等）
- 操作任务队列与重试
- 系统配置与日志查询
- 用户角色管理（ADMIN / OPERATOR / VIEWER）

## 安全约定

- JWT 密钥与内部通信令牌通过环境变量注入，禁止写入源码
- 数据库密码通过环境变量注入
- 主服务对外暴露管理 API，采集服务内部接口通过 `X-Internal-Token` 保护
- 首次部署后请立即修改默认管理员密码

## 许可证

MIT
