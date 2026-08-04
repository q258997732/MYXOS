# 任务队列与日志改进实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 优化任务队列与日志页面的表格列宽、补齐任务重试/详情后端、在操作任务执行时写入详细日志，并在前端展示完整结果与详情。

**Architecture:** 后端 `OpTaskController` 新增详情与重试端点，`OpTaskRunnerFactory` 在任务执行成功/失败时写入 `ActionLog` 并丰富 `resultMsg`；前端 `OpTaskListView.vue` 与 `LogListView.vue` 调整列宽、新增详情抽屉、统一时间格式并适配全局 `.page-header` 标题栏。

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, Vue 3, Element Plus

---

## File Structure

| File | Responsibility |
|------|----------------|
| `myxos-domain/src/main/java/bob/myxos/domain/entity/OpTask.java` | 可保留现有字段，本计划通过 `resultMsg` 与 `ActionLog` 记录详情 |
| `myxos-collector-service/src/main/java/bob/myxos/collector/execute/OpTaskRunnerFactory.java` | 写入 ActionLog、捕获并记录设备返回结果 |
| `myxos-main-service/src/main/java/bob/myxos/main/controller/OpTaskController.java` | 新增 `GET /api/op-tasks/{id}` 与 `POST /api/op-tasks/{id}/retry` |
| `myxos-main-service/src/main/java/bob/myxos/main/service/OpTaskService.java` | 新增 `getById` 与 `retry` |
| `myxos-main-service/src/main/java/bob/myxos/main/service/impl/OpTaskServiceImpl.java` | 实现详情与重试 |
| `myxos-ui/src/api/index.js` | 确认/新增 `opTaskApi.detail` |
| `myxos-ui/src/views/OpTaskListView.vue` | 列宽优化、详情抽屉、重试按钮、时间格式化、标题栏 |
| `myxos-ui/src/views/LogListView.vue` | 列宽优化、详情抽屉、时间格式化、标题栏 |

---

### Task 1: 优化任务队列表格列宽与标题栏

**Files:**
- Modify: `myxos-ui/src/views/OpTaskListView.vue`

- [ ] **Step 1: 标题栏替换为 `.page-header`**

```vue
<div class="page-header">
  <h2 class="page-title">任务队列</h2>
  <div class="page-actions">
    <span>{{ userStore.username }}</span>
    <el-button type="primary" link @click="logout">登出</el-button>
  </div>
</div>
```

并引入 `useRouter`、`useUserStore`、`authApi` 与 `logout`。

- [ ] **Step 2: 调整表格列宽并新增详情按钮**

```vue
<el-table-column prop="id" label="任务ID" width="90" />
<el-table-column prop="deviceId" label="设备ID" width="90" />
<el-table-column prop="operationCode" label="操作" width="150" show-overflow-tooltip />
<el-table-column prop="source" label="来源" width="90" />
<el-table-column prop="status" label="状态" width="90">
  <template #default="{ row }">
    <el-tag :type="statusType(row.status)" size="small">{{ row.status }}</el-tag>
  </template>
</el-table-column>
<el-table-column prop="scheduledAt" label="计划时间" width="160">
  <template #default="{ row }">{{ formatDateTime(row.scheduledAt) }}</template>
</el-table-column>
<el-table-column prop="finishedAt" label="完成时间" width="160">
  <template #default="{ row }">{{ formatDateTime(row.finishedAt) }}</template>
</el-table-column>
<el-table-column prop="resultMsg" label="结果" min-width="200" show-overflow-tooltip />
<el-table-column label="操作" width="140" fixed="right">
  <template #default="{ row }">
    <el-button size="small" link @click="openDetail(row)">详情</el-button>
    <el-button v-if="row.status === 'FAILED'" size="small" :icon="RefreshRight" @click="retry(row.id)">重试</el-button>
  </template>
</el-table-column>
```

- [ ] **Step 3: 新增详情抽屉与时间格式化**

```javascript
import { formatDateTime } from '@/utils/date'

const detailVisible = ref(false)
const currentTask = ref(null)

const openDetail = async (row) => {
  try {
    const res = await opTaskApi.detail(row.id)
    currentTask.value = res.data
    detailVisible.value = true
  } catch (e) {
    ElMessage.error('加载详情失败')
  }
}
```

```vue
<el-drawer v-model="detailVisible" title="任务详情" size="560px">
  <el-descriptions v-if="currentTask" :column="1" border size="small">
    <el-descriptions-item label="任务ID">{{ currentTask.id }}</el-descriptions-item>
    <el-descriptions-item label="设备ID">{{ currentTask.deviceId }}</el-descriptions-item>
    <el-descriptions-item label="操作">{{ currentTask.operationCode }}</el-descriptions-item>
    <el-descriptions-item label="参数">
      <pre class="json-preview">{{ formatJson(currentTask.params) }}</pre>
    </el-descriptions-item>
    <el-descriptions-item label="状态">
      <el-tag :type="statusType(currentTask.status)" size="small">{{ currentTask.status }}</el-tag>
    </el-descriptions-item>
    <el-descriptions-item label="重试次数">{{ currentTask.retryCount }} / {{ currentTask.maxRetry }}</el-descriptions-item>
    <el-descriptions-item label="计划时间">{{ formatDateTime(currentTask.scheduledAt) }}</el-descriptions-item>
    <el-descriptions-item label="开始时间">{{ formatDateTime(currentTask.startedAt) }}</el-descriptions-item>
    <el-descriptions-item label="完成时间">{{ formatDateTime(currentTask.finishedAt) }}</el-descriptions-item>
    <el-descriptions-item label="执行结果">
      <pre class="json-preview">{{ currentTask.resultMsg }}</pre>
    </el-descriptions-item>
  </el-descriptions>
</el-drawer>
```

```javascript
function formatJson(json) {
  if (!json) return '-'
  try {
    return JSON.stringify(JSON.parse(json), null, 2)
  } catch (e) {
    return json
  }
}
```

- [ ] **Step 4: Commit**

```bash
git add myxos-ui/src/views/OpTaskListView.vue
git commit -m "feat(ui,op-task): 任务队列表格列宽、详情抽屉与标题栏"
```

---

### Task 2: 实现任务详情与重试后端

**Files:**
- Modify: `myxos-main-service/src/main/java/bob/myxos/main/controller/OpTaskController.java`
- Modify: `myxos-main-service/src/main/java/bob/myxos/main/service/OpTaskService.java`
- Modify: `myxos-main-service/src/main/java/bob/myxos/main/service/impl/OpTaskServiceImpl.java`
- Modify: `myxos-ui/src/api/index.js`

- [ ] **Step 1: 服务层实现详情与重试**

```java
// myxos-main-service/src/main/java/bob/myxos/main/service/OpTaskService.java
OpTask getById(Long id);
void retry(Long id);
```

```java
// myxos-main-service/src/main/java/bob/myxos/main/service/impl/OpTaskServiceImpl.java
@Override
public OpTask getById(Long id) {
    OpTask task = opTaskMapper.selectById(id);
    if (task == null || (task.getDeleted() != null && task.getDeleted() == 1)) {
        throw new BizException("任务不存在");
    }
    return task;
}

@Override
@Transactional(rollbackFor = Exception.class)
public void retry(Long id) {
    OpTask task = getById(id);
    if (!"FAILED".equals(task.getStatus()) && !"SUCCESS".equals(task.getStatus())) {
        throw new BizException("只有失败或成功状态的任务可以重试");
    }
    OpTask update = new OpTask();
    update.setId(id);
    update.setStatus("PENDING");
    update.setRetryCount(0);
    update.setResultMsg(null);
    update.setScheduledAt(LocalDateTime.now());
    update.setStartedAt(null);
    update.setFinishedAt(null);
    opTaskMapper.updateById(update);
}
```

- [ ] **Step 2: Controller 新增端点**

```java
// myxos-main-service/src/main/java/bob/myxos/main/controller/OpTaskController.java
@GetMapping("/{id}")
public Result<OpTask> detail(@PathVariable Long id) {
    return Result.ok(opTaskService.getById(id));
}

@PostMapping("/{id}/retry")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
public Result<Void> retry(@PathVariable Long id) {
    opTaskService.retry(id);
    return Result.ok();
}
```

- [ ] **Step 3: 前端 API 确认详情方法**

```javascript
// myxos-ui/src/api/index.js
export const opTaskApi = {
  list: (params) => request.get('/op-tasks', { params }),
  detail: (id) => request.get(`/op-tasks/${id}`),
  retry: (id) => request.post(`/op-tasks/${id}/retry`)
}
```

- [ ] **Step 4: Commit**

```bash
git add myxos-main-service/src/main/java/bob/myxos/main/controller/OpTaskController.java myxos-main-service/src/main/java/bob/myxos/main/service/OpTaskService.java myxos-main-service/src/main/java/bob/myxos/main/service/impl/OpTaskServiceImpl.java myxos-ui/src/api/index.js
git commit -m "feat(op-task): 新增任务详情与重试接口"
```

---

### Task 3: 操作任务执行时写入详细日志

**Files:**
- Modify: `myxos-collector-service/src/main/java/bob/myxos/collector/execute/OpTaskRunnerFactory.java`

- [ ] **Step 1: 注入 `ActionLogMapper` 并记录日志**

```java
// myxos-collector-service/src/main/java/bob/myxos/collector/execute/OpTaskRunnerFactory.java
private final OpTaskMapper opTaskMapper;
private final DeviceMapper deviceMapper;
private final MytosClientFactory clientFactory;
private final ObjectMapper objectMapper;
private final ActionLogMapper actionLogMapper;
```

- [ ] **Step 2: 在 `runTask` 与 `handleRetry` 中写入 ActionLog**

```java
private void runTask(OpTask task) {
    if (task == null || task.getId() == null) {
        return;
    }
    long startMs = System.currentTimeMillis();
    try {
        Device device = getDevice(task.getDeviceId());
        MytosClient client = clientFactory.create(device.getIp(), device.getPort());
        OperationCode code = parseOperationCode(task.getOperationCode());
        Map<String, Object> params = parseParams(task.getParams());

        MytosBaseResp resp = client.execute(code, params);
        String resultMsg = buildSuccessResultMsg(resp);

        task.setStatus(OpTaskStatus.SUCCESS.name());
        task.setResultMsg(resultMsg);
        task.setFinishedAt(LocalDateTime.now());
        writeActionLog(task, "INFO", "操作成功：" + code + "，" + resultMsg);
        log.info("操作任务执行成功：taskId={}, deviceId={}, op={}", task.getId(), task.getDeviceId(), code);
    } catch (Exception e) {
        log.error("操作任务执行失败：taskId={}", task.getId(), e);
        handleRetry(task, e);
    } finally {
        try {
            opTaskMapper.updateById(task);
        } catch (Exception ex) {
            log.error("更新操作任务失败：taskId={}", task.getId(), ex);
        }
    }
}

private String buildSuccessResultMsg(MytosBaseResp resp) {
    if (resp == null) {
        return "执行成功";
    }
    StringBuilder sb = new StringBuilder();
    sb.append("设备返回码：").append(resp.getCode());
    if (resp.getMsg() != null) {
        sb.append("，消息：").append(resp.getMsg());
    }
    if (resp.getData() != null) {
        try {
            String dataJson = objectMapper.writeValueAsString(resp.getData());
            if (dataJson.length() > 200) {
                dataJson = dataJson.substring(0, 200) + "...";
            }
            sb.append("，数据：").append(dataJson);
        } catch (Exception ignored) {
        }
    }
    return sb.toString();
}

private void handleRetry(OpTask task, Exception e) {
    if (e instanceof BizException) {
        task.setStatus(OpTaskStatus.FAILED.name());
        task.setResultMsg(e.getMessage());
        task.setFinishedAt(LocalDateTime.now());
        writeActionLog(task, "ERROR", "操作失败：" + task.getOperationCode() + "，" + e.getMessage());
        return;
    }

    int retryCount = task.getRetryCount() == null ? 0 : task.getRetryCount();
    int maxRetry = task.getMaxRetry() == null ? 0 : task.getMaxRetry();
    if (retryCount >= maxRetry) {
        task.setStatus(OpTaskStatus.FAILED.name());
        task.setResultMsg("重试耗尽：" + e.getMessage());
        task.setFinishedAt(LocalDateTime.now());
        writeActionLog(task, "ERROR", "重试耗尽：" + task.getOperationCode() + "，" + e.getMessage());
    } else {
        int newRetry = retryCount + 1;
        task.setRetryCount(newRetry);
        task.setStatus(OpTaskStatus.PENDING.name());
        task.setScheduledAt(LocalDateTime.now().plusSeconds(RETRY_DELAY_BASE_SEC * newRetry));
        task.setResultMsg("等待第 " + newRetry + " 次重试：" + e.getMessage());
        writeActionLog(task, "WARN", "第 " + newRetry + " 次重试等待：" + task.getOperationCode() + "，" + e.getMessage());
    }
}

private void writeActionLog(OpTask task, String level, String message) {
    try {
        ActionLog log = new ActionLog();
        log.setTaskId(task.getId());
        log.setDeviceId(task.getDeviceId());
        log.setActionType("OPERATION");
        log.setLogLevel(level);
        log.setMessage(message);
        log.setCreatedAt(LocalDateTime.now());
        actionLogMapper.insert(log);
    } catch (Exception ex) {
        log.warn("写入操作日志失败：taskId={}", task.getId(), ex);
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add myxos-collector-service/src/main/java/bob/myxos/collector/execute/OpTaskRunnerFactory.java
git commit -m "feat(collector): 操作任务执行写入详细日志并丰富结果"
```

---

### Task 4: 优化日志查询页面

**Files:**
- Modify: `myxos-ui/src/views/LogListView.vue`

- [ ] **Step 1: 标题栏替换为 `.page-header`**

```vue
<div class="page-header">
  <h2 class="page-title">日志查询</h2>
  <div class="page-actions">
    <span>{{ userStore.username }}</span>
    <el-button type="primary" link @click="logout">登出</el-button>
  </div>
</div>
```

- [ ] **Step 2: 调整表格列宽并新增详情按钮**

```vue
<el-table-column prop="id" label="日志ID" width="90" />
<el-table-column prop="deviceId" label="设备ID" width="90" />
<el-table-column prop="taskId" label="任务ID" width="90" />
<el-table-column prop="actionType" label="动作类型" width="100" />
<el-table-column prop="logLevel" label="日志级别" width="90">
  <template #default="{ row }">
    <el-tag :type="logLevelType(row.logLevel)" size="small">{{ row.logLevel }}</el-tag>
  </template>
</el-table-column>
<el-table-column prop="message" label="消息" min-width="260" show-overflow-tooltip />
<el-table-column prop="createdAt" label="时间" width="160">
  <template #default="{ row }">{{ formatDateTime(row.createdAt) }}</template>
</el-table-column>
<el-table-column label="操作" width="90" fixed="right">
  <template #default="{ row }">
    <el-button size="small" link @click="openDetail(row)">详情</el-button>
  </template>
</el-table-column>
```

- [ ] **Step 3: 新增详情弹窗**

```javascript
const detailVisible = ref(false)
const currentLog = ref(null)

const openDetail = (row) => {
  currentLog.value = row
  detailVisible.value = true
}
```

```vue
<el-dialog v-model="detailVisible" title="日志详情" width="560px" align-center destroy-on-close>
  <el-descriptions v-if="currentLog" :column="1" border size="small">
    <el-descriptions-item label="日志ID">{{ currentLog.id }}</el-descriptions-item>
    <el-descriptions-item label="设备ID">{{ currentLog.deviceId }}</el-descriptions-item>
    <el-descriptions-item label="任务ID">{{ currentLog.taskId }}</el-descriptions-item>
    <el-descriptions-item label="动作类型">{{ currentLog.actionType }}</el-descriptions-item>
    <el-descriptions-item label="日志级别">
      <el-tag :type="logLevelType(currentLog.logLevel)" size="small">{{ currentLog.logLevel }}</el-tag>
    </el-descriptions-item>
    <el-descriptions-item label="时间">{{ formatDateTime(currentLog.createdAt) }}</el-descriptions-item>
    <el-descriptions-item label="详细内容">
      <pre class="log-detail">{{ currentLog.message }}</pre>
    </el-descriptions-item>
  </el-descriptions>
</el-dialog>
```

```css
/* 追加到 LogListView.vue 的 style scoped */
.log-detail {
  white-space: pre-wrap;
  word-break: break-all;
  max-height: 400px;
  overflow: auto;
  font-family: 'Courier New', monospace;
  font-size: 13px;
  background-color: #f5f7fa;
  padding: var(--spacing-sm);
  border-radius: var(--border-radius);
}
```

- [ ] **Step 4: Commit**

```bash
git add myxos-ui/src/views/LogListView.vue
git commit -m "feat(ui,log): 日志查询页面列宽、详情弹窗与标题栏"
```

---

## Self-Review

1. **Spec coverage:**
   - "任务队列：表格显示宽度不合理，很多换行了" → Task 1 调整列宽。
   - "所有日志/任务记录显示应该包含详细内容/执行结果" → Task 1/4 详情抽屉 + Task 3 丰富 resultMsg + ActionLog。
   - "所有的前端显示时间都采用YYYY-MM-DD HH:mm:ss" → Task 1/4 使用 `formatDateTime`。
   - "每个页面右侧内容区域上面都白了一块很难看" → Task 1/4 标题栏 + 全局 Layout 已改。

2. **Placeholder scan:** 无 TBD/TODO。

3. **Type consistency:** 前后端 OpTask 字段一致；ActionLog `actionType=OPERATION` 与前端过滤器一致。

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-08-04-task-queue-and-logs.md`.**

Two execution options:

1. **Subagent-Driven (recommended)** - dispatch a fresh subagent per task.
2. **Inline Execution** - execute tasks in this session using executing-plans.

**Dependency:** Implement `docs/superpowers/plans/2026-08-04-global-ui-basics.md` first.
