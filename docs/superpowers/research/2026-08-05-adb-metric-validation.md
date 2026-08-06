# 受控 ADB 指标验证记录

## 验证范围

- 验证日期：2026-08-05
- API 地址：`http://192.168.107.199:81`
- 目标主机：`192.168.107.193`
- 安卓实例：`MYTSDK1782180364795806781_3_T3001-EJNB`
- 结果：HTTP 状态码 `200`，shell 返回码 `0`
- 单条 API 响应耗时：附件示例约 `130ms`
- 本轮 5 条只读命令总耗时：约 `1.1s`

## 已验证的只读输出

| 指标或命令 | 验证结果 |
| --- | --- |
| `getprop ro.build.version.release` | Android `12` |
| `getprop ro.product.model` | `2410DPN6CC` |
| `cat /proc/meminfo` | 包含 `MemTotal`、`MemAvailable` 等内存字段 |
| `top -b -n 1` | 包含 `Tasks: ... total` 和多核汇总 `%cpu`、`%idle` 字段 |
| `dumpsys activity recents` | API 返回最近应用列表响应 |

## 输出预览与耗时

以下内容为响应预览，较长输出已截断；大小为基于预览和响应记录的近似值，不能作为完整原始输出归档。

| 命令 | 输出摘要 | 预览大小 | shell_code |
| --- | --- | --- | --- |
| `getprop ro.build.version.release` | `12` | 约 `3B` | `0` |
| `getprop ro.product.model` | `2410DPN6CC` | 约 `11B` | `0` |
| `cat /proc/meminfo` | 含 `MemTotal`、`MemAvailable` 等字段 | 约 `4KB`，预览截断 | `0` |
| `top -b -n 1` | `Tasks: 99 total`；`800%cpu 36%user 0%nice 16%sys 736%idle 4%iow` | 约 `8KB`，预览截断 | `0` |
| `dumpsys activity recents` | 返回最近应用列表 | 约 `2KB`，预览截断 | `0` |

按上述 `top` 前缀计算，CPU 使用率为 `8.00%`；任务总数为 `99`。

本记录仅覆盖授权的只读诊断命令，不包含任何状态修改命令，也不保存截图或设备敏感内容。

## 复验记录（2026-08-06）

- 使用同一 API 地址、目标主机和安卓实例复验全部 5 条受控只读命令。
- 每条命令均返回 HTTP `200` 与 `shell_code=0`。
- `getprop ro.build.version.release` 返回 `12`；`getprop ro.product.model` 仍返回 `2410DPN6CC`。
- `/proc/meminfo` 返回 `MemTotal: 12572420 kB`、`MemAvailable: 4285780 kB`。
- `top -b -n 1` 返回 `Tasks: 97 total` 和 `800%cpu ... 676%idle`，解析器计算 CPU 使用率为 `15.50%`。
- `dumpsys activity recents` 返回最近任务列表；输出仅用于验证解析兼容性，未归档完整内容。
