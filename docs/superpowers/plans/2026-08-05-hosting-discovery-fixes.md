# 设备托管发现修复实施计划

> **给代理工作者：** 必需子技能：使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务逐步实施。步骤使用复选框 `- [ ]` 语法以便跟踪。

**目标：** 支持不规则 IP 范围发现、逻辑删除设备重新发现，并为逐 IP 结果表格提供四列本地列头筛选。

**架构：** 前端把 IP 范围拆为多个既有单 CIDR 扫描请求，服务端接口和扫描器保持不变。数据库通过新的 Flyway 迁移移除过期唯一索引，使 V4 引入的有效记录唯一索引成为唯一冲突约束。逐 IP 结果保持完整加载，通过可测试的前端纯函数进行本地筛选。

**技术栈：** Vue 3、Element Plus、Vite、Vitest、Java 8、Spring Boot 2.7、MyBatis-Plus、Flyway、MySQL 8。

## 全局约束

- 所有新增或更新文字、测试名和代码注释使用中文；技术名称、类名、方法名、变量名、URL、配置键可保留英文。
- 保持单次发现最多 256 个 IP 地址和现有端口范围校验；不修改 `/api/discover/scan` 的请求结构。
- 不调用真实 MYTOS 设备，不新增安卓 CPU、内存、进程或卡顿采集功能。
- 仅修改本计划列出的文件，不重构扫描器并发模型、发现任务数据模型或手工 Shell API。
- 未经用户明确要求，不创建 Git 提交。

---

### 任务 1：建立前端可测试的范围提交与结果筛选逻辑

**文件：**
- 修改：`myxos-ui/package.json`
- 修改：`myxos-ui/src/utils/ip.js`
- 创建：`myxos-ui/src/utils/discover.js`
- 创建：`myxos-ui/src/utils/discover.test.js`

**接口：**
- 消费：`ipRangeToCidr(startIp, endIp)` 返回 CIDR 数组；发现 API 的 `scan(payload)` 返回 Promise。
- 产出：`submitRangeDiscover(scan, startIp, endIp, portFrom, portTo)` 和 `filterIpResults(rows, filters)` 纯函数，供托管页调用。

- [ ] **步骤 1：添加 Vitest 与测试命令**

在 `myxos-ui/package.json` 的 `devDependencies` 中增加 `vitest`，并增加脚本：

```json
"test": "vitest run"
```

- [ ] **步骤 2：先编写失败测试**

在 `discover.test.js` 中覆盖范围拆分与四列筛选：

```javascript
import { describe, expect, it, vi } from 'vitest'
import { filterIpResults, submitRangeDiscover } from './discover'

it('将 192.168.107.1 到 192.168.107.254 按顺序提交所有 CIDR', async () => {
  const scan = vi.fn().mockResolvedValue({ data: { id: 1 } })
  const result = await submitRangeDiscover(scan, '192.168.107.1', '192.168.107.254', 81, 81)
  expect(result.submittedCount).toBeGreaterThan(1)
  expect(scan).toHaveBeenCalledTimes(result.submittedCount)
  expect(scan.mock.calls[0][0]).toMatchObject({ portFrom: 81, portTo: 81 })
})

it('按 IP、端口、结果和说明同时筛选逐 IP 结果', () => {
  const rows = [
    { ip: '192.168.107.1', port: 81, result: 'ADDED', message: '已添加' },
    { ip: '192.168.107.2', port: 81, result: 'DUPLICATE', message: '设备已存在' }
  ]
  expect(filterIpResults(rows, { ip: '107.2', port: [81], result: ['DUPLICATE'], message: '存在' }))
    .toEqual([rows[1]])
})

it('范围拆分请求失败时停止后续提交并保留已提交数量', async () => {
  const scan = vi.fn()
    .mockResolvedValueOnce({ data: { id: 1 } })
    .mockRejectedValueOnce(new Error('网络错误'))
  await expect(submitRangeDiscover(scan, '192.168.107.1', '192.168.107.254', 81, 81))
    .rejects.toMatchObject({ message: '网络错误', submittedCount: 1 })
  expect(scan).toHaveBeenCalledTimes(2)
})
```

- [ ] **步骤 3：运行测试确认失败**

运行：

```powershell
Set-Location myxos-ui
npm test -- discover.test.js
```

预期：失败，原因是 `discover.js` 尚未提供两个函数。

- [ ] **步骤 4：实现最小纯函数**

在 `discover.js` 中：

```javascript
import { ipRangeToCidr } from './ip'

export async function submitRangeDiscover(scan, startIp, endIp, portFrom, portTo) {
  const cidrs = ipRangeToCidr(startIp, endIp)
  const submitted = []
  for (const cidr of cidrs) {
    try {
      await scan({ cidr, portFrom, portTo })
      submitted.push(cidr)
    } catch (error) {
      error.submittedCount = submitted.length
      throw error
    }
  }
  return { submittedCount: submitted.length, cidrs: submitted }
}

export function filterIpResults(rows, filters) {
  return rows.filter(row =>
    (!filters.ip || row.ip.includes(filters.ip)) &&
    (!filters.port.length || filters.port.includes(row.port)) &&
    (!filters.result.length || filters.result.includes(row.result)) &&
    (!filters.message || (row.message || '').includes(filters.message))
  )
}
```

在调用前由页面继续负责 IPv4 格式、空范围和端口合法性校验；`submitRangeDiscover` 不吞掉 API 异常，确保页面可展示部分提交结果。

- [ ] **步骤 5：运行前端单元测试**

运行：

```powershell
Set-Location myxos-ui
npm test -- discover.test.js
```

预期：范围拆分和四列组合筛选测试通过。

### 任务 2：接入托管页范围发现与逐 IP 列头筛选

**文件：**
- 修改：`myxos-ui/src/views/HostingView.vue`

**接口：**
- 消费：任务 1 的 `submitRangeDiscover`、`filterIpResults`；现有 `discoverApi.scan`；`taskDetail.ipResults`。
- 产出：范围模式可创建多个发现任务；抽屉结果表格展示已筛选行。

- [ ] **步骤 1：修改范围模式提交行为**

在 `HostingView.vue` 中移除“CIDR 数量大于 1”时的报错。范围模式校验 IP 后调用 `submitRangeDiscover(discoverApi.scan, ...)`；成功显示“已提交 N 个扫描任务”。失败时从异常携带的 `submittedCount` 显示“已提交 N 个扫描任务，后续提交失败：<原因>”。CIDR 模式继续只提交一次。

- [ ] **步骤 2：接入四列列头筛选**

新增响应式 `ipResultFilters`，字段为 `ip`、`port`、`result`、`message`；以 `computed` 生成：

```javascript
const filteredIpResults = computed(() => filterIpResults(taskDetail.ipResults || [], ipResultFilters))
```

将抽屉表格 `:data` 改为 `filteredIpResults`。IP 与说明列使用列头插槽中的输入框；端口与结果列从 `taskDetail.ipResults` 派生 `filters`，并使用 Element Plus `filter-method` 写入数组筛选状态。切换或关闭抽屉时重置四项筛选，避免上一次任务的条件影响下一次展示。

- [ ] **步骤 3：运行前端测试与构建**

运行：

```powershell
Set-Location myxos-ui
npm test
npm run build
```

预期：测试和 Vite 生产构建成功。

### 任务 3：修复逻辑删除设备的唯一索引迁移

**文件：**
- 创建：`myxos-main-service/src/main/resources/db/migration/V9__allow_rediscover_deleted_device.sql`
- 创建：`myxos-main-service/src/test/java/bob/myxos/main/migration/DeviceRediscoveryMigrationTest.java`

**接口：**
- 消费：`V1__init.sql` 的 `uk_ip_port` 和 `V4__device_unique_index.sql` 的 `uk_device_ip_port_active`。
- 产出：升级数据库移除原始唯一索引，保留有效记录唯一索引；扫描器已有 `deviceMapper.insert` 可以插入已逻辑删除设备的同一 IP/端口。

- [ ] **步骤 1：编写失败的迁移结构测试**

测试读取 V9 脚本，断言它显式处理旧索引并不删除有效记录唯一索引：

```java
@Test
void 迁移必须移除旧的IP端口唯一索引并保留有效记录唯一索引() throws IOException {
    String sql = new String(Files.readAllBytes(Paths.get(
            "src/main/resources/db/migration/V9__allow_rediscover_deleted_device.sql")), StandardCharsets.UTF_8);
    assertTrue(sql.contains("DROP INDEX uk_ip_port ON device"));
    assertTrue(sql.contains("uk_device_ip_port_active"));
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：

```powershell
mvn -pl myxos-main-service -Dtest=DeviceRediscoveryMigrationTest test
```

预期：失败，因为 V9 脚本尚不存在。

- [ ] **步骤 3：创建幂等 Flyway 迁移**

脚本用 MySQL 存储过程检查 `information_schema.statistics`：若 `uk_ip_port` 存在，执行 `DROP INDEX uk_ip_port ON device`；随后校验 `uk_device_ip_port_active` 存在，若不存在则停止迁移并报错。不得删除 `active` 生成列或 `uk_device_ip_port_active`。

- [ ] **步骤 4：运行迁移结构测试**

运行：

```powershell
mvn -pl myxos-main-service -Dtest=DeviceRediscoveryMigrationTest test
```

预期：测试通过，说明迁移明确移除旧索引且保护有效记录唯一约束。

### 任务 4：执行全量回归验证

**文件：**
- 验证：`myxos-ui/src/utils/discover.test.js`
- 验证：`myxos-main-service/src/test/java/bob/myxos/main/migration/DeviceRediscoveryMigrationTest.java`
- 验证：`myxos-main-service/src/main/resources/db/migration/V9__allow_rediscover_deleted_device.sql`

**接口：**
- 消费：前三项任务的实现。
- 产出：不调用真实设备的前端、后端与构建验证结果。

- [ ] **步骤 1：检查变更范围与空白**

运行：

```powershell
git diff --check
git status --short
```

预期：无空白错误；变更仅限前端测试/逻辑、Flyway 迁移与迁移测试。

- [ ] **步骤 2：运行全量测试**

运行：

```powershell
mvn test
Set-Location myxos-ui
npm test
npm run build
```

预期：Maven 测试、Vitest 测试与前端构建均成功；不产生真实设备请求。

- [ ] **步骤 3：提交前复核**

确认扫描请求仍使用 `/api/discover/scan` 的 `{ cidr, portFrom, portTo }`，安卓指标功能没有进入本次差异。未经用户明确要求，不创建提交。
