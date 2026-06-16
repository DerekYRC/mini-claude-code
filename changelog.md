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

## s02：加一个工具，只加一个 handler

**教学分支：** `s02-tool-dispatch`

s02 只解决一个问题：工具越来越多时，主循环不能因为“新增工具”继续变胖。

本章新增：

- `ToolRegistry`：一个最小 dispatch map，负责 `register(tool)`、`find(name)` 和导出 `definitions()`。
- `ReadFileTool`：读取文件内容，工具名是 `read_file`。
- `WriteFileTool`：写入文件内容，工具名是 `write_file`。
- `EditFileTool`：替换文件中的精确文本一次，工具名是 `edit_file`。
- `GlobTool`：按 glob pattern 查找文件，工具名是 `glob`。
- `S02ToolDispatchDemo`：注册 `bash/read_file/write_file/edit_file/glob`，外层仍然循环读输入。

### 核心变化

s01 的循环结构不变：

```text
LLM -> tool_use -> execute tool -> tool_result -> LLM
```

s02 只把“按工具名找执行器”从循环里抽到 `ToolRegistry`：

```text
ToolRegistry
  bash       -> BashTool
  read_file  -> ReadFileTool
  write_file -> WriteFileTool
  edit_file  -> EditFileTool
  glob       -> GlobTool
```

所以新增工具时，只需要：

1. 写一个实现 `Tool` 的类。
2. 在 demo 里 `registry.register(new XxxTool(...))`。

### 五个工具

本章对齐参考项目 s02 的工具集合：

```text
bash       Run a shell command.
read_file  Read file contents.
write_file Write content to file.
edit_file  Replace text in file once.
glob       Find files by pattern.
```

`read_file` 输入：

```json
{"path":"README.md","limit":3}
```

`write_file` 输入：

```json
{"path":"target/s02-demo.txt","content":"old hello"}
```

`edit_file` 输入：

```json
{"path":"target/s02-demo.txt","old_text":"old","new_text":"new"}
```

`glob` 输入：

```json
{"pattern":"target/s02-*.txt"}
```

文件类工具的共同边界：

- 路径必须留在 workdir 内，避免读取工作目录之外的文件。
- `read_file` 的 `limit` 可选，用来限制返回行数。
- `write_file` 会自动创建父目录。
- `edit_file` 只替换第一次出现的精确文本。

权限系统仍然不在 s02 实现；“能不能做、要不要问用户”会放到 s03。

### 验证

编译命令：

```sh
mvn package -DskipTests
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
```

启动 demo：

```sh
java -cp "target/classes:$(cat target/classpath.txt)" org.miniclaudecode.demo.s02.S02ToolDispatchDemo
```

真实 API smoke test：

- prompt：`请依次使用 write_file、edit_file、read_file、glob 工具：先写入 target/s02-demo.txt，内容为 old hello；再把 old 替换为 new；然后读取文件内容；最后用 glob 查找 target/s02-*.txt，并回答最终文件内容和匹配结果。`
- 预期观察：控制台出现 `Tool> write_file ...`、`Tool> edit_file ...`、`Tool> read_file ...`、`Tool> glob ...`，最终回答包含 `new hello` 和 `target/s02-demo.txt`。

### 源码注释补充

本章为核心源码补充了中文注释，重点解释本章机制挂载在 Agent 循环中的位置、新增类的职责边界，以及为了保持教学最小化而刻意没有实现的能力。

同时移除了 `AnthropicConfig.timeoutMillis`，保持 LLM 配置只包含本章需要理解的字段。
