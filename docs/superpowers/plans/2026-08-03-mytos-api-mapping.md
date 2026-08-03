# MYTOS 监控/运维接口映射规划

> 依据 `mytos_real_openapi.json` 与前端页面规划整理，**已按用户最终确认范围定稿**。本文件为实施依据，相关内容已同步写入 `CLAUDE.md`。

---

## 一、说明

- **基本原则**：优先使用带版本前缀的接口（`/host_api/v1/`、`/dc_api/v1/`、`/and_api/v1/`），避免使用根路径下的同名旧接口。
- **路径变量约定**：
  - `host_ip` / `ip`：3588 主机 IP（即监控系统中 `device.ip`）。
  - `name`：安卓容器/实例名称（对应监控系统中实例标识）。
- **版本选择**：全部接口均来自 `mytos_real_openapi.json`，优先采用 `/host_api/v1/`、`/dc_api/v1/`、`/and_api/v1/` 版本。

---

## 二、首期接入接口清单（已确认）

### 2.1 用户明确功能

| 功能描述 | 推荐接口 | 方法 | 路径变量/关键参数 |
|---------|---------|------|-----------------|
| 主机存活：获取健康状态 | `POST /host_api/v1/healthcheck/{host_ip}` | POST | `host_ip` |
| 重启主机 | `GET /host_api/v1/reboot_host/{host_ip}` | GET | `host_ip`，可选 `isblock`、`timeout`、`ssh_uname`、`ssh_pwd`、`root_pwd` |
| 获取安卓实例启动状态 | `GET /and_api/v1/get_android_boot_status/{ip}/{name}` | GET | `ip`、`name`，可选 `isblock`、`timeout`、`init_devinfo` |
| 获取所有安卓实例列表 | `GET /dc_api/v1/list/{ip}` | GET | `ip`，可选 `extraQuery` |
| 获取指定安卓实例详情 | `GET /dc_api/v1/get_android_detail/{ip}/{name}` | GET | `ip`、`name` |
| 启动安卓实例 | `GET /dc_api/v1/run/{ip}/{name}` | GET | `ip`、`name`，可选 `force` |
| 停止安卓实例 | `GET /dc_api/v1/stop/{ip}/{name}` | GET | `ip`、`name` |
| 重启安卓实例 | `GET /dc_api/v1/reboot/{ip}/{name}` | GET | `ip`、`name` |

### 2.2 日常监控（只读）

| 功能 | 推荐接口 | 方法 |
|------|---------|------|
| 获取指定主机系统信息 | `GET /host_api/v1/get_systeminfo/{host_ip}` | GET |
| 获取 3588 本机信息 | `GET /host_api/v1/systeminfo` | GET |
| 获取主机硬件配置 | `GET /host_api/v1/get_hardware_cfg` | GET |
| 获取主机版本 | `GET /dc_api/v1/get_host_ver/{ip}` | GET |
| 获取网络对象明细 | `GET /dc_api/v1/get_network_detail/{ip}` | GET |
| 获取局域网在线设备 | `GET /host_api/v1/query_myt` | GET |

### 2.3 运维核心（容器生命周期）

| 功能 | 推荐接口 | 方法 | OperationCode |
|------|---------|------|--------------|
| 运行安卓容器 | `GET /dc_api/v1/run/{ip}/{name}` | GET | `RUN_ANDROID` |
| 停止安卓容器 | `GET /dc_api/v1/stop/{ip}/{name}` | GET | `STOP_ANDROID` |
| 重启安卓容器 | `GET /dc_api/v1/reboot/{ip}/{name}` | GET | `REBOOT_ANDROID` |
| 重置安卓容器 | `GET /dc_api/v1/reset/{ip}/{name}` | GET | `RESET_ANDROID` |
| 重命名容器 | `GET /dc_api/v1/rename/{ip}/{old_name}/{new_name}` | GET | `RENAME_ANDROID` |

### 2.4 手动操作面板

| 前端操作 | 推荐接口 | 方法 | OperationCode | 说明 |
|---------|---------|------|--------------|------|
| 设备截图（临时查看） | `GET /and_api/v1/screenshots/{ip}/{name}/{level}` | GET | `SCREENSHOT` | 临时展示，不保存、不进 `op_task` |
| 执行 Adb 命令 | `POST /and_api/v1/shell/{ip}/{name}` | POST | `SHELL_ADB` | 请求体为 shell 命令 |
| 设置剪贴板 | `GET /and_api/v1/clipboard_set/{ip}/{name}` | GET | `SET_CLIPBOARD` | `text` 查询参数 |
| 获取剪贴板 | `GET /and_api/v1/clipboard_get/{ip}/{name}` | GET | `GET_CLIPBOARD` | 查询类 |
| 设置系统语言 | `GET /and_api/v1/set_Language/{ip}/{name}/{country}/{lanuage}` | GET | `SET_LANGUAGE` | `country`、`lanuage` 路径参数 |
| IP 智能定位 | `GET /and_api/v1/set_ipLocation/{ip}/{name}/{lanuage}` | GET | `REFRESH_LOCATION` | 对应原 `REFRESH_LOC` |

---

## 三、不采纳接口（记录备查）

以下接口经用户确认不在首期接入：

- S5 代理相关：`/and_api/v1/s5_set`、`/and_api/v1/s5_stop`、`/and_api/v1/s5_query`、`/and_api/v1/s5_filter_url`
- 更新指纹信息：`/and_api/v1/upload_fingerprint`
- 备份/清理/排障：`/dc_api/v1/export`、`/dc_api/v1/import`、`/dc_api/v1/prune_images`、`/dc_api/v1/reset_network`
- 可选扩展：容器创建/批量创建、Swap、授权同步、Myt Bridge 网络管理等

---

## 四、OperationCode 枚举（最终版）

```java
package bob.myxos.common.enums;

public enum OperationCode {
    // 主机级
    REBOOT_HOST,

    // 容器实例生命周期
    RUN_ANDROID,
    STOP_ANDROID,
    REBOOT_ANDROID,
    RESET_ANDROID,
    RENAME_ANDROID,

    // 安卓实例内部操作
    SET_CLIPBOARD,
    GET_CLIPBOARD,
    SET_LANGUAGE,
    REFRESH_LOCATION,
    SCREENSHOT,
    SHELL_ADB
}
```

---

## 五、自动发现设备流程

1. 用户提交 CIDR + 端口范围（`discover_task`）。
2. 采集服务对每个 `ip:port` 调用 `POST /host_api/v1/healthcheck/{host_ip}` 探测主机存活。
3. 存活主机再调用 `GET /host_api/v1/get_systeminfo/{host_ip}` 与 `GET /dc_api/v1/get_host_ver/{ip}` 补充主机信息。
4. 将该主机写入 `device` 表（`source=DISCOVERED`）。
5. 后续定时采集周期性调用 `/dc_api/v1/list/{ip}` 刷新该主机下的安卓实例清单，用于前端"设备详情"页展示实例列表。

---

## 六、截图接口特别说明

`GET /and_api/v1/screenshots/{ip}/{name}/{level}` 按用户要求纳入手动操作面板，但：

- **仅临时展示**：前端获取图片流后直接在弹窗中展示，**不持久化到服务器磁盘或数据库**。
- **不生成 `op_task`**：截图作为即时查询类操作，直接通过 `MytosClient` 调用并返回，不进入异步任务队列。

---

## 七、同步范围

本规划定稿后，已同步更新：

- `CLAUDE.md`：新增"MYTOS 监控/运维接口映射"章节。
- `docs/superpowers/plans/2026-07-29-mytos-monitor.md`：任务 5 `MytosClient` DTO/方法、任务 11 `OperationCode` 与 `OpTaskRunner` 映射。
- `docs/superpowers/plans/2026-07-29-system-design.md`：1.3.4 手动操作面板操作类型说明。
