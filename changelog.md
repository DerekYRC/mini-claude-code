# mini-claude-code changelog

本文件按章节讲解 `mini-claude-code` 的源码改动、核心知识和测试验证。

## 阶段一：基础 Agent Harness

阶段一包含 s01-s07：

- s01 Agent Loop：一个工具 + 一个循环 = 一个 Agent，分支 `s01-agent-loop`
- s02 Tool Dispatch：加一个工具，只加一个 handler，分支 `s02-tool-dispatch`
- s03 Permission：先划边界，再给自由，分支 `s03-permission`
- s04 Hooks：挂在循环上，不写进循环里，分支 `s04-hooks`
- s05 Todo：没有计划的 agent 走哪算哪，分支 `s05-todo`
- s06 Subagent：大任务拆小，每个小任务干净的上下文，分支 `s06-subagent`
- s07 Skill Loading：用到时再加载，别全塞 prompt 里，分支 `s07-skill-loading`

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

本项目按主人要求直接启动 demo 连接真实 API 进行验证，不再保留单元测试。启动命令：

```sh
git switch s01-agent-loop

export ANTHROPIC_BASE_URL='https://api.deepseek.com/anthropic'
export MODEL_ID='deepseek-v4-pro'
export ANTHROPIC_API_KEY='你的 API Key'

mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.s01.S01AgentLoopDemo
```

测试：

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

### 提示词位置调整

本章已把 system prompt 从 `AnthropicConfig` 移到 `S01AgentLoopDemo`，让读者打开章节入口类就能看到模型的工作目录和 bash 使用约束。

`AnthropicConfig` 不再提供 `systemPrompt(String workdir)` 默认提示词方法，只负责保存调用真实 API 所需的配置字段。

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

启动命令：

```sh
git switch s02-tool-dispatch

export ANTHROPIC_BASE_URL='https://api.deepseek.com/anthropic'
export MODEL_ID='deepseek-v4-pro'
export ANTHROPIC_API_KEY='你的 API Key'

mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.s02.S02ToolDispatchDemo
```

真实 API smoke test：

- prompt：`请依次使用 write_file、edit_file、read_file、glob 工具：先写入 target/s02-demo.txt，内容为 old hello；再把 old 替换为 new；然后读取文件内容；最后用 glob 查找 target/s02-*.txt，并回答最终文件内容和匹配结果。`
- 预期观察：控制台出现 `Tool> write_file ...`、`Tool> edit_file ...`、`Tool> read_file ...`、`Tool> glob ...`，最终回答包含 `new hello` 和 `target/s02-demo.txt`。

### 源码注释补充

本章为核心源码补充了中文注释，重点解释本章机制挂载在 Agent 循环中的位置、新增类的职责边界，以及为了保持教学最小化而刻意没有实现的能力。

同时移除了 `AnthropicConfig.timeoutMillis`，保持 LLM 配置只包含本章需要理解的字段。

### 提示词位置调整

本章将 system prompt 作为 `S02ToolDispatchDemo` 顶部的 `SYSTEM_PROMPT` 静态变量，方便读者先看到模型被要求使用工具池解决任务。

`AnthropicConfig` 不再提供 `systemPrompt(String workdir)` 默认提示词方法，只负责保存调用真实 API 所需的配置字段。

## s03：先划边界，再给自由

**教学分支：** `s03-permission`

s03 在 s02 的工具执行前加入权限判断。循环的核心仍然是：

```text
LLM -> tool_use -> permission -> execute tool -> tool_result -> LLM
```

本章新增：

- `PermissionManager`：权限管线入口。
- `PermissionDecision`：表达允许或拒绝。
- `ApprovalPrompter` / `ConsoleApprovalPrompter`：当规则需要用户确认时暂停询问。
- `S03PermissionDemo`：注册 s02 的五个工具，并把权限管线挂到 `AgentLoop`。

### 三道门

参考项目 s03 把工具执行前的权限分为三层：

1. 硬阻止列表：例如 `rm -rf /`、`sudo`、`shutdown`、`mkfs`、`dd if=`，永远拒绝。
2. 规则匹配：例如 `rm `、`> /etc/`、`chmod 777`，属于潜在破坏操作。
3. 用户确认：命中规则后打印 `Permission>` 和工具参数，等待用户输入 `y/yes`。

文件写入类工具仍限制在 workdir 内；路径越界直接拒绝。

### 验证

启动命令：

```sh
git switch s03-permission

export ANTHROPIC_BASE_URL='https://api.deepseek.com/anthropic'
export MODEL_ID='deepseek-v4-pro'
export ANTHROPIC_API_KEY='你的 API Key'

mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.s03.S03PermissionDemo
```

真实 API smoke test：

- prompt：`请调用 bash 工具执行：chmod 777 target/s02-demo.txt。出现 Allow? 时等待我的输入。`
- 输入确认：输入 `n`
- 预期观察：控制台出现 `Permission> Potentially destructive command` 和 `Allow? [y/N]`，拒绝后工具不会执行，模型收到 `Permission denied by user...`。

### 源码注释补充

本章为核心源码补充了中文注释，重点解释本章机制挂载在 Agent 循环中的位置、新增类的职责边界，以及为了保持教学最小化而刻意没有实现的能力。

同时移除了 `AnthropicConfig.timeoutMillis`，保持 LLM 配置只包含本章需要理解的字段。

### 提示词位置调整

本章将 system prompt 作为 `S03PermissionDemo` 顶部的 `SYSTEM_PROMPT` 静态变量，方便读者先看到模型层面的权限提示。

真正的权限边界仍由 `PermissionManager` 执行，`AnthropicConfig` 不再提供 `systemPrompt(String workdir)` 默认提示词方法。

## s04：挂在循环上，不写进循环里

**教学分支：** `s04-hooks`

s04 解决的问题是：权限、日志、输出检查、停止统计这些能力都和工具调用相关，但不应该全部写进 `AgentLoop`。

本章新增：

- `HookEvent`：定义四个事件点：`USER_PROMPT_SUBMIT`、`PRE_TOOL_USE`、`POST_TOOL_USE`、`STOP`。
- `HookContext`：在事件触发时携带 prompt、tool_use、tool_result、messages 等上下文。
- `HookDecision`：Hook 可以选择放行，也可以阻止本次工具调用。
- `HookManager`：按事件注册多个 hook，并按顺序触发。
- `S04HooksDemo`：演示把权限、日志、输出检查和停止统计都挂到 hook 上。

### 核心变化

主循环没有写死“权限规则”或“日志格式”，只在固定位置触发 hook：

```text
用户输入 -> UserPromptSubmit
工具执行前 -> PreToolUse
工具执行后 -> PostToolUse
循环停止时 -> Stop
```

`PreToolUse` 的返回值会影响工具是否执行：

- `HookDecision.pass()`：继续执行工具。
- `HookDecision.block(message)`：跳过工具，把 `message` 作为 `tool_result` 回传给模型。

这样权限系统可以从 s03 的主流程判断，迁移成 s04 的一个 hook。以后要加审计日志、敏感信息扫描、输出截断提醒，也只需要注册新的 hook，不需要继续改主循环。

### 本章保留的最小 Hook

`S04HooksDemo` 注册了四类 hook：

- `UserPromptSubmit`：打印当前工作目录。
- `PreToolUse`：一个权限 hook 加一个工具调用日志 hook。
- `PostToolUse`：当工具输出超过阈值时提醒。
- `Stop`：统计本轮对话用了多少次工具。

这些 hook 故意写在 demo 中，便于读者直接看到“扩展点怎么挂上去”。真正项目里可以把它们拆到独立类。

### 验证

启动命令：

```sh
git switch s04-hooks

export ANTHROPIC_BASE_URL='https://api.deepseek.com/anthropic'
export MODEL_ID='deepseek-v4-pro'
export ANTHROPIC_API_KEY='你的 API Key'

mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.s04.S04HooksDemo
```

真实 API smoke test：

- prompt：`请调用 bash 工具执行：printf s04-hook-ok。然后只回答工具输出。`
- 预期观察：控制台出现 `[HOOK] UserPromptSubmit`、`[HOOK] PreToolUse: bash ...`、工具输出 `s04-hook-ok`、以及 `[HOOK] Stop: session used 1 tool calls`。

### 源码注释补充

本章为核心源码补充了中文注释，重点解释本章机制挂载在 Agent 循环中的位置、新增类的职责边界，以及为了保持教学最小化而刻意没有实现的能力。

同时移除了 `AnthropicConfig.timeoutMillis`，保持 LLM 配置只包含本章需要理解的字段。

### 提示词位置调整

本章将 system prompt 作为 `S04HooksDemo` 顶部的 `SYSTEM_PROMPT` 静态变量，提示词内容保持参考项目的工具使用约束。

`AnthropicConfig` 不再提供 `systemPrompt(String workdir)` 默认提示词方法，只负责保存调用真实 API 所需的配置字段。

## s05：没有计划的 agent 走哪算哪

**教学分支：** `s05-todo`

s05 解决的问题是：复杂任务如果不先列步骤，Agent 容易边做边忘。最小解法是把“计划”也做成一个工具，让模型显式写下当前任务列表。

本章新增：

- `TodoItem`：普通 Java 数据类，保存 `content` 和 `status`。
- `TodoWriteTool`：工具名 `todo_write`，用内存保存当前任务列表。
- `S05TodoDemo`：在 s02 的五个工具基础上注册 `todo_write`，并在 system prompt 中要求“多步骤任务先计划，执行中更新状态”。

### 核心变化

s05 没有把计划逻辑写进主循环。循环仍然只做：

```text
LLM -> tool_use -> dispatch -> tool_result -> LLM
```

新增能力来自一个普通工具：

```text
todo_write -> TodoWriteTool -> currentTodos
```

所以本章仍然延续 s02 的原则：加一个工具，只加一个 handler。为了让本章更容易阅读，demo 没有继承 s03 的权限管线和 s04 的 Hook 展示代码，只保留理解 `todo_write` 所需的最小上下文。

### todo_write 输入

`todo_write` 接收完整的当前任务列表：

```json
{
  "todos": [
    {"content": "检查 s05 demo", "status": "in_progress"},
    {"content": "总结结果", "status": "pending"}
  ]
}
```

状态只允许三种：

- `pending`
- `in_progress`
- `completed`

工具会替换当前内存中的 todo 列表，并返回一份可读的任务清单。它只负责记录计划，不直接执行任务。

### 验证

启动命令：

```sh
git switch s05-todo

export ANTHROPIC_BASE_URL='https://api.deepseek.com/anthropic'
export MODEL_ID='deepseek-v4-pro'
export ANTHROPIC_API_KEY='你的 API Key'

mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.s05.S05TodoDemo
```

真实 API smoke test：

- prompt：`请务必先调用 todo_write 工具，写入两个任务：检查 s05 demo 为 in_progress，总结结果为 pending。然后只回答 todo_write 的工具结果。`
- 预期观察：控制台先出现 `Tool> todo_write ...`，工具结果包含 `Updated 2 tasks`、`[in_progress] 检查 s05 demo` 和 `[pending] 总结结果`。

### 源码注释补充

本章为核心源码补充了中文注释，重点解释本章机制挂载在 Agent 循环中的位置、新增类的职责边界，以及为了保持教学最小化而刻意没有实现的能力。

同时移除了 `AnthropicConfig.timeoutMillis`，保持 LLM 配置只包含本章需要理解的字段。

### 提示词位置调整

本章将 system prompt 作为 `S05TodoDemo` 顶部的 `SYSTEM_PROMPT` 静态变量，提示词内容保持参考项目的计划约束。

`AnthropicConfig` 不再提供 `systemPrompt(String workdir)` 默认提示词方法，只负责保存调用真实 API 所需的配置字段。

## s06：大任务拆小，每个小任务干净的上下文

**教学分支：** `s06-subagent`

s06 解决的问题是：父 Agent 的上下文已经很长时，复杂子任务继续塞在同一个 `messages` 里会越来越乱。最小解法是把“委托子任务”做成一个工具，让子 Agent 用全新的上下文独立完成任务，只把最终摘要带回父 Agent。

本章新增：

- `AgentLoop` 的 `maxTurns`：默认仍是 20，子 Agent 可以单独设置为 30。
- `TaskTool`：工具名 `task`，输入是 `description`，内部启动一个子 Agent。
- `S06SubagentDemo`：父工具池包含 `task`，子工具池只包含 `bash/read_file/write_file/edit_file/glob`。

### 核心变化

父 Agent 调用 `task` 后，子 Agent 重新开始一份干净消息列表：

```text
Parent messages[] -> task(description)
                  -> Subagent messages[] = [description]
                  -> Subagent tools: bash/read_file/write_file/edit_file/glob
                  -> summary text
                  -> Parent tool_result
```

关键边界是：子 Agent 不注册 `task` 工具，所以不能继续递归创建子 Agent。子 Agent 的中间工具调用和历史消息也不会带回父 Agent，父 Agent 只收到最终摘要。

本章故意不带 s05 todo 和 s04 hook，只保留理解“干净上下文子 Agent”所需的最小代码。

### 验证

启动命令：

```sh
git switch s06-subagent

export ANTHROPIC_BASE_URL='https://api.deepseek.com/anthropic'
export MODEL_ID='deepseek-v4-pro'
export ANTHROPIC_API_KEY='你的 API Key'

mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.s06.S06SubagentDemo
```

真实 API smoke test：

- prompt：`请调用 task 工具，description 参数必须完整写成：请调用 bash 工具执行命令 printf s06-subagent-ok，然后返回这个命令的输出。父 Agent 最后只回答子 Agent 摘要。`
- 预期观察：控制台出现 `Tool> task ...`、`[Subagent spawned]`、`[sub] Tool> bash ...` 和 `s06-subagent-ok`。

### 源码注释补充

本章为核心源码补充了中文注释，重点解释本章机制挂载在 Agent 循环中的位置、新增类的职责边界，以及为了保持教学最小化而刻意没有实现的能力。

同时移除了 `AnthropicConfig.timeoutMillis`，保持 LLM 配置只包含本章需要理解的字段。

### 提示词位置调整

本章将父 Agent 和子 Agent 的 system prompt 分别作为 `PARENT_SYSTEM_PROMPT` 与 `SUBAGENT_SYSTEM_PROMPT` 静态变量，提示词内容保持参考项目的委托边界。

`AnthropicConfig` 不再提供 `systemPrompt(String workdir)` 默认提示词方法，只负责保存调用真实 API 所需的配置字段。

## s07：用到时再加载，别全塞 prompt 里

**教学分支：** `s07-skill-loading`

s07 解决的问题是：Agent 可能有很多技能说明，但把所有 `SKILL.md` 全塞进 system prompt 会浪费上下文。最小解法是启动时只注入技能目录，真正需要时再通过工具加载正文。

本章新增：

- `Skill`：普通 Java 数据类，保存 `name`、`description` 和 `body`。
- `SkillRegistry`：扫描 `skills/*/SKILL.md`，解析 frontmatter，用 `name/description` 生成目录。
- `LoadSkillTool`：工具名 `load_skill`，通过技能名返回 `<skill name="...">正文</skill>`。
- `S07SkillLoadingDemo`：注册 s02 的五个基础工具和 `load_skill`。
- `skills/code-review/SKILL.md`、`skills/java-cli/SKILL.md`：两个最小示例技能。

### 两层加载

s07 把技能分成两层：

```text
便宜层：system prompt 只放技能目录
昂贵层：load_skill(name) 返回 <skill name="...">正文</skill>
```

`SkillRegistry` 只允许通过已扫描到的技能名查找内容，因此 `load_skill` 不接受任意路径，避免路径穿越。frontmatter 里的 `name` 和 `description` 用于生成目录，真正注入给模型的是正文 body。

本章不注册 s06 的 `task` 工具，目的是让读者专注理解“先列目录，用到再展开”。

### 验证

启动命令：

```sh
git switch s07-skill-loading

export ANTHROPIC_BASE_URL='https://api.deepseek.com/anthropic'
export MODEL_ID='deepseek-v4-pro'
export ANTHROPIC_API_KEY='你的 API Key'

mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.s07.S07SkillLoadingDemo
```

真实 API smoke test：

- prompt：`请调用 load_skill 加载 code-review 技能，然后只回答这个技能的 name 和第一句说明。`
- 预期观察：控制台出现 `Tool> load_skill {"name":"code-review"}`，工具结果以 `<skill name="code-review">` 开头，并包含 `# Code Review Skill`。

### 源码注释补充

本章为核心源码补充了中文注释，重点解释本章机制挂载在 Agent 循环中的位置、新增类的职责边界，以及为了保持教学最小化而刻意没有实现的能力。

同时移除了 `AnthropicConfig.timeoutMillis`，保持 LLM 配置只包含本章需要理解的字段。

### 提示词位置调整

本章将 system prompt 主体作为 `S07SkillLoadingDemo` 顶部的 `SYSTEM_PROMPT_TEMPLATE` 静态变量，运行时只填入技能目录。

`AnthropicConfig` 不再提供 `systemPrompt(String workdir)` 默认提示词方法，只负责保存调用真实 API 所需的配置字段。
