# Codex 文档清理实施计划

> **给代理工作者：** 必需子技能：使用 `superpowers:subagent-driven-development`（推荐）或 `superpowers:executing-plans` 按任务逐步实施。步骤使用复选框 `- [ ]` 语法以便跟踪。

**目标：** 将仓库文档同步到当前 MYXOS 实现，清理 Claude Code 遗留文件，同时完整保留 Superpowers 文档。

**架构：** 根目录 `AGENTS.md` 作为 Codex 协作约定的唯一入口，README 作为项目使用说明。历史 Superpowers 计划保持原文件与技术内容，仅将已删除文件的引用改为新的入口文档。

**技术栈：** Markdown、Git、PowerShell、Maven 3.8+、Node.js 18+。

## 全局约束

- 所有新增或更新文字使用中文，类名、命令、路径和配置键保留英文。
- 不修改任何 Java、Vue、Maven、Docker、环境变量示例或部署实现。
- 不删除或重写既有 `docs/superpowers/` 文件；仅更新其中失效的 `CLAUDE.md` 引用。
- 保留工作区已有的无关未提交内容，包括 `.codegraph/` 与根目录 `AGENTS.md`。

---

### 任务 1：同步协作与项目说明

**文件：**
- 修改：`AGENTS.md`
- 修改：`README.md`

**接口：**
- 消费：根 `pom.xml` 中的 Spring Boot 2.7.18、Java 1.8 和五个 Maven 模块定义；`myxos-ui/package.json` 中的 Vue/Vite 前端定义。
- 产出：准确反映当前架构、模块职责、构建与启动命令的根目录协作说明和项目说明。

- [ ] **步骤 1：确定当前事实来源**

运行：

```powershell
Select-String -Path pom.xml -Pattern '<version>2.7.18</version>|<java.version>1.8</java.version>|<module>'
Get-Content -Raw -Encoding utf8 myxos-ui/package.json
```

预期：确认当前为五个后端 Maven 模块加一个 `myxos-ui` Vue 前端模块。

- [ ] **步骤 2：更新 `AGENTS.md`**

将过时的“单模块、待改造”说明替换为当前系统状态；保留数据库、安全、提交规范和 MYTOS 接口映射约定。将常用命令统一为当前多模块构建、服务启动、前端开发与测试命令。

- [ ] **步骤 3：更新 README**

保留项目概述、技术栈、环境变量、Docker 和安全章节；补充或校正后端与前端的独立启动命令，并让模块列表与根 `pom.xml`、`myxos-ui/package.json` 一致。

- [ ] **步骤 4：验证根说明文档**

运行：

```powershell
Select-String -Path AGENTS.md,README.md -Pattern 'Spring Boot 4.1.1|Java 17|单模块|待改造'
```

预期：没有匹配结果。

### 任务 2：清理 Claude Code 遗留文件与引用

**文件：**
- 删除：`CLAUDE.md`
- 删除：`.claude/`
- 修改：`.gitignore`
- 修改：`docs/superpowers/plans/2026-08-03-mytos-api-mapping.md`

**接口：**
- 消费：根目录 `AGENTS.md` 作为后续唯一协作说明入口。
- 产出：不含 Claude Code 专用文件与忽略规则的仓库，且历史计划不再指向已删除文件。

- [ ] **步骤 1：核对待删除目标**

运行：

```powershell
git ls-files -- CLAUDE.md .claude
Get-ChildItem -Force .claude -Recurse
```

预期：仅显示 `CLAUDE.md` 和 Claude Code 的检查点文件或目录。

- [ ] **步骤 2：移除 Claude 专用文件与规则**

删除根目录 `CLAUDE.md` 和 `.claude/`；从 `.gitignore` 中移除 `### Claude Code ###` 注释及 `.claude/` 条目，不影响其他工具的忽略规则。

- [ ] **步骤 3：修正 Superpowers 中的失效引用**

在 `docs/superpowers/plans/2026-08-03-mytos-api-mapping.md` 中，将“同步写入 `CLAUDE.md`”及对应变更清单条目替换为 `AGENTS.md`。不得修改计划中的接口映射、任务或技术范围。

- [ ] **步骤 4：验证清理范围**

运行：

```powershell
Test-Path CLAUDE.md
Test-Path .claude
Get-ChildItem -File -Recurse docs\superpowers | Measure-Object
Get-ChildItem -File -Recurse -Force | Where-Object {
  $_.FullName -notmatch '\\.git\\|\\node_modules\\|\\target\\|\\.codegraph\\'
} | Select-String -Pattern 'CLAUDE\.md|Claude Code' -CaseSensitive:$false
```

预期：前两项均为 `False`；Superpowers 文件仍存在；最后一条命令没有输出。

### 任务 3：完成一致性验证

**文件：**
- 验证：`AGENTS.md`
- 验证：`README.md`
- 验证：`.gitignore`
- 验证：`docs/superpowers/`

**接口：**
- 消费：前两项任务产生的文档与删除结果。
- 产出：可审查的变更摘要，不包含业务代码变更。

- [ ] **步骤 1：检查变更清单**

运行：

```powershell
git status --short
git diff --check
git diff -- AGENTS.md README.md .gitignore docs/superpowers
git diff --cached --check
```

预期：变更限于目标文档、Claude 专用文件删除与已存在的用户工作区变更；无空白错误。

- [ ] **步骤 2：执行不修改文件的构建验证**

运行：

```powershell
mvn test
```

预期：所有 Maven 模块测试通过；文档清理不引入构建行为变更。

- [ ] **步骤 3：记录验证结果**

报告已更新与删除的文件、保留的 Superpowers 文件，以及构建验证的实际结果。未经用户明确要求，不创建提交。
