# mini-claude-code changelog

本文件按章节讲解 `mini-claude-code` 的源码改动、核心知识和测试验证。

## 阶段一：基础 Agent Harness

阶段一包含 s01-s05：

- s01 Agent Loop：一个工具 + 一个循环 = 一个 Agent，分支 `s01-agent-loop`
- s02 Tool Dispatch：加一个工具，只加一个 handler，分支 `s02-tool-dispatch`
- s03 Permission：先划边界，再给自由，分支 `s03-permission`
- s04 Hooks：挂在循环上，不写进循环里，分支 `s04-hooks`
- s05 Todo：没有计划的 agent 走哪算哪，分支 `s05-todo`
