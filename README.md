# <img src="assets/cc.png" height="40" align="absmiddle"> mini-claude-code

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/DerekYRC/mini-claude-code)
[![Java](https://img.shields.io/badge/Java-17-4EB1BA.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/build-Maven-C71A36.svg)](https://maven.apache.org/)
[![Stars](https://img.shields.io/github/stars/DerekYRC/mini-claude-code)](https://img.shields.io/github/stars/DerekYRC/mini-claude-code)
[![Forks](https://img.shields.io/github/forks/DerekYRC/mini-claude-code)](https://img.shields.io/github/forks/DerekYRC/mini-claude-code)

**姊妹版：**
- [**mini-spring**](https://github.com/DerekYRC/mini-spring) **(简化版的spring框架)**
- [**mini-spring-cloud**](https://github.com/DerekYRC/mini-spring-cloud) **(简化版的spring cloud框架)**
- [**mini-netty**](https://github.com/DerekYRC/mini-netty) **(简化版的netty框架)**

## 关于

**mini-claude-code** 是一个简化版的Java版 Claude Code 编程 Agent 项目，能帮助你快速理解编码 Agent 的核心原理。项目抽取了 Agent Harness 的关键机制，**代码尽量精简，保留核心功能**，如 Agent Loop、工具调用、权限控制、Hooks、Todo、Subagent、Skill Loading、上下文压缩、记忆系统、任务系统、后台任务、定时调度、多 Agent 协作、团队协议、自主认领任务和 MCP Plugin 等功能。

项目按章节拆解一个编码 Agent 的核心机制，每章对应一个独立分支，并尽量保留理解当前机制所需的最小代码。

如果本项目能帮助到你，请给个 **STAR**，谢谢！

## 功能

* [s01 Agent Loop](changelog.md#s01one-loop--bash-is-all-you-need)：一个工具 + 一个循环 = 一个 Agent
* [s02 Tool Dispatch](changelog.md#s02加一个工具只加一个-handler)：加一个工具，只加一个 handler
* [s03 Permission](changelog.md#s03先划边界再给自由)：先划边界，再给自由
* [s04 Hooks](changelog.md#s04挂在循环上不写进循环里)：挂在循环上，不写进循环里
* [s05 Todo](changelog.md#s05没有计划的-agent-走哪算哪)：没有计划的 agent 走哪算哪
* [s06 Subagent](changelog.md#s06大任务拆小每个小任务干净的上下文)：大任务拆小，每个小任务干净的上下文
* [s07 Skill Loading](changelog.md#s07用到时再加载别全塞-prompt-里)：用到时再加载，别全塞 prompt 里
* [s08 Context Compact](changelog.md#s08上下文总会满要有办法腾地方)：上下文总会满，要有办法腾地方
* [s09 Memory](changelog.md#s09记住该记的忘掉该忘的)：记住该记的，忘掉该忘的
* [s10 Task System](changelog.md#s10task-system)：大目标拆成小任务，排好序，持久化
* [s11 Background Tasks](changelog.md#s11background-tasks)：慢操作丢后台，agent 继续思考
* [s12 Cron Scheduler](changelog.md#s12cron-scheduler)：定时触发，不需要人推
* [s13 Agent Teams](changelog.md#s13agent-teams)：一个搞不定，组队来
* [s14 Team Protocols](changelog.md#s14队友之间要有约定)：队友之间要有约定
* [s15 Autonomous Agents](changelog.md#s15队友自己看板有活就认领)：队友自己看板，有活就认领
* [s16 MCP Plugin](changelog.md#s16能力不够插上-mcp)：能力不够，插上 MCP

## 使用方法

### 1. 准备环境

本项目需要 Java 17 和 Maven。

运行真实模型 demo 前，先在 shell 中设置：

```sh
export ANTHROPIC_BASE_URL='你的 Anthropic 兼容 API Base URL，如: https://api.deepseek.com/anthropic'
export MODEL_ID='你的模型 ID'
export ANTHROPIC_API_KEY='你的 API Key'
```

不要把 API Key 写入仓库文件。

### 2. 切换章节分支

每章对应一个教学分支。比如学习 s01：

```sh
git switch s01-agent-loop
```

### 3. 启动 demo

使用 Maven 启动指定 demo：

```sh
mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.S01AgentLoopDemo
```

### 4. 阅读源码说明

阅读 [changelog.md](changelog.md)。

`changelog.md` 会按章节说明：

* 本章新增了哪些源码
* 为什么这样设计
* 和前一章相比变了什么
* 如何启动真实 API demo
* 可以尝试哪些 smoke test prompt

## 提问

[点此提问](https://github.com/DerekYRC/mini-claude-code/issues)

## 贡献

欢迎 Pull Request。

## 参考项目

- [learn-claude-code](https://github.com/shareAI-lab/learn-claude-code)(强烈推荐)

