# <img src="assets/cc.png" width="80" height="80"> mini-claude-code

[![Build Status](https://img.shields.io/badge/build-passing-brightgreen)](https://github.com/DerekYRC/mini-claude-code)
[![Java](https://img.shields.io/badge/Java-17-4EB1BA.svg)](https://www.oracle.com/java/)
[![Maven](https://img.shields.io/badge/build-Maven-C71A36.svg)](https://maven.apache.org/)
[![Stars](https://img.shields.io/github/stars/DerekYRC/mini-claude-code)](https://img.shields.io/github/stars/DerekYRC/mini-claude-code)
[![Forks](https://img.shields.io/github/forks/DerekYRC/mini-claude-code)](https://img.shields.io/github/forks/DerekYRC/mini-claude-code)

**English | [简体中文](./README.md)**

**Sister Projects:**
 - [**mini-spring**](https://github.com/DerekYRC/mini-spring) **(simplified Spring framework)**
 - [**mini-spring-cloud**](https://github.com/DerekYRC/mini-spring-cloud) **(simplified Spring Cloud framework)**
 - [**mini-netty**](https://github.com/DerekYRC/mini-netty) **(simplified Netty framework)**

## About

**mini-claude-code** is a simplified Java implementation of the Claude Code programming agent that helps you quickly understand the core principles behind coding agents. The project extracts key mechanisms from the Agent Harness with **extremely simplified code while preserving core functionality**, including Agent Loop, tool dispatch, permission control, hooks, todo planning, subagents, skill loading, context compression, memory systems, task orchestration, background tasks, cron scheduling, multi-agent collaboration, team protocols, autonomous task claiming, and MCP plugins.

Each chapter breaks down one core mechanism of a coding agent, with each chapter on its own branch containing only the minimum code needed to understand that mechanism.

If this project helps you, please give it a **STAR, thank you!**

## Features

* [s01 Agent Loop](changelog.md#s01-agent-loop) — ReAct: Think-Act-Observe, LLM ⇄ Tool loop
* [s02 Tool Dispatch](changelog.md#s02-tool-dispatch) — Tool Use: register-and-use, no loop changes needed
* [s03 Permission](changelog.md#s03-permission) — Guardrails: three-tier check, review before execution
* [s04 Hooks](changelog.md#s04-hooks) — Lifecycle: four event points, cross-cutting logic separated from the loop
* [s05 Todo](changelog.md#s05-todo) — Planning: plan first then execute, trackable state
* [s06 Subagent](changelog.md#s06-subagent) — Delegation: delegate to sub-agents with clean context isolation
* [s07 Skill Loading](changelog.md#s07-skill-loading) — Lazy Skill Loading: index in prompt, body loaded on demand
* [s08 Context Compact](changelog.md#s08-context-compact) — Context Compression: four-layer compaction pipeline when full
* [s09 Memory](changelog.md#s09-memory) — Long-term Memory: what to remember, what to forget, when to consolidate
* [s10 Task System](changelog.md#s10-task-system) — Task Orchestration: task graph + state machine + dependency checks
* [s11 Background Tasks](changelog.md#s11-background-tasks) — Async: slow ops go background, Agent keeps thinking
* [s12 Cron Scheduler](changelog.md#s12-cron-scheduler) — Scheduled Trigger: cron expressions, unattended execution
* [s13 Agent Teams](changelog.md#s13-agent-teams) — Multi-Agent: lead assigns work, teammates execute in parallel
* [s14 Team Protocols](changelog.md#s14-team-protocols) — Structured Communication: request_id + state machine between teammates
* [s15 Autonomous Agents](changelog.md#s15-autonomous-agents) — Self-Organization: teammates scan board, claim unassigned work
* [s16 MCP Plugin](changelog.md#s16-mcp-plugin) — MCP: dynamic tool pools, plug in when capabilities fall short

## Usage

### 1. Prerequisites

Requires Java 17 and Maven.

Before running demos with a real model, set these environment variables in your shell:

```sh
export ANTHROPIC_BASE_URL='Your Anthropic-compatible API Base URL, e.g. https://api.deepseek.com/anthropic'
export MODEL_ID='Your model ID'
export ANTHROPIC_API_KEY='Your API Key'
```

### 2. Switch to a chapter branch

Each chapter has its own teaching branch. For example, to study s01:

```sh
git switch s01-agent-loop
```

### 3. Run the demo

Run a specific demo with Maven:

```sh
mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.S01AgentLoopDemo
```

Or use the provided script:

```sh
./run.sh s01
```

### 4. Read the source notes

Read [changelog.md](changelog.md) or [changelog_en.md](changelog_en.md)

## Questions

[Ask Questions Here](https://github.com/DerekYRC/mini-claude-code/issues)

## Contributing

Pull Requests are welcome.

## Reference

- [learn-claude-code](https://github.com/shareAI-lab/learn-claude-code) (highly recommended)

## Star History

[![Star History Chart](https://api.star-history.com/svg?repos=DerekYRC/mini-claude-code&type=Date)](https://star-history.com/#DerekYRC/mini-claude-code&Date)
