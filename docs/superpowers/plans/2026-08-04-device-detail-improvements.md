# 设备详情页面改进实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 正确判断安卓实例状态、区分主机与安卓实例实时指标、重构手动操作面板以明确主机/容器/实例三级操作、改进截图/剪贴板/IP 定位/adb 命令的交互体验，并统一时间格式。

**Architecture:** 后端 `DeviceServiceImpl.listAndroidInstances` 同时调用 `getAndroidDetail` 与 `getAndroidBootStatus` 综合判断状态，并返回更丰富的 `AndroidInstanceVO`；`DeviceController` 新增同步 `shell` 与 `clipboardGet` 接口供前端即时展示结果；前端 `DeviceDetailView.vue` 重构操作面板，新增主机指标与安卓实例状态两个独立区块，优化弹窗/抽屉比例与样式。

**Tech Stack:** Java 17, Spring Boot, MyBatis-Plus, Vue 3, Element Plus

---

## File Structure

| File | Responsibility |
|------|----------------|
| `myxos-main-service/src/main/java/bob/myxos/main/dto/AndroidInstanceVO.java` | 增加 `ip`、`image`、`statusDetail` 等字段 |
| `myxos-main-service/src/main/java/bob/myxos/main/service/impl/DeviceServiceImpl.java` | 综合判断安卓状态、新增 `executeShell`/`getClipboard` 服务方法 |
| `myxos-main-service/src/main/java/bob/myxos/main/controller/DeviceController.java` | 新增 `POST /devices/{id}/shell` 与 `GET /devices/{id}/clipboard` |
| `myxos-ui/src/api/index.js` | 新增 `deviceApi.shell` 与 `deviceApi.clipboardGet` |
| `myxos-ui/src/views/DeviceDetailView.vue` | 重构安卓实例、实时指标、手动操作面板与各类弹窗 |
| `myxos-ui/src/style.css` | 调整 metric-value 字体 |

---

### Task 1: 改进安卓实例状态判断

**Files:**
- Modify: `myxos-main-service/src/main/java/bob/myxos/main/dto/AndroidInstanceVO.java`
- Modify: `myxos-main-service/src/main/java/bob/myxos/main/service/impl/DeviceServiceImpl.java`

- [ ] **Step 1: 扩展 `AndroidInstanceVO`**

```java
// myxos-main-service/src/main/java/bob/myxos/main/dto/AndroidInstanceVO.java
@Data
public class AndroidInstanceVO {
    private String name;
    private String status;
    private String statusLabel;
    private String statusDetail;
    private String ip;
    private String image;
}
```

- [ ] **Step 2: 在 `DeviceServiceImpl` 中结合 `getAndroidDetail` 与 `getAndroidBootStatus` 判断状态**

```java
// myxos-main-service/src/main/java/bob/myxos/main/service/impl/DeviceServiceImpl.java
private AndroidInstanceVO buildAndroidInstanceVO(MytosClient client, String ip, String name) {
    AndroidInstanceVO vo = new AndroidInstanceVO();
    vo.setName(name);
    vo.setIp(ip);

    AndroidDetail detail = fetchAndroidDetail(client, ip, name);
    if (detail != null) {
        vo.setImage(detail.getImage());
    }

    String status = fetchAndroidStatus(client, ip, name, detail);
    vo.setStatus(status);
    vo.setStatusLabel(androidStatusLabel(status));
    vo.setStatusDetail(buildStatusDetail(detail));
    return vo;
}

private AndroidDetail fetchAndroidDetail(MytosClient client, String ip, String name) {
    try {
        AndroidDetailResp resp = client.getAndroidDetail(ip, name);
        if (resp != null && resp.getCode() != null && resp.getCode() == 200 && resp.getData() != null) {
            AndroidDetail detail = new AndroidDetail();
            JsonNode data = resp.getData();
            if (data.has("image")) detail.setImage(data.get("image").asText(null));
            if (data.has("status")) detail.setStatus(data.get("status").asText(null));
            if (data.has("ip")) detail.setIp(data.get("ip").asText(null));
            return detail;
        }
    } catch (Exception e) {
        log.debug("获取安卓实例详情失败：{} {}", ip, name, e);
    }
    return null;
}

private String fetchAndroidStatus(MytosClient client, String ip, String name, AndroidDetail detail) {
    if (detail != null && detail.getStatus() != null) {
        String s = detail.getStatus().trim().toLowerCase();
        if (s.contains("run") || s.contains("booted") || s.contains("online") || s.equals("true")) {
            return "RUNNING";
        }
        if (s.contains("stop") || s.contains("offline") || s.contains("down") || s.equals("false")) {
            return "STOPPED";
        }
    }
    try {
        BootStatusResp resp = client.getAndroidBootStatus(ip, name);
        if (resp == null || resp.getCode() == null || resp.getCode() != 200 || resp.getData() == null) {
            return "UNKNOWN";
        }
        String raw = resp.getData().isTextual() ? resp.getData().asText().trim().toLowerCase()
                : resp.getData().toString().toLowerCase();
        if (raw.contains("run") || raw.contains("booted") || raw.contains("online")) {
            return "RUNNING";
        }
        if (raw.contains("stop") || raw.contains("offline") || raw.contains("down")) {
            return "STOPPED";
        }
        return "UNKNOWN";
    } catch (Exception e) {
        return "UNKNOWN";
    }
}

private String buildStatusDetail(AndroidDetail detail) {
    if (detail == null) return null;
    List<String> parts = new ArrayList<>();
    if (detail.getIp() != null) parts.add("IP: " + detail.getIp());
    if (detail.getImage() != null) parts.add("镜像: " + detail.getImage());
    if (detail.getStatus() != null) parts.add("原始状态: " + detail.getStatus());
    return parts.isEmpty() ? null : String.join(" | ", parts);
}

@Data
private static class AndroidDetail {
    private String status;
    private String ip;
    private String image;
}
```

- [ ] **Step 3: Commit**

```bash
git add myxos-main-service/src/main/java/bob/myxos/main/dto/AndroidInstanceVO.java myxos-main-service/src/main/java/bob/myxos/main/service/impl/DeviceServiceImpl.java
git commit -m "feat(device): 综合详情与启动状态判断安卓实例状态"
```

---

### Task 2: 实时指标区分主机与安卓实例并优化样式

**Files:**
- Modify: `myxos-ui/src/views/DeviceDetailView.vue`
- Modify: `myxos-ui/src/style.css`

- [ ] **Step 1: 在 `DeviceDetailView.vue` 中将指标分为主机指标与安卓实例状态两个区块**

```vue
<!-- 实时指标 tab -->
<el-tab-pane label="实时指标" name="metrics">
  <div v-loading="metricsLoading">
    <h4 class="metric-section-title">主机指标</h4>
    <el-empty v-if="hostMetrics.length === 0" description="暂无主机指标" />
    <el-row :gutter="16" v-else>
      <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="item in hostMetrics" :key="item.metricType">
        <el-card class="metric-card" shadow="hover" @click="openMetricHistory(item)">
          <div class="metric-title">{{ metricLabel(item.metricType) }}</div>
          <div class="metric-value">{{ formatMetricValue(item) }}</div>
          <div class="metric-time">{{ formatDateTime(item.collectedAt) }}</div>
        </el-card>
      </el-col>
    </el-row>

    <h4 class="metric-section-title">安卓实例状态</h4>
    <el-empty v-if="androidMetrics.length === 0" description="暂无安卓实例状态" />
    <el-row :gutter="16" v-else>
      <el-col :xs="24" :sm="12" :md="8" :lg="6" v-for="item in androidMetrics" :key="item.metricType + item.extra">
        <el-card class="metric-card" shadow="hover" @click="openMetricHistory(item)">
          <div class="metric-title">{{ androidMetricTitle(item) }}</div>
          <div class="metric-value">{{ formatMetricValue(item) }}</div>
          <div class="metric-time">{{ formatDateTime(item.collectedAt) }}</div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</el-tab-pane>
```

- [ ] **Step 2: 添加计算属性与辅助函数**

```javascript
import { formatDateTime } from '@/utils/date'

const hostMetrics = computed(() => latestMetrics.filter(m => m.metricType !== 'ANDROID_STATUS'))
const androidMetrics = computed(() => latestMetrics.filter(m => m.metricType === 'ANDROID_STATUS'))

function androidMetricTitle(item) {
  try {
    const extra = JSON.parse(item.extra)
    return (extra.name || '未知容器') + ' 状态'
  } catch (e) {
    return '安卓实例状态'
  }
}
```

- [ ] **Step 3: 调整 `metric-value` 字体**

```css
/* myxos-ui/src/style.css */
.metric-value {
  font-size: 24px;
  font-weight: 500;
  color: var(--text-primary);
  margin-bottom: 8px;
  word-break: break-all;
  font-family: 'Helvetica Neue', Helvetica, 'PingFang SC', 'Hiragino Sans GB', 'Microsoft YaHei', Arial, sans-serif;
}

.metric-section-title {
  font-size: 16px;
  font-weight: 600;
  margin: 16px 0 12px;
  color: var(--text-primary);
}
```

- [ ] **Step 4: Commit**

```bash
git add myxos-ui/src/views/DeviceDetailView.vue myxos-ui/src/style.css
git commit -m "feat(ui,device): 实时指标区分主机与安卓实例并优化字体"
```

---

### Task 3: 重构手动操作面板

**Files:**
- Modify: `myxos-ui/src/views/DeviceDetailView.vue`

- [ ] **Step 1: 将操作区明确分为三级**

```vue
<!-- 手动操作 tab -->
<el-tab-pane label="手动操作" name="ops">
  <!-- 主机级 -->
  <el-card class="op-card" shadow="never">
    <template #header><div class="op-card-header">主机操作</div></template>
    <el-alert type="info" :closable="false" show-icon title="主机级操作会影响整台设备">
      以下操作针对当前设备（{{ device.ip }}:{{ device.port }}）本身执行。
    </el-alert>
    <el-button-group class="op-buttons">
      <el-button type="danger" plain @click="submitOp('REBOOT_HOST')">重启主机</el-button>
    </el-button-group>
  </el-card>

  <!-- 安卓容器级 -->
  <el-card class="op-card" shadow="never">
    <template #header><div class="op-card-header">安卓容器操作</div></template>
    <el-alert type="info" :closable="false" show-icon title="容器级操作会改变容器的运行状态">
      请选择下方容器，然后执行启动、停止、重启、重置或重命名。
    </el-alert>
    <el-form inline>
      <el-form-item label="容器名称">
        <el-select v-model="instanceName" placeholder="请选择容器" filterable style="width: 220px">
          <el-option v-for="item in androids" :key="item.name" :label="item.name" :value="item.name" />
        </el-select>
      </el-form-item>
      <el-form-item label="新名称" v-if="showRename">
        <el-input v-model="newInstanceName" placeholder="重命名时填写" style="width: 200px;" />
      </el-form-item>
    </el-form>
    <el-button-group class="op-buttons">
      <el-button @click="submitAndroidOp('RUN_ANDROID')">启动容器</el-button>
      <el-button @click="submitAndroidOp('STOP_ANDROID')">停止容器</el-button>
      <el-button @click="submitAndroidOp('REBOOT_ANDROID')">重启容器</el-button>
      <el-button type="warning" plain @click="submitAndroidOp('RESET_ANDROID')">重置容器</el-button>
      <el-button v-if="!showRename" @click="showRename = true">重命名</el-button>
      <el-button v-else type="primary" @click="submitAndroidOp('RENAME_ANDROID')">确认重命名</el-button>
    </el-button-group>
  </el-card>

  <!-- 安卓实例级 -->
  <el-card class="op-card" shadow="never">
    <template #header><div class="op-card-header">安卓实例操作</div></template>
    <el-alert type="info" :closable="false" show-icon title="实例级操作针对运行中的安卓系统">
      请选择容器后执行截图、剪贴板、语言设置、IP 定位或 Adb 命令。
    </el-alert>
    <el-form inline>
      <el-form-item label="容器名称">
        <el-select v-model="instanceName" placeholder="请选择容器" filterable style="width: 220px">
          <el-option v-for="item in androids" :key="item.name" :label="item.name" :value="item.name" />
        </el-select>
      </el-form-item>
    </el-form>
    <el-button-group class="op-buttons">
      <el-button @click="submitScreenshot">截图（临时查看）</el-button>
      <el-button @click="openDialog('clipboard')">设置剪贴板</el-button>
      <el-button @click="submitClipboardGet">获取剪贴板</el-button>
      <el-button @click="openDialog('language')">设置语言</el-button>
      <el-button @click="openDialog('location')">IP 智能定位</el-button>
      <el-button @click="openDialog('shell')">执行 Adb 命令</el-button>
    </el-button-group>
  </el-card>

  <!-- 截图临时预览 -->
  <el-dialog v-model="screenshotVisible" title="设备截图" width="fit-content" align-center destroy-on-close>
    <img v-if="screenshotData" :src="screenshotData" style="max-width: 80vw; max-height: 80vh; display: block;" />
    <span v-else>暂无截图数据</span>
  </el-dialog>

  <!-- 参数/结果对话框 -->
  <el-dialog v-model="dialogVisible" :title="dialogTitle" width="560px" align-center destroy-on-close>
    <el-form v-if="dialogType === 'clipboard'">
      <el-form-item label="文本内容">
        <el-input v-model="dialogForm.text" type="textarea" :rows="3" />
      </el-form-item>
    </el-form>
    <el-form v-if="dialogType === 'language'">
      <el-form-item label="国家">
        <el-input v-model="dialogForm.country" placeholder="如 cn" />
      </el-form-item>
      <el-form-item label="语言">
        <el-input v-model="dialogForm.language" placeholder="如 zh" />
      </el-form-item>
    </el-form>
    <el-form v-if="dialogType === 'location'">
      <el-form-item label="语言">
        <el-input v-model="dialogForm.language" placeholder="如 zh" />
      </el-form-item>
    </el-form>
    <el-form v-if="dialogType === 'shell'">
      <el-form-item label="Adb 命令">
        <el-input v-model="dialogForm.command" type="textarea" :rows="3" placeholder="例如：pm list packages" />
      </el-form-item>
      <el-form-item v-if="dialogResult" label="执行结果">
        <pre class="shell-result">{{ dialogResult }}</pre>
      </el-form-item>
    </el-form>
    <template #footer>
      <el-button @click="dialogVisible = false">取消</el-button>
      <el-button type="primary" @click="confirmDialog" :loading="dialogLoading">确定</el-button>
    </template>
  </el-dialog>

  <!-- 剪贴板内容展示 -->
  <el-dialog v-model="clipboardVisible" title="剪贴板内容" width="480px" align-center destroy-on-close>
    <el-input v-model="clipboardData" type="textarea" :rows="6" readonly />
  </el-dialog>
</el-tab-pane>
```

- [ ] **Step 2: 修改 `script setup` 中的相关逻辑**

```javascript
const dialogLoading = ref(false)
const dialogResult = ref('')
const clipboardVisible = ref(false)
const clipboardData = ref('')

function openDialog(type) {
  if (!instanceName.value) {
    ElMessage.warning('请先选择容器名称')
    return
  }
  dialogType.value = type
  dialogResult.value = ''
  if (type === 'clipboard') {
    dialogTitle.value = '设置剪贴板'
  } else if (type === 'language') {
    dialogTitle.value = '设置系统语言'
  } else if (type === 'location') {
    dialogTitle.value = 'IP 智能定位'
  } else if (type === 'shell') {
    dialogTitle.value = '执行 Adb 命令'
  }
  dialogVisible.value = true
}

async function confirmDialog() {
  const params = { name: instanceName.value }
  if (dialogType.value === 'clipboard') {
    params.text = dialogForm.text
    await submitOp('SET_CLIPBOARD', params)
    dialogVisible.value = false
  } else if (dialogType.value === 'language') {
    params.country = dialogForm.country
    params.language = dialogForm.language
    await submitOp('SET_LANGUAGE', params)
    dialogVisible.value = false
  } else if (dialogType.value === 'location') {
    if (!dialogForm.language) {
      ElMessage.warning('请输入语言参数')
      return
    }
    params.language = dialogForm.language
    await submitOp('REFRESH_LOCATION', params)
    dialogVisible.value = false
  } else if (dialogType.value === 'shell') {
    if (!dialogForm.command) {
      ElMessage.warning('请输入 Adb 命令')
      return
    }
    dialogLoading.value = true
    try {
      const res = await deviceApi.shell(deviceId, {
        name: instanceName.value,
        command: dialogForm.command
      })
      dialogResult.value = res.data || '执行成功，无返回'
      ElMessage.success('命令执行成功')
    } catch (e) {
      dialogResult.value = '执行失败：' + (e.message || '未知错误')
      ElMessage.error('命令执行失败')
    } finally {
      dialogLoading.value = false
    }
  }
}

async function submitClipboardGet() {
  if (!instanceName.value) {
    ElMessage.warning('请先选择容器名称')
    return
  }
  try {
    const res = await deviceApi.clipboardGet(deviceId, { name: instanceName.value })
    clipboardData.value = res.data || '（空）'
    clipboardVisible.value = true
  } catch (e) {
    ElMessage.error('获取剪贴板失败：' + (e.message || '未知错误'))
  }
}
```

- [ ] **Step 3: 增加样式**

```css
/* 追加到 DeviceDetailView.vue 的 style scoped */
.op-card {
  margin-bottom: var(--spacing-md);
}
.op-card-header {
  font-weight: 600;
}
.op-buttons {
  margin-top: var(--spacing-sm);
}
.shell-result {
  background-color: #f5f7fa;
  padding: var(--spacing-sm);
  border-radius: var(--border-radius);
  max-height: 300px;
  overflow: auto;
  white-space: pre-wrap;
  word-break: break-all;
  font-family: 'Courier New', monospace;
  font-size: 13px;
}
```

- [ ] **Step 4: Commit**

```bash
git add myxos-ui/src/views/DeviceDetailView.vue myxos-ui/src/style.css
git commit -m "feat(ui,device): 重构手动操作面板，明确主机/容器/实例三级"
```

---

### Task 4: 后端新增同步 Shell 与剪贴板获取接口

**Files:**
- Modify: `myxos-main-service/src/main/java/bob/myxos/main/service/DeviceService.java`
- Modify: `myxos-main-service/src/main/java/bob/myxos/main/service/impl/DeviceServiceImpl.java`
- Modify: `myxos-main-service/src/main/java/bob/myxos/main/controller/DeviceController.java`
- Modify: `myxos-ui/src/api/index.js`

- [ ] **Step 1: 服务层新增方法**

```java
// myxos-main-service/src/main/java/bob/myxos/main/service/DeviceService.java
String executeShell(Long id, String name, String command);
String getClipboard(Long id, String name);
```

```java
// myxos-main-service/src/main/java/bob/myxos/main/service/impl/DeviceServiceImpl.java
@Override
public String executeShell(Long id, String name, String command) {
    Device device = getDetail(id);
    MytosClient client = clientFactory.create(device.getIp(), device.getPort());
    ShellResp resp = client.shell(device.getIp(), name, command);
    if (resp == null || resp.getData() == null) {
        return "";
    }
    JsonNode data = resp.getData();
    if (data.has("output")) {
        return data.get("output").asText("");
    }
    if (data.has("result")) {
        return data.get("result").asText("");
    }
    return data.toString();
}

@Override
public String getClipboard(Long id, String name) {
    Device device = getDetail(id);
    MytosClient client = clientFactory.create(device.getIp(), device.getPort());
    ClipboardResp resp = client.clipboardGet(device.getIp(), name);
    if (resp == null || resp.getData() == null) {
        return "";
    }
    JsonNode data = resp.getData();
    if (data.isTextual()) {
        return data.asText();
    }
    if (data.has("text")) {
        return data.get("text").asText("");
    }
    return data.toString();
}
```

- [ ] **Step 2: Controller 新增端点**

```java
// myxos-main-service/src/main/java/bob/myxos/main/controller/DeviceController.java
@PostMapping("/{id}/shell")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
public Result<String> shell(@PathVariable Long id,
                               @RequestParam @NotBlank(message = "容器名称不能为空")
                               @Pattern(regexp = "^[A-Za-z0-9_.-]{1,64}$", message = "容器名称包含非法字符")
                               String name,
                               @RequestBody @NotBlank(message = "命令不能为空") String command) {
    return Result.ok(deviceService.executeShell(id, name, command));
}

@GetMapping("/{id}/clipboard")
@PreAuthorize("hasAnyRole('ADMIN','OPERATOR')")
public Result<String> clipboard(@PathVariable Long id,
                                   @RequestParam @NotBlank(message = "容器名称不能为空")
                                   @Pattern(regexp = "^[A-Za-z0-9_.-]{1,64}$", message = "容器名称包含非法字符")
                                   String name) {
    return Result.ok(deviceService.getClipboard(id, name));
}
```

- [ ] **Step 3: 前端 API 注册**

```javascript
// myxos-ui/src/api/index.js
export const deviceApi = {
  // ... 现有方法 ...
  shell: (id, data) => request.post(`/devices/${id}/shell`, data.command, { params: { name: data.name } }),
  clipboardGet: (id, params) => request.get(`/devices/${id}/clipboard`, { params })
}
```

- [ ] **Step 4: Commit**

```bash
git add myxos-main-service/src/main/java/bob/myxos/main/service/DeviceService.java myxos-main-service/src/main/java/bob/myxos/main/service/impl/DeviceServiceImpl.java myxos-main-service/src/main/java/bob/myxos/main/controller/DeviceController.java myxos-ui/src/api/index.js
git commit -m "feat(device): 新增同步 shell 执行与剪贴板获取接口"
```

---

### Task 5: 统一设备详情页时间格式与页面标题栏

**Files:**
- Modify: `myxos-ui/src/views/DeviceDetailView.vue`

- [ ] **Step 1: 页面标题栏使用 `.page-header`**

```vue
<div class="page-header">
  <h2 class="page-title">设备详情</h2>
  <div class="page-actions">
    <span>{{ userStore.username }}</span>
    <el-button type="primary" link @click="logout">登出</el-button>
  </div>
</div>
```

并引入 `useRouter`、`useUserStore`、`authApi` 和 `logout` 函数（同 HostingView）。

- [ ] **Step 2: 所有时间字段使用 `formatDateTime`**

```vue
<el-descriptions-item label="最后在线">{{ formatDateTime(device.lastSeenAt) }}</el-descriptions-item>
```

表格中的 `firedAt`、`createdAt`、`finishedAt`、`collectedAt` 等统一替换。

- [ ] **Step 3: Commit**

```bash
git add myxos-ui/src/views/DeviceDetailView.vue
git commit -m "feat(ui,device): 设备详情页标题栏与时间格式统一"
```

---

## Self-Review

1. **Spec coverage:**
   - "安卓实例中各个实例的状态为未知" → Task 1 综合 detail 与 boot_status 判断。
   - "实时指标中，主机的状态信息应该与安卓实例的状态信息区分开" → Task 2 分区块显示。
   - "采集记录中的时间使用YYYY-MM-DD HH:mm:ss" → Task 2/5 使用 `formatDateTime`。
   - "实时指标具体值的字体也太丑了" → Task 2 调整 metric-value 字体。
   - "手动操作中，容器生命周期意义不明确" → Task 3 明确主机/容器/实例三级。
   - "安卓容器也应该有启动、停止、重启操作" → Task 3 已提供。
   - "截图弹出窗体比例不对" → Task 3 使用 fit-content 与 max-width/max-height。
   - "IP智能定位：重试耗尽：缺少必需参数: language" → Task 3 改为弹窗输入 language 后提交。
   - "获取剪贴板：应该弹出一个窗体，将剪贴板的内容临时显示出来" → Task 3 新增 `clipboardVisible` 弹窗 + Task 4 同步接口。
   - "执行adb命令：给出命令示例，执行结果要同步显示回来" → Task 3 命令示例 + 同步结果展示 + Task 4 同步接口。

2. **Placeholder scan:** 无 TBD/TODO。

3. **Type consistency:** 前后端 `name/language/country/command` 参数名一致；`AndroidInstanceVO` 新增字段同步。

---

## Execution Handoff

**Plan complete and saved to `docs/superpowers/plans/2026-08-04-device-detail-improvements.md`.**

Two execution options:

1. **Subagent-Driven (recommended)** - dispatch a fresh subagent per task.
2. **Inline Execution** - execute tasks in this session using executing-plans.

**Dependency:** Implement `docs/superpowers/plans/2026-08-04-global-ui-basics.md` first.
