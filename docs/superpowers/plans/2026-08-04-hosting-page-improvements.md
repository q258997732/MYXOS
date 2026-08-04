# 设备托管页面改进实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 修复手动添加设备报错、消除重复默认分组、让网段发现输入更友好、在发现任务中展示成功/重复 IP 详情，并在设备添加成功后写入系统日志。

**Architecture:** 后端调整 `DeviceCreateReq` 与 `DeviceServiceImpl.createDevice` 使名称可自动生成，并捕获 `DuplicateKeyException` 给出友好提示；通过 Flyway 迁移修复 `device_group` 唯一索引并清理重复；`DiscoverTask` 新增 `detail` JSON 字段记录逐 IP 结果；扫描器与手动添加服务分别写入 `ActionLog`；前端 `HostingView.vue` 增加网段范围输入、详情抽屉与表格列宽优化。

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, Flyway, Vue 3, Element Plus

---

## File Structure

| File | Responsibility |
|------|----------------|
| `myxos-domain/src/main/java/bob/myxos/domain/entity/DiscoverTask.java` | 新增 `detail` 字段 |
| `myxos-domain/src/main/java/bob/myxos/domain/mapper/DiscoverTaskMapper.java` | 复用 MyBatis-Plus |
| `myxos-main-service/src/main/resources/db/migration/V5__discover_detail_and_group_unique.sql` | 新增 `discover_task.detail` 列、修复 `device_group` 唯一索引 |
| `myxos-main-service/src/main/java/bob/myxos/main/dto/DeviceCreateReq.java` | 放宽 `name` 非空约束（自动生成） |
| `myxos-main-service/src/main/java/bob/myxos/main/service/impl/DeviceServiceImpl.java` | 自动生成名称、捕获唯一键冲突、写入 ActionLog |
| `myxos-main-service/src/main/java/bob/myxos/main/service/impl/DeviceGroupServiceImpl.java` | 创建分组前校验重名 |
| `myxos-collector-service/src/main/java/bob/myxos/collector/collector/DeviceDiscoveryScanner.java` | 收集逐 IP 结果写入 `detail`、写 ActionLog |
| `myxos-ui/src/views/HostingView.vue` | 网段范围输入、发现任务详情抽屉、表格列宽、全局 `.page-header` 与 `formatDateTime` 适配 |
| `myxos-ui/src/api/index.js` | 新增 `discoverApi.taskDetail(id)` |
| `myxos-main-service/src/main/java/bob/myxos/main/controller/DiscoverController.java` | 新增 `GET /api/discover/tasks/{id}` |
| `myxos-main-service/src/main/java/bob/myxos/main/service/DiscoverService.java` | 新增 `getTaskDetail(id)` |
| `myxos-main-service/src/main/java/bob/myxos/main/service/impl/DiscoverServiceImpl.java` | 实现详情查询 |

---

### Task 1: 修复设备分组唯一索引与重复默认分组

**Files:**
- Create: `myxos-main-service/src/main/resources/db/migration/V5__discover_detail_and_group_unique.sql`
- Modify: `myxos-main-service/src/main/java/bob/myxos/main/service/impl/DeviceGroupServiceImpl.java`

- [ ] **Step 1: 创建 Flyway 迁移脚本**

```sql
-- myxos-main-service/src/main/resources/db/migration/V5__discover_detail_and_group_unique.sql

-- 1) 清理 device_group 中重复的名称（保留 id 最小的那条）
DELETE g1 FROM device_group g1
INNER JOIN device_group g2
  ON g1.name = g2.name
  AND g1.parent_id = g2.parent_id
  AND g1.id > g2.id
WHERE g1.deleted = 0 AND g2.deleted = 0;

-- 2) 添加唯一索引，避免再次出现重复默认分组
ALTER TABLE device_group ADD UNIQUE INDEX uk_group_name_parent (name, parent_id);

-- 3) 为发现任务增加详情字段（用于记录逐 IP 结果）
ALTER TABLE discover_task ADD COLUMN detail TEXT NULL COMMENT '逐 IP 发现结果 JSON';
```

- [ ] **Step 2: 在 `DeviceGroupServiceImpl.createGroup` 中增加重名校验**

```java
// myxos-main-service/src/main/java/bob/myxos/main/service/impl/DeviceGroupServiceImpl.java
@Override
public DeviceGroup createGroup(DeviceGroupCreateReq req) {
    // 新增：同一父节点下名称不能重复
    Long count = deviceGroupMapper.selectCount(
            new LambdaQueryWrapper<DeviceGroup>()
                    .eq(DeviceGroup::getName, req.getName())
                    .eq(DeviceGroup::getParentId, req.getParentId() == null ? 0L : req.getParentId())
                    .eq(DeviceGroup::getDeleted, 0));
    if (count != null && count > 0) {
        throw new BizException("分组名称已存在");
    }
    DeviceGroup group = new DeviceGroup();
    BeanUtils.copyProperties(req, group);
    if (group.getParentId() == null) {
        group.setParentId(0L);
    }
    deviceGroupMapper.insert(group);
    return group;
}
```

- [ ] **Step 3: Commit**

```bash
git add myxos-main-service/src/main/resources/db/migration/V5__discover_detail_and_group_unique.sql myxos-main-service/src/main/java/bob/myxos/main/service/impl/DeviceGroupServiceImpl.java
git commit -m "fix(db,hosting): 修复默认分组重复问题并添加唯一索引"
```

---

### Task 2: 修复手动添加设备接口报错

**Files:**
- Modify: `myxos-main-service/src/main/java/bob/myxos/main/dto/DeviceCreateReq.java`
- Modify: `myxos-main-service/src/main/java/bob/myxos/main/service/impl/DeviceServiceImpl.java`

- [ ] **Step 1: 放宽 `DeviceCreateReq.name` 的非空校验**

```java
// myxos-main-service/src/main/java/bob/myxos/main/dto/DeviceCreateReq.java
/** 设备名称 */
@Size(max = 64, message = "设备名称长度不能超过 64")
private String name;
```

- [ ] **Step 2: 修改 `DeviceServiceImpl.createDevice` 自动生成名称并捕获唯一键冲突**

```java
// myxos-main-service/src/main/java/bob/myxos/main/service/impl/DeviceServiceImpl.java
@Override
@Transactional(rollbackFor = Exception.class)
public Device createDevice(DeviceCreateReq req) {
    validateGroupId(req.getGroupId());
    Device device = new Device();
    String name = req.getName();
    if (name == null || name.trim().isEmpty()) {
        name = req.getIp() + ":" + req.getPort();
    }
    device.setName(name);
    device.setIp(req.getIp());
    device.setPort(req.getPort());
    device.setMode(req.getMode());
    device.setGroupId(req.getGroupId());
    device.setRemark(req.getRemark());
    device.setStatus(DeviceStatus.UNKNOWN.name());
    device.setSource("MANUAL");
    try {
        deviceMapper.insert(device);
    } catch (DuplicateKeyException e) {
        throw new BizException("该 IP 和端口已存在");
    }
    writeActionLog(device, "手动添加设备：" + device.getName() + "(" + device.getIp() + ":" + device.getPort() + ")");
    return device;
}

private void writeActionLog(Device device, String message) {
    ActionLog log = new ActionLog();
    log.setDeviceId(device.getId());
    log.setActionType("SYSTEM");
    log.setLogLevel("INFO");
    log.setMessage(message);
    log.setCreatedAt(LocalDateTime.now());
    actionLogMapper.insert(log);
}
```

注意：需要保留 `org.springframework.dao.DuplicateKeyException` 导入。

- [ ] **Step 3: Commit**

```bash
git add myxos-main-service/src/main/java/bob/myxos/main/dto/DeviceCreateReq.java myxos-main-service/src/main/java/bob/myxos/main/service/impl/DeviceServiceImpl.java
git commit -m "fix(hosting): 手动添加设备名称自动生成，友好提示重复 IP"
```

---

### Task 3: 网段发现支持更友好的 IP 范围输入

**Files:**
- Modify: `myxos-ui/src/views/HostingView.vue`

- [ ] **Step 1: 在 `HostingView.vue` 中新增发现方式切换**

把原有的 CIDR 输入改为"CIDR"与"IP 范围"两种模式，本地计算成 CIDR 后提交。

```vue
<el-form :model="discoverForm" label-width="100px">
  <el-form-item label="发现方式">
    <el-radio-group v-model="discoverForm.discoverMode">
      <el-radio-button label="cidr">CIDR</el-radio-button>
      <el-radio-button label="range">IP 范围</el-radio-button>
    </el-radio-group>
  </el-form-item>

  <el-form-item v-if="discoverForm.discoverMode === 'cidr'" label="CIDR">
    <el-input v-model="discoverForm.cidr" placeholder="192.168.30.0/24" />
  </el-form-item>

  <template v-else>
    <el-form-item label="起始 IP">
      <el-input v-model="discoverForm.startIp" placeholder="192.168.30.1" />
    </el-form-item>
    <el-form-item label="结束 IP">
      <el-input v-model="discoverForm.endIp" placeholder="192.168.30.254" />
    </el-form-item>
  </template>

  <el-form-item label="起始端口">
    <el-input-number v-model="discoverForm.portFrom" :min="1" :max="65535" style="width: 100%" />
  </el-form-item>
  <el-form-item label="结束端口">
    <el-input-number v-model="discoverForm.portTo" :min="1" :max="65535" style="width: 100%" />
  </el-form-item>
  <el-form-item>
    <el-button type="primary" :icon="Search" @click="submitDiscover" :loading="discoverSubmitting">开始扫描</el-button>
  </el-form-item>
</el-form>
```

- [ ] **Step 2: 在前端计算 CIDR 或验证 IP 范围**

```javascript
// 新增在 HostingView.vue 的 script setup 中
import { isValidIPv4, ipRangeToCidr } from '@/utils/ip'

const discoverForm = reactive({
  discoverMode: 'cidr',
  cidr: '',
  startIp: '',
  endIp: '',
  portFrom: 9082,
  portTo: 9082
})

function buildDiscoverPayload() {
  if (discoverForm.discoverMode === 'cidr') {
    return {
      cidr: discoverForm.cidr,
      portFrom: discoverForm.portFrom,
      portTo: discoverForm.portTo
    }
  }
  if (!isValidIPv4(discoverForm.startIp) || !isValidIPv4(discoverForm.endIp)) {
    throw new Error('起始 IP 或结束 IP 格式不正确')
  }
  const cidrs = ipRangeToCidr(discoverForm.startIp, discoverForm.endIp)
  if (!cidrs || cidrs.length === 0) {
    throw new Error('IP 范围无效')
  }
  if (cidrs.length > 1) {
    throw new Error('IP 范围跨多个 CIDR，请拆分或使用 CIDR 输入')
  }
  return {
    cidr: cidrs[0],
    portFrom: discoverForm.portFrom,
    portTo: discoverForm.portTo
  }
}

const submitDiscover = async () => {
  discoverSubmitting.value = true
  try {
    const payload = buildDiscoverPayload()
    await discoverApi.scan(payload)
    ElMessage.success('扫描任务已提交')
    loadTasks()
    startRefresh()
  } catch (e) {
    ElMessage.error(e.message || '提交失败')
  } finally {
    discoverSubmitting.value = false
  }
}
```

- [ ] **Step 3: 新增 `myxos-ui/src/utils/ip.js`**

```javascript
export function isValidIPv4(ip) {
  if (!ip) return false
  const parts = ip.split('.')
  if (parts.length !== 4) return false
  return parts.every(p => {
    const n = parseInt(p, 10)
    return p !== '' && !isNaN(n) && n >= 0 && n <= 255
  })
}

function ipToLong(ip) {
  return ip.split('.').reduce((acc, p) => (acc << 8) + parseInt(p, 10), 0) >>> 0
}

function longToIp(long) {
  return [(long >>> 24), (long >> 16) & 0xff, (long >> 8) & 0xff, long & 0xff].join('.')
}

export function ipRangeToCidr(startIp, endIp) {
  const start = ipToLong(startIp)
  const end = ipToLong(endIp)
  if (start > end) return []
  const cidrs = []
  let current = start
  while (current <= end) {
    let mask = 32
    while (mask > 0) {
      const bits = 32 - mask
      const rangeStart = (current >> bits) << bits
      const rangeEnd = rangeStart + (1 << bits) - 1
      if (rangeStart < current || rangeEnd > end) {
        mask++
        break
      }
      mask--
    }
    const finalMask = mask === 32 ? 32 : mask + 1
    const bits = 32 - finalMask
    const rangeStart = (current >> bits) << bits
    const rangeEnd = rangeStart + (1 << bits) - 1
    cidrs.push(longToIp(rangeStart) + '/' + finalMask)
    current = rangeEnd + 1
  }
  return cidrs
}
```

- [ ] **Step 4: Commit**

```bash
git add myxos-ui/src/views/HostingView.vue myxos-ui/src/utils/ip.js
git commit -m "feat(ui,hosting): 网段发现支持 CIDR 与 IP 范围两种输入"
```

---

### Task 4: 发现任务记录逐 IP 详情

**Files:**
- Modify: `myxos-domain/src/main/java/bob/myxos/domain/entity/DiscoverTask.java`
- Modify: `myxos-collector-service/src/main/java/bob/myxos/collector/collector/DeviceDiscoveryScanner.java`
- Modify: `myxos-main-service/src/main/java/bob/myxos/main/controller/DiscoverController.java`
- Modify: `myxos-main-service/src/main/java/bob/myxos/main/service/DiscoverService.java`
- Modify: `myxos-main-service/src/main/java/bob/myxos/main/service/impl/DiscoverServiceImpl.java`
- Modify: `myxos-ui/src/api/index.js`

- [ ] **Step 1: 在 `DiscoverTask` 实体新增 `detail` 字段**

```java
// myxos-domain/src/main/java/bob/myxos/domain/entity/DiscoverTask.java
/** 逐 IP 发现结果 JSON */
private String detail;
```

- [ ] **Step 2: 修改 `DeviceDiscoveryScanner` 收集逐 IP 结果**

注入 `ObjectMapper` 与 `ActionLogMapper`，扫描线程中将每 IP 结果写入线程安全列表，完成时序列化到 `detail`。

```java
// myxos-collector-service/src/main/java/bob/myxos/collector/collector/DeviceDiscoveryScanner.java
private final MytosClientFactory clientFactory;
private final DeviceMapper deviceMapper;
private final DiscoverTaskMapper discoverTaskMapper;
private final MetricPersistService metricPersistService;
private final ObjectMapper objectMapper;
private final ActionLogMapper actionLogMapper;

public void scan(DiscoverTask task) {
    // ... expandCidr ...
    List<DiscoveryIpResult> ipResults = Collections.synchronizedList(new ArrayList<>());
    AtomicInteger found = new AtomicInteger(0);
    AtomicInteger duplicate = new AtomicInteger(0);
    AtomicInteger scanned = new AtomicInteger(0);
    // ...
    executor.execute(() -> {
        try {
            MytosClient client = clientFactory.create(ip, p);
            HealthResp resp = client.healthcheck(ip);
            if (resp.getCode() != null && resp.getCode() == 200) {
                SaveResult saveResult = saveDiscoveredDevice(ip, p, resp);
                if (saveResult.added) {
                    found.incrementAndGet();
                    ipResults.add(new DiscoveryIpResult(ip, p, "ADDED", null));
                } else {
                    duplicate.incrementAndGet();
                    ipResults.add(new DiscoveryIpResult(ip, p, "DUPLICATE", saveResult.reason));
                }
            } else {
                ipResults.add(new DiscoveryIpResult(ip, p, "IGNORED", "健康检查未通过"));
            }
        } catch (Exception e) {
            ipResults.add(new DiscoveryIpResult(ip, p, "ERROR", e.getMessage()));
        } finally {
            scanned.incrementAndGet();
            latch.countDown();
        }
    });
    // ...
    markDone(taskId, found.get(), duplicate.get(), scanned.get(), total, completed, ipResults);
}

private SaveResult saveDiscoveredDevice(String ip, int port, HealthResp resp) {
    String name = ip + ":" + port;
    if (resp.getData() != null && resp.getData().getHostIp() != null) {
        name = resp.getData().getHostIp() + ":" + port;
    }
    Device device = new Device();
    device.setName(name);
    device.setIp(ip);
    device.setPort(port);
    device.setMode(DeviceMode.BRIDGE.name());
    device.setStatus(DeviceStatus.UNKNOWN.name());
    device.setSource("DISCOVERED");
    try {
        deviceMapper.insert(device);
        log.info("发现新设备：{}:{}, name={}", ip, port, name);
        writeActionLog(device, "网段发现添加设备：" + name + "(" + ip + ":" + port + ")");
        collectImmediately(device);
        return new SaveResult(true, null);
    } catch (DuplicateKeyException e) {
        log.debug("设备已存在，跳过：{}:{}", ip, port);
        return new SaveResult(false, "设备已存在");
    }
}

private void writeActionLog(Device device, String message) {
    ActionLog log = new ActionLog();
    log.setDeviceId(device.getId());
    log.setActionType("SYSTEM");
    log.setLogLevel("INFO");
    log.setMessage(message);
    log.setCreatedAt(LocalDateTime.now());
    actionLogMapper.insert(log);
}

private void markDone(Long taskId, int foundCount, int duplicateCount, int scannedCount, int total,
                      boolean completed, List<DiscoveryIpResult> ipResults) {
    DiscoverTask update = new DiscoverTask();
    update.setId(taskId);
    update.setFinishedAt(LocalDateTime.now());
    update.setFoundCount(foundCount);
    update.setScannedIpCount(scannedCount);
    try {
        DiscoverTaskDetail detail = new DiscoverTaskDetail();
        detail.setAddedCount(foundCount);
        detail.setDuplicateCount(duplicateCount);
        detail.setFailedCount(Math.max(0, total - scannedCount));
        detail.setIpResults(ipResults);
        update.setDetail(objectMapper.writeValueAsString(detail));
    } catch (Exception e) {
        log.warn("序列化发现详情失败：taskId={}", taskId, e);
    }
    if (completed) {
        update.setStatus("DONE");
        update.setMessage("扫描完成，新增 " + foundCount + " 台，重复 " + duplicateCount + " 台");
    } else {
        update.setStatus("TIMEOUT");
        update.setMessage(String.format("扫描超时，已扫描 %d / %d，新增 %d 台", scannedCount, total, foundCount));
    }
    discoverTaskMapper.updateById(update);
}

private static class SaveResult {
    final boolean added;
    final String reason;
    SaveResult(boolean added, String reason) {
        this.added = added;
        this.reason = reason;
    }
}

public static class DiscoveryIpResult {
    public String ip;
    public Integer port;
    public String result;
    public String message;
    public DiscoveryIpResult(String ip, Integer port, String result, String message) {
        this.ip = ip;
        this.port = port;
        this.result = result;
        this.message = message;
    }
}

public static class DiscoverTaskDetail {
    private int addedCount;
    private int duplicateCount;
    private int failedCount;
    private List<DiscoveryIpResult> ipResults;
    public int getAddedCount() { return addedCount; }
    public void setAddedCount(int addedCount) { this.addedCount = addedCount; }
    public int getDuplicateCount() { return duplicateCount; }
    public void setDuplicateCount(int duplicateCount) { this.duplicateCount = duplicateCount; }
    public int getFailedCount() { return failedCount; }
    public void setFailedCount(int failedCount) { this.failedCount = failedCount; }
    public List<DiscoveryIpResult> getIpResults() { return ipResults; }
    public void setIpResults(List<DiscoveryIpResult> ipResults) { this.ipResults = ipResults; }
}
```

- [ ] **Step 3: 新增发现任务详情后端接口**

```java
// myxos-main-service/src/main/java/bob/myxos/main/controller/DiscoverController.java
@GetMapping("/tasks/{id}")
public Result<DiscoverTask> detail(@PathVariable Long id) {
    return Result.ok(discoverService.getTaskDetail(id));
}
```

```java
// myxos-main-service/src/main/java/bob/myxos/main/service/DiscoverService.java
DiscoverTask getTaskDetail(Long id);
```

```java
// myxos-main-service/src/main/java/bob/myxos/main/service/impl/DiscoverServiceImpl.java
@Override
public DiscoverTask getTaskDetail(Long id) {
    DiscoverTask task = discoverTaskMapper.selectById(id);
    if (task == null || (task.getDeleted() != null && task.getDeleted() == 1)) {
        throw new BizException("发现任务不存在");
    }
    return task;
}
```

- [ ] **Step 4: 前端 API 新增详情方法**

```javascript
// myxos-ui/src/api/index.js
export const discoverApi = {
  scan: (data) => request.post('/discover/scan', data),
  tasks: (params) => request.get('/discover/tasks', { params }),
  taskDetail: (id) => request.get(`/discover/tasks/${id}`),
  deleteTask: (id) => request.delete(`/discover/tasks/${id}`),
  clearTasks: () => request.delete('/discover/tasks')
}
```

- [ ] **Step 5: Commit**

```bash
git add myxos-domain/src/main/java/bob/myxos/domain/entity/DiscoverTask.java myxos-collector-service/src/main/java/bob/myxos/collector/collector/DeviceDiscoveryScanner.java myxos-main-service/src/main/java/bob/myxos/main/controller/DiscoverController.java myxos-main-service/src/main/java/bob/myxos/main/service/DiscoverService.java myxos-main-service/src/main/java/bob/myxos/main/service/impl/DiscoverServiceImpl.java myxos-ui/src/api/index.js
git commit -m "feat(hosting): 发现任务记录逐 IP 详情并写入系统日志"
```

---

### Task 5: 前端 HostingView 表格列宽与详情抽屉

**Files:**
- Modify: `myxos-ui/src/views/HostingView.vue`

- [ ] **Step 1: 使用 `.page-header` 替换原有标题，并在右侧放置用户/登出**

```vue
<div class="page-header">
  <h2 class="page-title">设备托管</h2>
  <div class="page-actions">
    <span>{{ userStore.username }}</span>
    <el-button type="primary" link @click="logout">登出</el-button>
  </div>
</div>
```

并在 `script setup` 中引入 `useRouter`、`useUserStore`、`authApi`：

```javascript
import { useRouter } from 'vue-router'
import { useUserStore } from '@/store'
import { authApi } from '@/api'

const router = useRouter()
const userStore = useUserStore()

async function logout() {
  try {
    await authApi.logout()
  } finally {
    userStore.clearUser()
    router.push('/login')
  }
}
```

- [ ] **Step 2: 调整发现任务表格列宽并增加详情按钮**

```vue
<el-table-column prop="cidr" label="CIDR" min-width="140" show-overflow-tooltip />
<el-table-column prop="portFrom" label="起始端口" width="90" />
<el-table-column prop="portTo" label="结束端口" width="90" />
<el-table-column label="进度" width="180">
  <template #default="{ row }">
    <el-progress :percentage="progressPercent(row)" :stroke-width="12" />
    <div class="table-progress-text">{{ row.scannedIpCount }} / {{ row.totalIpCount }}</div>
  </template>
</el-table-column>
<el-table-column prop="status" label="状态" width="90">
  <template #default="{ row }">
    <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
  </template>
</el-table-column>
<el-table-column prop="foundCount" label="新增" width="80" />
<el-table-column prop="message" label="结果" min-width="200" show-overflow-tooltip />
<el-table-column prop="startedAt" label="开始时间" width="160">
  <template #default="{ row }">{{ formatDateTime(row.startedAt) }}</template>
</el-table-column>
<el-table-column prop="finishedAt" label="完成时间" width="160">
  <template #default="{ row }">{{ formatDateTime(row.finishedAt) }}</template>
</el-table-column>
<el-table-column label="操作" width="120" fixed="right">
  <template #default="{ row }">
    <el-button type="primary" link size="small" @click="openDetail(row)">详情</el-button>
    <el-button type="danger" link size="small" @click="remove(row.id)">删除</el-button>
  </template>
</el-table-column>
```

- [ ] **Step 3: 新增详情抽屉与数据加载**

```vue
<!-- 发现任务详情抽屉 -->
<el-drawer v-model="detailVisible" title="发现任务详情" size="600px">
  <el-descriptions v-if="currentTask" :column="2" border size="small">
    <el-descriptions-item label="CIDR">{{ currentTask.cidr }}</el-descriptions-item>
    <el-descriptions-item label="状态">
      <el-tag :type="statusType(currentTask.status)" size="small">{{ currentTask.status }}</el-tag>
    </el-descriptions-item>
    <el-descriptions-item label="新增数量">{{ taskDetail.addedCount || 0 }}</el-descriptions-item>
    <el-descriptions-item label="重复数量">{{ taskDetail.duplicateCount || 0 }}</el-descriptions-item>
    <el-descriptions-item label="失败数量">{{ taskDetail.failedCount || 0 }}</el-descriptions-item>
    <el-descriptions-item label="完成时间">{{ formatDateTime(currentTask.finishedAt) }}</el-descriptions-item>
  </el-descriptions>

  <el-divider content-position="left">逐 IP 结果</el-divider>
  <el-table :data="taskDetail.ipResults || []" size="small" stripe max-height="500">
    <el-table-column prop="ip" label="IP" width="140" />
    <el-table-column prop="port" label="端口" width="80" />
    <el-table-column prop="result" label="结果" width="100">
      <template #default="{ row }">
        <el-tag :type="resultTagType(row.result)" size="small">{{ row.result }}</el-tag>
      </template>
    </el-table-column>
    <el-table-column prop="message" label="说明" show-overflow-tooltip />
  </el-table>
</el-drawer>
```

```javascript
// script setup 中
import { formatDateTime } from '@/utils/date'

const detailVisible = ref(false)
const currentTask = ref(null)
const taskDetail = reactive({ addedCount: 0, duplicateCount: 0, failedCount: 0, ipResults: [] })

const openDetail = async (row) => {
  currentTask.value = row
  try {
    const res = await discoverApi.taskDetail(row.id)
    const task = res.data
    Object.assign(taskDetail, { addedCount: 0, duplicateCount: 0, failedCount: 0, ipResults: [] })
    if (task.detail) {
      try {
        const d = JSON.parse(task.detail)
        Object.assign(taskDetail, d)
      } catch (e) {
        console.error('解析详情失败', e)
      }
    }
    detailVisible.value = true
  } catch (e) {
    ElMessage.error('加载详情失败')
  }
}

const resultTagType = (result) => {
  switch (result) {
    case 'ADDED': return 'success'
    case 'DUPLICATE': return 'warning'
    case 'ERROR': return 'danger'
    default: return 'info'
  }
}
```

- [ ] **Step 4: Commit**

```bash
git add myxos-ui/src/views/HostingView.vue
git commit -m "feat(ui,hosting): 优化发现任务表格与详情抽屉"
```

---

## Self-Review

1. **Spec coverage:**
   - "手动添加接口报错" → Task 2 放宽 name 必填、捕获唯一键异常。
   - "分组为什么有两个默认分组" → Task 1 添加唯一索引并清理重复。
   - "网段发现只能通过网段输入方式应该对用户更友好" → Task 3 增加 IP 范围输入。
   - "发现任务表格中内容拥挤" → Task 5 调整列宽。
   - "发现任务应该加上详情，成功添加了哪些IP的设备，是否有重复的，重复的怎么处理的" → Task 4 新增 detail 字段、Task 5 前端抽屉展示。
   - "添加成功后为什么没有日志记录，扫描xxx网段/手动添加xxx 添加了xxx、xxx设备成功" → Task 2 与 Task 4 写入 ActionLog。

2. **Placeholder scan:** 无 TBD/TODO。

3. **Type consistency:** `DiscoverTask.detail` 为 String(JSON)；前后端字段名 `addedCount/duplicateCount/failedCount/ipResults` 一致。

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-08-04-hosting-page-improvements.md`.**

Two execution options:

1. **Subagent-Driven (recommended)** - dispatch a fresh subagent per task.
2. **Inline Execution** - execute tasks in this session using executing-plans.

**Dependency:** Implement `docs/superpowers/plans/2026-08-04-global-ui-basics.md` first for `formatDateTime` and `.page-header` styles.
