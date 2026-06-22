# <img src="assets/cc.png" height="40" align="absmiddle"> mini-claude-code

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/DerekYRC/mini-claude-code)
[![Java](https://img.shields.io/badge/Java-17-4EB1BA.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/build-Maven-C71A36.svg)](https://maven.apache.org/)


**[English](./README_en.md) | 简体中文**

**姊妹版：**
- [**mini-spring**](https://github.com/DerekYRC/mini-spring) **(简化版的spring框架)**
- [**mini-spring-cloud**](https://github.com/DerekYRC/mini-spring-cloud) **(简化版的spring cloud框架)**
- [**mini-netty**](https://github.com/DerekYRC/mini-netty) **(简化版的netty框架)**

## 关于

**mini-claude-code** 是一个简化版的Java版 Claude Code 编程 Agent 项目，能帮助你快速理解编码 Agent 的核心原理。项目抽取了 Agent Harness 的关键机制，**代码尽量精简，保留核心功能**，如 Agent Loop、工具调用、权限控制、Hooks、Todo、Subagent、Skill Loading、上下文压缩、记忆系统、任务系统、后台任务、定时调度、多 Agent 协作、团队协议、自主认领任务和 MCP Plugin 等功能。

项目按章节拆解一个编码 Agent 的核心机制，每章对应一个独立分支，并尽量保留理解当前机制所需的最小代码。

如果本项目能帮助到你，请给个 **STAR**，谢谢！

## 功能

* [s01 Agent Loop](changelog.md#s01-agent-loop) — ReAct：思考-行动-观察，LLM ⇄ Tool 闭环
* [s02 Tool Dispatch](changelog.md#s02-tool-dispatch) — Tool Use：注册即用，不改主循环
* [s03 Permission](changelog.md#s03-permission) — Guardrails：三层校验，先审后执行
* [s04 Hooks](changelog.md#s04-hooks) — Lifecycle：四个事件点，横切逻辑不侵入循环
* [s05 Todo](changelog.md#s05-todo) — Planning：先规划再执行，状态可追踪
* [s06 Subagent](changelog.md#s06-subagent) — Delegation：委托子代理，上下文干净隔离
* [s07 Skill Loading](changelog.md#s07-skill-loading) — Lazy Skill Loading：索引在 Prompt，正文按需加载
* [s08 Context Compact](changelog.md#s08-context-compact) — Context Compression：四层压缩管线，满了就腾空间
* [s09 Memory](changelog.md#s09-memory) — Long-term Memory：记什么、忘什么、何时合并
* [s10 Task System](changelog.md#s10-task-system) — Task Orchestration：任务图 + 状态机 + 依赖检查
* [s11 Background Tasks](changelog.md#s11-background-tasks) — Async：慢操作丢后台，Agent 继续思考
* [s12 Cron Scheduler](changelog.md#s12-cron-scheduler) — Scheduled Trigger：cron 表达式，无人值守触发
* [s13 Agent Teams](changelog.md#s13-agent-teams) — Multi-Agent：Lead 派活，队友并行干活
* [s14 Team Protocols](changelog.md#s14-team-protocols) — Structured Communication：request_id + 状态机，队友间结构化通信
* [s15 Autonomous Agents](changelog.md#s15-autonomous-agents) — Self-Organization：队友扫看板，有活自己认领
* [s16 MCP Plugin](changelog.md#s16-mcp-plugin) — MCP：动态工具池，能力不够插上就用

## 使用方法

### 1. 准备环境

本项目需要 Java 17 和 Maven。

运行真实模型 demo 前，先在 shell 中设置：

```sh
export ANTHROPIC_BASE_URL='你的 Anthropic 兼容 API Base URL，如: https://api.deepseek.com/anthropic'
export MODEL_ID='你的模型 ID'
export ANTHROPIC_API_KEY='你的 API Key'
```

### 2. 切换章节分支

每章对应一个教学分支。比如学习 s01：

```sh
git switch s01-agent-loop
```

### 3. 启动 Demo

使用 Maven 启动指定 demo：

```sh
mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.S01AgentLoopDemo
```

### 4. 阅读源码说明

阅读 [changelog.md](changelog.md)


## 提问

[点此提问](https://github.com/DerekYRC/mini-claude-code/issues)

## 贡献

欢迎 Pull Request。

## 参考项目

- [learn-claude-code](https://github.com/shareAI-lab/learn-claude-code)(强烈推荐)

