# 受控 ADB 指标验证记录

## 验证范围

- 验证日期：2026-08-05
- API 地址：`http://192.168.107.199:81`
- 目标主机：`192.168.107.193`
- 安卓实例：`MYTSDK1782180364795806781_3_T3001-EJNB`
- 结果：HTTP 状态码 `200`，shell 返回码 `0`

## 已验证的只读输出

| 指标或命令 | 验证结果 |
| --- | --- |
| `getprop ro.build.version.release` | Android `12` |
| `getprop ro.product.model` | `2410DPN6CC` |
| `cat /proc/meminfo` | 包含 `MemTotal`、`MemAvailable` 等内存字段 |
| `top -b -n 1` | 包含 `Tasks: ... total` 和多核汇总 `%cpu`、`%idle` 字段 |
| `dumpsys activity recents` | API 返回最近应用列表响应 |

本记录仅覆盖授权的只读诊断命令，不包含任何状态修改命令，也不保存截图或设备敏感内容。
