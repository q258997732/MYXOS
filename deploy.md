# MYXOS Docker Compose 部署指南

本文档说明如何在一台新的 Linux 服务器上使用 Docker Compose 部署整套 MYXOS 系统。

## 前置要求

- Linux 服务器（建议 Ubuntu 22.04 LTS 或更高版本）
- 已安装 Docker 与 Docker Compose 插件
- 服务器能够访问被监控的 MYTOS 设备网络

## 部署服务清单

| 服务 | 容器名 | 内部端口 | 说明 |
|------|--------|----------|------|
| MySQL | myxos-mysql | 3306 | 数据库，数据持久化在 Docker volume |
| 主服务 | myxos-main-service | 8080 | Web API + 前端静态资源 |
| 采集服务 | myxos-collector-service | 8081 | 定时采集、阈值判定、动作执行 |
| Nginx | myxos-nginx | 80 / 443 | 反向代理（可选，推荐用于域名/HTTPS） |

## 快速开始

### 1. 安装 Docker（Ubuntu 示例）

```bash
sudo apt update
sudo apt install -y docker.io docker-compose-plugin git
sudo systemctl enable --now docker
```

### 2. 拉取代码

```bash
git clone <仓库地址> /opt/myxos
cd /opt/myxos
```

### 3. 配置环境变量

```bash
cp .env.example .env
nano .env
```

必填项：

- `MYSQL_ROOT_PASSWORD`：MySQL root 密码
- `MYSQL_PASSWORD`：应用账号密码
- `MYXOS_JWT_SECRET`：JWT 签名密钥，建议生成随机字符串
- `MYXOS_INTERNAL_TOKEN`：主服务与采集服务内部通信令牌

生成随机令牌示例：

```bash
openssl rand -base64 32
```

### 4. 启动系统

```bash
docker compose up -d --build
```

首次启动会：

1. 构建主服务与采集服务镜像
2. 拉取 MySQL 8.0 与 Nginx 镜像
3. 创建 `myxos` 数据库
4. 主服务启动后由 Flyway 自动执行 `db/migration/*.sql`
5. 采集服务启动后开始定时采集任务

### 5. 查看运行状态

```bash
docker compose ps
docker compose logs -f myxos-main-service
docker compose logs -f myxos-collector-service
docker compose logs -f mysql
```

### 6. 访问系统

- 通过 Nginx 访问：`http://服务器IP`
- 本机调试主服务：`http://127.0.0.1:8080`（生产环境建议仅通过 Nginx 入口访问）

## 常用维护命令

```bash
# 停止所有服务
docker compose down

# 停止并删除数据卷（危险：会清空数据库）
docker compose down -v

# 重建并重启某个服务
docker compose up -d --build myxos-main-service

# 查看主服务日志最后 100 行
docker compose logs --tail=100 myxos-main-service

# 进入 MySQL 容器执行 SQL（执行后会交互式提示输入密码）
docker compose exec mysql mysql -u myxos -p myxos

# 进入主服务容器
docker compose exec myxos-main-service sh
```

## 安全配置

1. **禁止提交 `.env` 文件**：`.env` 已加入 `.gitignore`，请确保不会意外提交。
2. **MySQL 不直接暴露公网**：`docker-compose.yml` 中已将 MySQL 3306 绑定到 `127.0.0.1`，生产环境建议删除该端口映射。
3. **主服务端口不直接暴露公网**：`docker-compose.yml` 中主服务 8080 已绑定到 `127.0.0.1`，统一由 Nginx 对外提供 80/443 入口，避免绕过反向代理直接访问后端。
4. **使用 HTTPS**：准备 SSL 证书放到 `ssl/` 目录，并取消 `nginx.conf` 与 `docker-compose.yml` 中的 HTTPS 注释。
5. **定期修改密钥**：`MYXOS_JWT_SECRET` 与 `MYXOS_INTERNAL_TOKEN` 应定期轮换。
6. **数据库账号权限**：当前 `MYSQL_USER` 默认拥有 `myxos` 库全部权限（含 DDL），可满足 Flyway 迁移需求。生产环境中如需更严格权限控制，可拆分为迁移账号（DDL）与应用账号（DML）。

## 健康检查

当前 `docker-compose.yml` 使用主服务的 `/index.html` 进行基础探活。该探活只验证 Web 服务可响应；设备连通性、安卓实例状态、受控指标采集和告警处理由采集服务按各自调度周期执行。

## 故障排查

### 主服务无法连接 MySQL

检查环境变量 `MYSQL_HOST` 是否为 `mysql`（Docker 内部服务名），以及 MySQL 健康检查是否通过。

### 采集服务无法与主服务通信

确认 `MYXOS_INTERNAL_TOKEN` 在主服务与采集服务中设置一致。

### 前端页面空白

检查 `myxos-ui` 是否已成功构建并被复制到 `myxos-main-service/target/classes/static`，可在主服务容器内执行：

```bash
docker compose exec myxos-main-service ls /app/BOOT-INF/classes/static
```

### 数据库迁移失败

查看主服务日志中的 Flyway 错误，确认 `myxos-main-service/src/main/resources/db/migration/` 下的脚本版本号没有冲突。
