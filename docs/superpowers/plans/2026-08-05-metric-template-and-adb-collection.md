

> **给代理工作者：** 必须使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 逐任务实施。本计划步骤使用 `- [ ]` 清单语法跟踪。

**目标：** 通过受控指标目录、可复用模板和目标级绑定，实现主机/安卓实例按单项频率采集，并使阈值规则按指标名称和数据类型工作。

**架构：** 受控目录定义安全命令、解析器和类型；模板定义一组目录指标的默认频率；绑定将模板项生效到主机或安卓实例并允许启停、频率覆盖。采集器每 5 秒筛选到期绑定项，通过有界线程池与目标单飞锁执行，状态不满足时跳过。既有 `metric_snapshot` 保持历史兼容，增加目标维度和稳定指标编码。

**技术栈：** Java 8、Spring Boot 2.7、MyBatis-Plus、Flyway、MySQL 8、OkHttp、Vue 3、Element Plus、Vitest、JUnit 5。

---

## 文件结构

- `myxos-common/.../enums/Metric*.java`：指标目标、值类型、分类和扩展比较操作枚举。
- `myxos-domain/.../entity/MetricCatalog.java`、`MetricTemplate.java`、`MetricTemplateItem.java`、`MetricBinding.java`：目录、模板、模板项和目标绑定实体。
- `myxos-domain/.../mapper/Metric*.java`：目录、模板、绑定和快照查询；快照按安卓实例区分最新值。
- `myxos-main-service/.../metric/`：目录注册表、解析器、模板/绑定服务和管理 API。
- `myxos-collector-service/.../collector/`：到期绑定调度、受控采集执行和状态门控。
- `myxos-ui/src/views/MetricTemplate*.vue`：目录和模板管理界面。
- `myxos-ui/src/views/DeviceDetailView.vue`：主机/实例指标绑定和实例指标详情。
- `myxos-ui/src/views/ThresholdEditView.vue`：按指标名称和类型配置条件，修复持续时长回显。

### Task 1：数据库迁移、领域模型与目录枚举

**文件：**
- 创建：`myxos-main-service/src/main/resources/db/migration/V10__metric_template_schema.sql`
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/entity/MetricCatalog.java`
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/entity/MetricTemplate.java`
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/entity/MetricTemplateItem.java`
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/entity/MetricBinding.java`
- 创建：`myxos-common/src/main/java/bob/myxos/common/enums/MetricTargetType.java`
- 创建：`myxos-common/src/main/java/bob/myxos/common/enums/MetricValueType.java`
- 创建：`myxos-common/src/main/java/bob/myxos/common/enums/MetricCategory.java`
- 修改：`myxos-common/src/main/java/bob/myxos/common/enums/CompareOp.java`
- 修改：`myxos-common/src/main/java/bob/myxos/common/enums/ConditionType.java`
- 修改：`myxos-domain/src/main/java/bob/myxos/domain/entity/MetricSnapshot.java`
- 修改：`myxos-domain/src/main/java/bob/myxos/domain/entity/ThresholdRule.java`
- 测试：`myxos-domain/src/test/java/bob/myxos/domain/entity/MetricTemplateEntityTest.java`
- 测试：`myxos-main-service/src/test/java/bob/myxos/main/migration/MetricTemplateMigrationTest.java`

- [ ] **步骤 1：编写失败的枚举与迁移结构测试**

```java
@Test
void 应定义主机和安卓实例目标类型及三种指标值类型() {
    assertEquals(MetricTargetType.HOST, MetricTargetType.valueOf("HOST"));
    assertEquals(MetricTargetType.ANDROID_INSTANCE, MetricTargetType.valueOf("ANDROID_INSTANCE"));
    assertEquals(MetricValueType.ENUM, MetricValueType.valueOf("ENUM"));
}

@Test
void 迁移应创建模板绑定表并扩展快照和阈值字段() throws IOException {
    String sql = readMigration("V10__metric_template_schema.sql");
    assertTrue(sql.contains("CREATE TABLE metric_catalog"));
    assertTrue(sql.contains("CREATE TABLE metric_template"));
    assertTrue(sql.contains("CREATE TABLE metric_template_item"));
    assertTrue(sql.contains("CREATE TABLE metric_binding"));
    assertTrue(sql.contains("ADD COLUMN metric_code"));
    assertTrue(sql.contains("ADD COLUMN android_name"));
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：`$env:JAVA_HOME='C:\Program Files\Java\jdk1.8.0_202'; mvn -pl myxos-common,myxos-domain,myxos-main-service -Dtest=MetricTemplateEntityTest,MetricTemplateMigrationTest test`

预期：因枚举、实体和 V10 迁移尚不存在而失败。

- [ ] **步骤 3：实现迁移和最小领域模型**

V10 必须创建下列关键字段与约束：

```sql
CREATE TABLE metric_binding (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    device_id BIGINT NOT NULL,
    android_name VARCHAR(128) NOT NULL DEFAULT '',
    target_type VARCHAR(32) NOT NULL,
    metric_code VARCHAR(64) NOT NULL,
    enabled TINYINT NOT NULL DEFAULT 1,
    interval_sec INT NULL,
    last_collected_at DATETIME NULL,
    next_collect_at DATETIME NULL,
    who_created VARCHAR(64) NOT NULL DEFAULT 'system',
    when_created DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    who_modified VARCHAR(64) NOT NULL DEFAULT 'system',
    when_modified DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted TINYINT NOT NULL DEFAULT 0,
    UNIQUE KEY uk_metric_binding_target_metric_active (device_id, android_name, metric_code, deleted),
    INDEX idx_metric_binding_due (enabled, next_collect_at, deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
```

`metric_template_item` 必须存储 `metric_catalog_id`、`enabled`、`default_interval_sec` 与 `enum_options JSON`。`metric_catalog` 必须存储 `code`、`name`、`target_type`、`value_type`、`category`、`unit`、`command_key` 与 `threshold_enabled`，其命令字段不暴露为管理员可写字段。为四张新表补齐审计字段和逻辑删除字段。

迁移必须为 `metric_snapshot` 增加 `metric_code`、`target_type`、`android_name`；为 `threshold_rule` 增加 `metric_code`，并将历史 `metric_type` 回填到 `metric_code`。保留旧 `metric_type` 直到所有读取路径迁移完成。

```java
public enum MetricValueType { NUMBER, STRING, ENUM }
public enum MetricTargetType { HOST, ANDROID_INSTANCE }
public enum MetricCategory { PERFORMANCE, STATUS, BASIC, APPLICATION }
```

`CompareOp` 增加 `IN`、`NOT_IN`，`ConditionType` 增加 `ENUM`。所有新实体沿用 `MetricSnapshot` 的审计和 `@TableLogic` 模式。

- [ ] **步骤 4：运行领域和迁移测试确认通过**

运行：`$env:JAVA_HOME='C:\Program Files\Java\jdk1.8.0_202'; mvn -pl myxos-common,myxos-domain,myxos-main-service -Dtest=MetricTemplateEntityTest,MetricTemplateMigrationTest test`

预期：测试通过。

- [ ] **步骤 5：提交领域迁移**

```powershell
git add myxos-common/src/main/java/bob/myxos/common/enums myxos-domain/src/main/java/bob/myxos/domain/entity myxos-main-service/src/main/resources/db/migration/V10__metric_template_schema.sql myxos-domain/src/test myxos-main-service/src/test
git commit -m "feat(metrics): 增加指标模板领域模型与迁移"
```

### Task 2：受控指标目录、ADB 解析器与实机验证记录

**文件：**
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/metric/MetricDefinition.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/metric/MetricDefinitionRegistry.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/metric/AndroidMetricParser.java`
- 创建：`myxos-main-service/src/test/java/bob/myxos/main/metric/AndroidMetricParserTest.java`
- 创建：`docs/superpowers/research/2026-08-05-adb-metric-validation.md`
- 修改：`myxos-main-service/src/main/resources/db/migration/V10__metric_template_schema.sql`

- [ ] **步骤 1：先写 ADB 解析失败测试**

```java
@Test
void 应解析真实top输出中的CPU使用率和任务数() {
    String output = "Tasks: 99 total, 1 running, 98 sleeping, 0 stopped, 0 zombie\n"
            + "800%cpu 36%user 0%nice 16%sys 736%idle 4%iow";
    assertEquals(new BigDecimal("8.00"), AndroidMetricParser.parseCpuUsagePercent(output));
    assertEquals(new BigDecimal("99"), AndroidMetricParser.parseTaskTotal(output));
}

@Test
void 应拒绝缺少MemAvailable的内存输出() {
    assertFalse(AndroidMetricParser.parseMemAvailableKb("MemTotal: 100 kB").isPresent());
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：`$env:JAVA_HOME='C:\Program Files\Java\jdk1.8.0_202'; mvn -pl myxos-main-service -Dtest=AndroidMetricParserTest test`

预期：因解析器不存在而失败。

- [ ] **步骤 3：实现只读目录和解析器**

`MetricDefinitionRegistry` 只允许下列经过授权验证的 `commandKey`：

```java
ANDROID_VERSION("ANDROID_VERSION", "getprop ro.build.version.release"),
ANDROID_MODEL("ANDROID_MODEL", "getprop ro.product.model"),
MEM_TOTAL_KB("MEM_TOTAL_KB", "cat /proc/meminfo"),
MEM_AVAILABLE_KB("MEM_AVAILABLE_KB", "cat /proc/meminfo"),
CPU_USAGE_PERCENT("CPU_USAGE_PERCENT", "top -b -n 1"),
TASK_TOTAL("TASK_TOTAL", "top -b -n 1"),
RECENT_APPS("RECENT_APPS", "dumpsys activity recents");
```

解析器必须仅从完整输出中提取字段，解析失败返回空结果，不以 `0` 伪造数值。V10 向 `metric_catalog` 插入这些目录项，数值指标的 `value_type=NUMBER`，系统版本和型号为 `STRING`，安卓运行状态为 `ENUM`；模板项初始枚举选项包含 `RUNNING`、`STOPPED`、`TRANSITION`、`UNKNOWN`。

验证记录须写明 2026-08-05 在授权实例上实际确认的响应：Android 12、型号 `2410DPN6CC`、`/proc/meminfo` 包含 `MemTotal`/`MemAvailable`、`top` 使用 `800%cpu` 格式、`dumpsys activity recents` 可返回最近任务。不得把未验证的 `grep`、`awk` 管道作为默认命令。

- [ ] **步骤 4：运行解析测试确认通过**

运行：`$env:JAVA_HOME='C:\Program Files\Java\jdk1.8.0_202'; mvn -pl myxos-main-service -Dtest=AndroidMetricParserTest test`

预期：测试通过。

- [ ] **步骤 5：提交目录与解析器**

```powershell
git add myxos-main-service/src/main/java/bob/myxos/main/metric myxos-main-service/src/test/java/bob/myxos/main/metric myxos-main-service/src/main/resources/db/migration/V10__metric_template_schema.sql docs/superpowers/research/2026-08-05-adb-metric-validation.md
git commit -m "feat(metrics): 增加受控ADB指标目录与解析器"
```

### Task 3：模板、绑定和指标查询管理 API

**文件：**
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/mapper/MetricCatalogMapper.java`
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/mapper/MetricTemplateMapper.java`
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/mapper/MetricTemplateItemMapper.java`
- 创建：`myxos-domain/src/main/java/bob/myxos/domain/mapper/MetricBindingMapper.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/dto/MetricTemplateReq.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/dto/MetricBindingReq.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/service/MetricTemplateService.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/service/impl/MetricTemplateServiceImpl.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/controller/MetricCatalogController.java`
- 创建：`myxos-main-service/src/main/java/bob/myxos/main/controller/MetricTemplateController.java`
- 修改：`myxos-main-service/src/main/java/bob/myxos/main/controller/DeviceController.java`
- 修改：`myxos-main-service/src/main/java/bob/myxos/main/service/DeviceService.java`
- 修改：`myxos-main-service/src/main/java/bob/myxos/main/service/impl/DeviceServiceImpl.java`
- 测试：`myxos-main-service/src/test/java/bob/myxos/main/service/MetricTemplateServiceTest.java`
- 测试：`myxos-main-service/src/test/java/bob/myxos/main/controller/MetricTemplateControllerIntegrationTest.java`

- [ ] **步骤 1：编写模板频率与实例覆盖失败测试**

```java
@Test
void 实例覆盖频率应优先于主机绑定() {
    MetricBinding host = binding(1L, null, "CPU_USAGE_PERCENT", 60);
    MetricBinding instance = binding(1L, "android-1", "CPU_USAGE_PERCENT", 15);
    assertEquals(15, service.resolveEffectiveBinding(1L, "android-1", "CPU_USAGE_PERCENT",
            Arrays.asList(host, instance)).getIntervalSec().intValue());
}

@Test
void 模板不应接受与目标类型不兼容的目录项() {
    MetricTemplateReq req = hostTemplateContaining("ANDROID_VERSION");
    assertThrows(BizException.class, () -> service.create(req));
}
```

- [ ] **步骤 2：运行服务测试确认失败**

运行：`$env:JAVA_HOME='C:\Program Files\Java\jdk1.8.0_202'; mvn -pl myxos-main-service -Dtest=MetricTemplateServiceTest test`

预期：服务和绑定解析方法尚不存在而失败。

- [ ] **步骤 3：实现模板、绑定和查询接口**

模板创建请求必须包含名称、目标类型和模板项；模板项必须指定目录指标、默认频率（15 至 86400 秒）和启用状态。服务必须从目录读取值类型与枚举选项，拒绝客户端写入 `command_key` 或解析规则。

应用模板时，为每个模板项创建或恢复一条 `metric_binding`；主机绑定的 `android_name` 固定写入空字符串，安卓实例绑定写入实例名称。一个目标的同一 `metric_code` 只能有一个有效绑定；应用包含重复指标的第二个模板时返回冲突明细，管理员必须先停用或移除既有绑定。取消应用时逻辑删除对应绑定。单项覆盖接口只允许修改 `enabled` 和 `interval_sec`。安卓实例绑定必须验证实例名称格式 `^[A-Za-z0-9_.-]{1,128}$`，并验证目录/模板目标类型为 `ANDROID_INSTANCE`。

实现以下接口：

```text
GET    /api/metric-catalogs?targetType=HOST|ANDROID_INSTANCE
GET    /api/metric-templates
POST   /api/metric-templates
PUT    /api/metric-templates/{id}
DELETE /api/metric-templates/{id}
GET    /api/devices/{id}/metric-bindings
PUT    /api/devices/{id}/metric-bindings
GET    /api/devices/{id}/androids/{name}/metric-bindings
PUT    /api/devices/{id}/androids/{name}/metric-bindings
```

模板和绑定写接口使用 `hasRole('ADMIN')`；目录和读取接口保持登录可访问。`DeviceServiceImpl` 必须提供最终生效绑定查询，使设备详情和采集器使用同一优先级算法。

- [ ] **步骤 4：运行服务和控制器测试确认通过**

运行：`$env:JAVA_HOME='C:\Program Files\Java\jdk1.8.0_202'; mvn -pl myxos-main-service -Dtest=MetricTemplateServiceTest,MetricTemplateControllerIntegrationTest test`

预期：测试通过，非管理员写请求返回 403。

- [ ] **步骤 5：提交管理 API**

```powershell
git add myxos-domain/src/main/java/bob/myxos/domain/mapper myxos-main-service/src/main/java/bob/myxos/main/dto myxos-main-service/src/main/java/bob/myxos/main/service myxos-main-service/src/main/java/bob/myxos/main/controller myxos-main-service/src/test
git commit -m "feat(metrics): 提供模板与目标绑定管理接口"
```

### Task 4：按绑定到期时间的有界采集调度

**文件：**
- 创建：`myxos-collector-service/src/main/java/bob/myxos/collector/collector/MetricBindingScheduler.java`
- 创建：`myxos-collector-service/src/main/java/bob/myxos/collector/collector/BoundMetricCollector.java`
- 创建：`myxos-collector-service/src/main/java/bob/myxos/collector/collector/MetricExecutionResult.java`
- 修改：`myxos-collector-service/src/main/java/bob/myxos/collector/collector/MetricCollectJob.java`
- 修改：`myxos-collector-service/src/main/java/bob/myxos/collector/config/ThreadPoolConfig.java`
- 修改：`myxos-collector-service/src/main/java/bob/myxos/collector/config/CollectorProperties.java`
- 修改：`myxos-collector-service/src/main/java/bob/myxos/collector/cleanup/DataCleanupJob.java`
- 修改：`myxos-domain/src/main/java/bob/myxos/domain/mapper/MetricBindingMapper.java`
- 修改：`myxos-domain/src/main/java/bob/myxos/domain/mapper/MetricSnapshotMapper.java`
- 测试：`myxos-collector-service/src/test/java/bob/myxos/collector/collector/MetricBindingSchedulerTest.java`
- 测试：`myxos-collector-service/src/test/java/bob/myxos/collector/collector/BoundMetricCollectorTest.java`

- [ ] **步骤 1：先写到期、状态门控和单飞失败测试**

```java
@Test
void 非运行中安卓实例不应提交ADB采集任务() {
    MetricBinding binding = androidBinding("a-1", dueNow());
    scheduler.dispatch(Collections.singletonList(binding), statuses("a-1", "STOPPED"));
    verify(executor, never()).execute(any(Runnable.class));
    verify(bindingMapper).markSkipped(binding.getId(), any(LocalDateTime.class));
}

@Test
void 同一目标已有执行任务时不应重复提交() {
    MetricBinding binding = hostBinding(1L, dueNow());
    scheduler.markTargetRunning("HOST:1");
    scheduler.dispatch(Collections.singletonList(binding), onlineHost(1L));
    verify(executor, never()).execute(any(Runnable.class));
}
```

- [ ] **步骤 2：运行采集器测试确认失败**

运行：`$env:JAVA_HOME='C:\Program Files\Java\jdk1.8.0_202'; mvn -pl myxos-collector-service -Dtest=MetricBindingSchedulerTest,BoundMetricCollectorTest test`

预期：新的调度器和采集执行器尚不存在而失败。

- [ ] **步骤 3：实现调度器和受控执行器**

保留 `MetricCollectJob` 的健康检查和安卓状态刷新职责，但移除其对所有设备统一采集系统指标的行为。`MetricBindingScheduler` 使用固定 5 秒 `@Scheduled(fixedDelay = 5000)` 查询 `enabled=1`、`next_collect_at <= NOW()` 的绑定，最多领取 `collector.metric-dispatch-batch-size`（默认 100）条。

使用 `ThreadPoolTaskExecutor` 配置固定核心线程、最大线程、有限队列与 `DiscardPolicy`；提交被拒绝时更新绑定下一次时间为当前时间加最小退避间隔，记录日志，不重试阻塞。使用 `ConcurrentHashMap<String, Boolean>` 目标键 `HOST:{deviceId}` 或 `ANDROID:{deviceId}:{name}` 实现单飞锁。

`BoundMetricCollector` 只从 `MetricDefinitionRegistry` 取得命令与解析器：主机目录项复用 MYTOS 系统信息接口；安卓目录项调用 `MytosClient.shell(ip, androidName, command)`。仅在主机 `ONLINE` 和安卓实例 `RUNNING` 时执行对应绑定，其他状态设置下次检查时间而不发请求。执行结果需写入 `metric_snapshot` 的 `metric_code`、`target_type`、`android_name`、`metric_value`、`metric_num` 和 `extra`；失败则写 `UNKNOWN` 字符/枚举快照和错误原因，数值解析失败不写伪造的 0。

每次完成或跳过必须原子更新 `last_collected_at` 和 `next_collect_at=completedAt+effectiveIntervalSec`。`DataCleanupJob` 读取 `metric.retention.days` 系统配置，缺失时使用 30 天，不再硬编码保留期。

- [ ] **步骤 4：运行调度器测试确认通过**

运行：`$env:JAVA_HOME='C:\Program Files\Java\jdk1.8.0_202'; mvn -pl myxos-collector-service -Dtest=MetricBindingSchedulerTest,BoundMetricCollectorTest test`

预期：到期执行、状态跳过、单飞、队列拒绝和下一次时间计算测试通过。

- [ ] **步骤 5：提交模板调度器**

```powershell
git add myxos-collector-service/src/main/java myxos-collector-service/src/test myxos-domain/src/main/java/bob/myxos/domain/mapper
git commit -m "feat(collector): 按指标模板调度采集任务"
```

### Task 5：快照查询、阈值类型化与持续时长兼容修复

**文件：**
- 修改：`myxos-domain/src/main/java/bob/myxos/domain/mapper/MetricSnapshotMapper.java`
- 修改：`myxos-main-service/src/main/java/bob/myxos/main/dto/ThresholdRuleReq.java`
- 修改：`myxos-main-service/src/main/java/bob/myxos/main/service/impl/ThresholdServiceImpl.java`
- 修改：`myxos-main-service/src/main/java/bob/myxos/main/controller/ThresholdController.java`
- 修改：`myxos-collector-service/src/main/java/bob/myxos/collector/evaluate/ThresholdEvaluator.java`
- 测试：`myxos-main-service/src/test/java/bob/myxos/main/service/ThresholdServiceTest.java`
- 测试：`myxos-collector-service/src/test/java/bob/myxos/collector/evaluate/ThresholdEvaluatorTest.java`

- [ ] **步骤 1：先写枚举条件和安卓实例隔离失败测试**

```java
@Test
void 枚举阈值只能使用模板项已验证选项() {
    ThresholdRuleReq req = enumRule("ANDROID_STATUS", "IN", "[\"RUNNING\",\"INVALID\"]");
    assertThrows(BizException.class, () -> thresholdService.create(req));
}

@Test
void 最新快照查询应按安卓实例和指标编码分组() {
    List<MetricSnapshot> latest = mapper.selectLatestPerMetricTargetByDevice(1L);
    assertEquals(2, latest.stream().filter(s -> "ANDROID_STATUS".equals(s.getMetricCode())).count());
}
```

- [ ] **步骤 2：运行测试确认失败**

运行：`$env:JAVA_HOME='C:\Program Files\Java\jdk1.8.0_202'; mvn -pl myxos-main-service,myxos-collector-service -Dtest=ThresholdServiceTest,ThresholdEvaluatorTest test`

预期：新的 `metricCode` 和枚举校验尚不存在而失败。

- [ ] **步骤 3：实现类型化阈值**

`ThresholdRuleReq` 新增 `metricCode` 和 `thresholdOptions`（枚举 JSON 数组），保留 `metricType` 仅作历史兼容。`ThresholdServiceImpl` 必须从目录/模板项加载值类型：

```java
NUMBER -> GT, GTE, LT, LTE, EQ, NE
STRING -> EQ, NE, CONTAINS, NOT_CONTAINS
ENUM   -> EQ, NE, IN, NOT_IN
```

对 `ENUM`，解析 `thresholdOptions`，验证每一个值都存在于目标范围可用模板项的 `enumOptions`，再以 JSON 写入 `threshold_text`。更新与创建均须写入 `metric_code`。`ThresholdEvaluator` 用 `metricCode` 匹配快照；对 `IN/NOT_IN` 解析 JSON 数组；数值快照为空、`UNKNOWN` 或解析失败时跳过数值判定。

`MetricSnapshotMapper` 新增按 `(metric_code, target_type, android_name)` 分组的最新查询，保留旧方法供未迁移页面使用。`ThresholdController` 增加按指标编码查询模板枚举选项的只读接口。

持续时长问题的后端保障：`ThresholdServiceImpl.update` 在 `triggerMode=DURATION` 且 `durationSec` 非空时显式写入该值，不能由 DTO 默认值覆盖；`CONSECUTIVE` 模式仅显式写入 `consecutiveCount`。

- [ ] **步骤 4：运行阈值测试确认通过**

运行：`$env:JAVA_HOME='C:\Program Files\Java\jdk1.8.0_202'; mvn -pl myxos-main-service,myxos-collector-service -Dtest=ThresholdServiceTest,ThresholdEvaluatorTest test`

预期：枚举值校验、实例隔离、持续时长更新和旧规则兼容测试通过。

- [ ] **步骤 5：提交阈值改造**

```powershell
git add myxos-domain/src/main/java/bob/myxos/domain/mapper/MetricSnapshotMapper.java myxos-main-service/src/main/java/bob/myxos/main myxos-collector-service/src/main/java/bob/myxos/collector/evaluate myxos-main-service/src/test myxos-collector-service/src/test
git commit -m "feat(threshold): 按指标名称支持类型化阈值"
```

### Task 6：指标目录、模板和绑定前端管理

**文件：**
- 创建：`myxos-ui/src/views/MetricCatalogView.vue`
- 创建：`myxos-ui/src/views/MetricTemplateListView.vue`
- 创建：`myxos-ui/src/views/MetricTemplateEditView.vue`
- 创建：`myxos-ui/src/utils/metric-template.js`
- 创建：`myxos-ui/src/utils/metric-template.test.js`
- 修改：`myxos-ui/src/api/index.js`
- 修改：`myxos-ui/src/router/index.js`
- 修改：`myxos-ui/src/App.vue`

- [ ] **步骤 1：先写模板项频率和枚举选项失败测试**

```javascript
it('仅为枚举指标保留可选值并校验频率下限', () => {
  const item = normalizeTemplateItem({ valueType: 'ENUM', intervalSec: 10, enumOptions: ['RUNNING'] })
  expect(item).toEqual({ valueType: 'ENUM', intervalSec: 15, enumOptions: ['RUNNING'] })
})

it('非枚举指标不应提交枚举选项', () => {
  expect(normalizeTemplateItem({ valueType: 'NUMBER', intervalSec: 60, enumOptions: ['x'] }).enumOptions).toEqual([])
})
```

- [ ] **步骤 2：运行前端测试确认失败**

运行：`Set-Location myxos-ui; npm.cmd test -- metric-template.test.js`

预期：工具函数不存在而失败。

- [ ] **步骤 3：实现 API、路由与管理页面**

在 `api/index.js` 增加 `metricCatalogApi`、`metricTemplateApi` 和 `metricBindingApi`，只传递后端允许字段。模板编辑页必须：按目标类型加载目录、用多选表格选择指标、限制频率为 15 至 86400 秒、仅在 `valueType==='ENUM'` 时显示枚举选项编辑器。目录页只读展示验证状态、分类、类型、单位和建议频率，不能展示或编辑原始命令。

新增管理员导航“指标模板”，其中包含“指标目录”和“模板管理”两个路由。路由和导航使用现有鉴权状态；非管理员不显示入口，后端仍作为权限边界。

- [ ] **步骤 4：运行模板工具测试和构建确认通过**

运行：`Set-Location myxos-ui; npm.cmd test -- metric-template.test.js; npm.cmd run build`

预期：测试和 Vite 构建通过。

- [ ] **步骤 5：提交模板管理界面**

```powershell
git add myxos-ui/src/api/index.js myxos-ui/src/router/index.js myxos-ui/src/App.vue myxos-ui/src/views/MetricCatalogView.vue myxos-ui/src/views/MetricTemplateListView.vue myxos-ui/src/views/MetricTemplateEditView.vue myxos-ui/src/utils/metric-template.js myxos-ui/src/utils/metric-template.test.js
git commit -m "feat(ui): 增加指标目录与模板管理页面"
```

### Task 7：设备和安卓实例绑定、详情及阈值编辑界面

**文件：**
- 修改：`myxos-ui/src/views/DeviceDetailView.vue`
- 修改：`myxos-ui/src/views/ThresholdEditView.vue`
- 修改：`myxos-ui/src/utils/threshold.js`
- 创建：`myxos-ui/src/utils/threshold.test.js`

- [ ] **步骤 1：先写持续时长与条件选项失败测试**

```javascript
it('编辑持续时长规则应保留后端秒数', () => {
  const form = toThresholdForm({ triggerMode: 'DURATION', durationSec: 45, consecutiveCount: null })
  expect(form.durationSec).toBe(45)
})

it('枚举指标应生成多选条件而数值指标应生成区间条件', () => {
  expect(conditionOptions('ENUM').map(item => item.value)).toEqual(['EQ', 'NE', 'IN', 'NOT_IN'])
  expect(conditionOptions('NUMBER').map(item => item.value)).toContain('GT')
})
```

- [ ] **步骤 2：运行测试确认失败**

运行：`Set-Location myxos-ui; npm.cmd test -- threshold.test.js`

预期：转换与条件工具尚不存在而失败。

- [ ] **步骤 3：实现绑定与详情交互**

设备详情新增“指标配置”区域：显示主机最终生效模板项、启用状态、实际频率、最近采集时间；管理员可添加模板并编辑单项启停/频率覆盖。每个安卓实例卡片增加图标按钮打开指标详情抽屉，显示继承项、直接绑定项和最终生效项；实例非 `RUNNING` 时显示“已暂停采集”。

实例卡片基础信息从最新实例指标中读取 `CPU_CORE_COUNT`、`MEM_TOTAL_KB`、`ANDROID_MODEL` 和 `ANDROID_VERSION`，缺失值展示“暂无采集数据”。不得将不同 `android_name` 的最新快照混用。

阈值编辑页改用指标目录选择器，选择后加载值类型和枚举选项。数值显示数值输入与单位；字符串显示文本输入；枚举显示多选下拉。表单回填必须调用 `toThresholdForm`，直接保留 `durationSec`，不得以 `|| 0` 覆盖有效值。提交时发送 `metricCode`、`thresholdOptions` 和匹配的比较操作。

- [ ] **步骤 4：运行前端测试和构建确认通过**

运行：`Set-Location myxos-ui; npm.cmd test; npm.cmd run build`

预期：全部 Vitest 测试和构建通过。

- [ ] **步骤 5：提交设备与阈值界面**

```powershell
git add myxos-ui/src/views/DeviceDetailView.vue myxos-ui/src/views/ThresholdEditView.vue myxos-ui/src/utils/threshold.js myxos-ui/src/utils/threshold.test.js
git commit -m "feat(ui): 支持设备指标绑定与类型化阈值"
```

### Task 8：系统配置、回归验证与授权设备验证

**文件：**
- 修改：`myxos-main-service/src/main/resources/db/migration/V10__metric_template_schema.sql`
- 修改：`myxos-collector-service/src/main/java/bob/myxos/collector/cleanup/DataCleanupJob.java`
- 修改：`myxos-ui/src/views/SettingsView.vue`
- 测试：`myxos-collector-service/src/test/java/bob/myxos/collector/cleanup/DataCleanupJobTest.java`
- 修改：`docs/superpowers/research/2026-08-05-adb-metric-validation.md`

- [ ] **步骤 1：先写保留期默认值和下限失败测试**

```java
@Test
void 指标保留期配置缺失时清理任务应使用30天() {
    assertEquals(LocalDateTime.now().minusDays(30).toLocalDate(),
            job.resolveMetricDeadline(LocalDateTime.now()).toLocalDate());
}

@Test
void 非法指标保留期配置应回退到30天() {
    when(mapper.selectOne(any())).thenReturn(config("metric.retention.days", "0"));
    assertEquals(30, job.resolveMetricRetentionDays());
}
```

- [ ] **步骤 2：运行配置测试确认失败**

运行：`$env:JAVA_HOME='C:\Program Files\Java\jdk1.8.0_202'; mvn -pl myxos-collector-service -Dtest=DataCleanupJobTest test`

预期：清理任务的解析方法尚不存在而失败。

- [ ] **步骤 3：实现保留期配置与前端设置项**

V10 插入系统键 `metric.retention.days`，默认 `30`、说明为“指标历史保留天数（1 至 3650）”。`DataCleanupJob` 将缺失、非整数或不在 1 至 3650 范围的值回退为 30 天，并把保留期解析提取为可测试的包级方法。现有 `/api/sys-config` 已提供管理员写权限，不新增专用 API。`SettingsView.vue` 对该键使用 `el-input-number` 限制 1 至 3650，其余系统键保持当前文本编辑方式；修改后下一个清理周期生效，无需重启。

- [ ] **步骤 4：运行全量回归**

运行：

```powershell
$env:JAVA_HOME='C:\Program Files\Java\jdk1.8.0_202'
$env:Path="$env:JAVA_HOME\bin;$env:Path"
mvn test
Set-Location myxos-ui
npm.cmd test
npm.cmd run build
```

预期：Maven、Vitest 和 Vite 构建全部通过；构建中仅保留既有依赖告警。

- [ ] **步骤 5：执行授权的只读设备验证并更新记录**

仅对 `192.168.107.193` 的 `MYTSDK1782180364795806781_3_T3001-EJNB` 调用 `http://192.168.107.199:81/and_api/v1/shell/{ip}/{name}`，执行目录中的 `getprop`、`cat /proc/meminfo`、`top -b -n 1` 和 `dumpsys activity recents`。记录 HTTP 结果、`shell_code`、解析值和耗时；不得执行修改状态命令。

- [ ] **步骤 6：提交系统配置与验证记录**

```powershell
git add myxos-main-service/src/main/resources/db/migration/V10__metric_template_schema.sql myxos-collector-service/src/main/java/bob/myxos/collector/cleanup/DataCleanupJob.java myxos-collector-service/src/test/java/bob/myxos/collector/cleanup/DataCleanupJobTest.java myxos-ui/src/views/SettingsView.vue docs/superpowers/research/2026-08-05-adb-metric-validation.md
git commit -m "feat(metrics): 增加指标保留期配置与验证记录"
```

## 实施前复核

- 规格覆盖：Task 1 定义模型和兼容迁移；Task 2 限定并验证 ADB 指标；Task 3 实现模板和绑定；Task 4 实现非阻塞按项调度；Task 5 实现类型化阈值；Task 6-7 实现管理、绑定和详情界面；Task 8 实现保留期与全量验证。
- 一致性：所有服务、调度和前端均使用 `metricCode`、`MetricTargetType`、`MetricValueType`、`MetricBinding` 和 15 至 86400 秒频率范围。
- 边界：真实设备验证只使用用户已授权的只读命令；管理员不能写入命令、解析器或未验证的枚举值。
