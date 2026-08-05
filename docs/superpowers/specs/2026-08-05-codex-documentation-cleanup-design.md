# Codex 文档清理设计

## 目标

使仓库说明文档与当前 MYXOS 实现保持一致，移除 Claude Code 专用遗留文件，并保留全部 Superpowers 计划文件。

## 范围

- 更新根目录 `AGENTS.md`，反映当前 JDK 1.8、Spring Boot 2.7.18、五个后端 Maven 模块与 Vue 前端模块的实际状态。
- 更新根目录 `README.md`，提供当前架构、环境要求、构建、启动、前端开发与 Docker 部署说明。
- 删除根目录 `CLAUDE.md` 与 `.claude/` 目录。
- 移除 `.gitignore` 中仅用于 Claude Code 的忽略规则。
- 保留 `docs/superpowers/` 下已有的全部文件；仅将该目录中对已删除 `CLAUDE.md` 的引用改为 `AGENTS.md`。

## 不在范围内

- 不改写或删除任何 Superpowers 计划的技术内容、任务清单或历史记录。
- 不改动业务代码、构建配置、环境变量示例或部署文件。
- 不提交本次变更，除非后续明确要求。

## 实施方式

采用最小变更策略。根目录文档只记录能够从现有 Maven、前端配置、Docker 配置和源代码直接验证的事实。历史计划文件只进行失效文件引用替换，避免影响其作为实施依据和历史记录的用途。

## 验证标准

- 仓库不再包含 `CLAUDE.md`、`.claude/` 或 Claude Code 专用忽略规则。
- 除 `.git/`、`node_modules/`、`target/` 和 CodeGraph 索引外，文本文件中不存在指向 `CLAUDE.md` 的引用。
- `AGENTS.md` 和 README 的模块、Java 与 Spring Boot 版本、启动命令与根目录构建配置一致。
- `docs/superpowers/` 的既有文件均仍存在。
