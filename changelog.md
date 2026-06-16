# mini-claude-code changelog

本文件按章节讲解 `mini-claude-code` 的源码改动、核心知识和测试验证。

## 阶段一：基础 Agent Harness

阶段一包含 s01-s05：

- s01 Agent Loop：一个工具 + 一个循环 = 一个 Agent，分支 `s01-agent-loop`
- s02 Tool Dispatch：加一个工具，只加一个 handler，分支 `s02-tool-dispatch`
- s03 Permission：先划边界，再给自由，分支 `s03-permission`
- s04 Hooks：挂在循环上，不写进循环里，分支 `s04-hooks`
- s05 Todo：没有计划的 agent 走哪算哪，分支 `s05-todo`

## s01：One loop & Bash is all you need

**教学分支：** `s01-agent-loop`

本章只回答一个问题：一个 Agent 最小需要什么？

答案是：

- 一段告诉模型“你是谁、当前工作目录在哪、什么时候用工具”的 `system` prompt
- 一个能和模型对话的 `LlmClient`
- 一个能描述和执行工具的 `Tool`
- 一个负责“模型 -> 工具 -> 模型”的 `AgentLoop`

### 核心流程

`AgentLoop.run()` 做的事情很少：

1. demo 把用户输入追加到 `history`，支持连续输入。
2. 调用 `LlmClient.chat()`，把历史消息和工具定义发给模型。
3. 如果模型返回 `tool_use`，找到同名工具并执行。
4. 把执行结果包装成 `tool_result`，继续发给模型。
5. 如果模型不再要求工具调用，就返回最终回答。

这就是最小 Agent loop。它没有权限系统、没有 hook、没有 todo，也没有多工具注册中心；这些机制会在后续章节单独出现。

### 真实 API 适配

`AnthropicLlmClient` 使用 Hutool HTTP 调用 Anthropic Messages 兼容接口，并使用 FastJSON 组装和解析 JSON。

请求顶层会带上最小 system prompt：

- 参考 s01 原始实现的风格：`You are a coding agent at <workdir>. Use bash to solve tasks. Act, don't explain.`
- `<workdir>` 来自当前 Java 进程工作目录，对齐 bash 工具实际执行目录。

Java 版没有使用 Anthropic Python SDK，而是用 Hutool 手写 HTTP，因此显式设置 Anthropic Messages API 所需请求头：

- `x-api-key`
- `anthropic-version`
- `content-type`

请求中的工具定义会被序列化成：

- `name`
- `description`
- `input_schema`

响应中的 content block 会被解析为：

- `TextBlock`
- `ThinkingBlock`
- `ToolUseBlock`
- `UnknownBlock`

兼容接口可能返回 `thinking` content block。本章保留 `thinking` 和 `signature`，并在后续 assistant 历史消息中原样序列化回请求，避免丢失模型要求保留的上下文块。

### Bash 工具

`BashTool` 是本章唯一真实工具：

- 输入：`{"command":"pwd"}`
- 执行：`/bin/sh -c <command>`
- 输出：`exit_code=<code>` 加 stdout/stderr

它故意不做权限判断。权限边界会放到 s03，让读者能单独看到“先判断能不能做，再决定要不要问用户”的机制。

### 验证

本项目按主人要求直接启动 demo 连接真实 API 进行验证，不再保留单元测试。编译命令：

```sh
mvn package -DskipTests
```

测试：

- 启动demo：`org.miniclaudecode.demo.s01.S01AgentLoopDemo`
- 输入：

```text
创建一个输出hello world的python文件
```
- 观察控制台输出
也可以试一个更贴近编码 agent 的例子：创建一个输出 `hello world` 的 Python 文件。


预期观察：

- 模型调用 `bash` 创建文件，例如写入 `print("hello world")`。
- 控制台出现 `Tool> bash ...` 和 `ToolResult> exit_code=0`。
- 运行 `python3 hello.py` 时输出 `hello world`。

### 源码注释补充

本章为核心源码补充了中文注释，重点解释 Agent 循环、bash 工具、Anthropic Messages 请求/响应转换，以及真实 API 返回 thinking/unknown content block 时为什么要保留这些结构。

当前分支已不包含 `AnthropicConfig.timeoutMillis`。
