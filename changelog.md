# mini-claude-code changelog

本文件按章节讲解 `mini-claude-code` 的源码改动、核心知识和测试验证。

## 阶段一：基础 Agent Harness

阶段一包含 s01-s12：

- s01 Agent Loop：一个工具 + 一个循环 = 一个 Agent，分支 `s01-agent-loop`
- s02 Tool Dispatch：加一个工具，只加一个 handler，分支 `s02-tool-dispatch`
- s03 Permission：先划边界，再给自由，分支 `s03-permission`
- s04 Hooks：挂在循环上，不写进循环里，分支 `s04-hooks`
- s05 Todo：没有计划的 agent 走哪算哪，分支 `s05-todo`
- s06 Subagent：大任务拆小，每个小任务干净的上下文，分支 `s06-subagent`
- s07 Skill Loading：用到时再加载，别全塞 prompt 里，分支 `s07-skill-loading`
- s08 Context Compact：上下文总会满，要有办法腾地方，分支 `s08-context-compact`
- s09 Memory：记住该记的，忘掉该忘的，分支 `s09-memory`
- s10 Task System：大目标拆成小任务，排好序，持久化，分支 `s10-task-system`
- s11 Background Tasks：慢操作丢后台，agent 继续思考，分支 `s11-background-tasks`
- s12 Cron Scheduler：定时触发，不需要人推，分支 `s12-cron-scheduler`

## s01：One loop & Bash is all you need

**教学分支：** `s01-agent-loop`

本章只回答一个问题：一个 Agent 最小需要什么？

答案是：

- 一段告诉模型「你是谁、当前工作目录在哪、什么时候用工具」的 `system` prompt
- 一个封装了 Anthropic Messages API 的 `LlmClient`
- 一个能描述和执行工具的 `Tool`
- 一个负责「模型 → 工具 → 模型」的 `AgentLoop`

### 核心流程

`AgentLoop.run()` 做的事情很少：

1. 把用户输入追加到 `history`，支持连续输入。
2. 调用 `LlmClient.chat()`，把历史消息和工具定义发给模型。
3. 如果模型返回 `tool_use`，找到同名工具并执行。
4. 把执行结果包装成 `tool_result`，继续发给模型。
5. 如果模型不再要求工具调用，就返回最终回答。

这就是最小 Agent 循环。它没有权限系统、没有 hook、没有 todo，也没有多工具注册中心——这些机制会在后续章节单独出现。

### 真实 API 适配

`AnthropicLlmClient` 使用 Hutool HTTP 调用 Anthropic Messages 兼容接口，并使用 FastJSON 组装和解析 JSON。

请求顶层携带最小 system prompt：

- 风格参考 s01 原始实现：`You are a coding agent at <workdir>. Use bash to solve tasks. Act, don't explain.`
- `<workdir>` 来自当前 Java 进程工作目录，对齐 bash 工具实际执行目录。

Java 版没有使用 Anthropic Python SDK，而是用 Hutool 手写 HTTP，显式设置以下请求头：

- `x-api-key`
- `content-type`

工具定义序列化字段：

- `name`
- `description`
- `input_schema`

响应 content block 解析为：

- `TextBlock`
- `ThinkingBlock`
- `ToolUseBlock`
- `UnknownBlock`

兼容接口可能返回 `thinking` content block。本章保留 `thinking` 和 `signature`，在后续 assistant 历史消息中原样序列化回请求，避免丢失模型要求保留的上下文块。

### Bash 工具

`BashTool` 是本章唯一真实工具：

- 输入：`{"command":"pwd"}`
- 执行：`/bin/sh -c <command>`
- 输出：`exit_code=<code>` 加 stdout/stderr

它故意不做权限判断。权限边界会放到 s03，让读者能单独看到「先判断能不能做，再决定要不要问用户」的机制。

### 验证

本项目直接启动 demo 连接真实 API 验证，不保留单元测试。启动命令：

```sh
git switch s01-agent-loop

export ANTHROPIC_BASE_URL='https://api.deepseek.com/anthropic'
export MODEL_ID='deepseek-v4-pro'
export ANTHROPIC_API_KEY='你的 API Key'

mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.S01AgentLoopDemo
```

真实 API smoke test：

试试这些 prompt：

1. `创建一个名为 hello.py 的文件，内容是打印 "Hello, World!"`
2. `列出当前目录中的所有 Python 文件`
3. `当前 git 分支是什么？`

预期观察：

- 模型需要触碰真实环境时调用 `bash`，例如创建文件、执行 `find` 或查看 git 分支。
- 控制台出现 `Tool> bash ...` 和 `ToolResult> exit_code=0`。
- 模型拿到足够信息后不再调用工具，循环结束并返回最终回答。

### 源码注释补充

本章为核心源码补充了中文注释，重点解释 Agent 循环的执行流程、bash 工具的参数和输出格式，以及 Anthropic Messages 请求/响应的 JSON 转换逻辑。

当前分支已不包含 `AnthropicConfig.timeoutMillis`。

### 提示词位置调整

本章把 system prompt 从 `AnthropicConfig` 移到 `S01AgentLoopDemo`，让读者打开章节入口类就能看到模型的工作目录和 bash 使用约束。

## s02：加一个工具，只加一个 handler

**教学分支：** `s02-tool-dispatch`

s02 只解决一个问题：工具越来越多时，Agent 循环不能因为「新增工具」继续变胖。

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

mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.S02ToolDispatchDemo
```

真实 API smoke test：

试试这些 prompt：

1. `读取 README.md，并告诉我这个项目是做什么的`
2. `创建一个名为 test.py 的文件，内容是打印 "hello"，然后再读取它确认内容`
3. `查找当前目录中的所有 Java 文件`
4. `同时读取 README.md 和 pom.xml，然后创建一个 summary.md 总结文件`

预期观察：

- 简单读取通常出现 `Tool> read_file ...`。
- 创建并回读文件会连续出现 `Tool> write_file ...` 和 `Tool> read_file ...`。
- 查找文件会出现 `Tool> glob ...`，多文件总结可能一次触发多个工具调用。

### 源码注释补充

本章为核心源码补充了中文注释，重点解释 `ToolRegistry` 的 dispatch 机制和五个工具的职责边界。

### 提示词位置调整

本章将 system prompt 作为 `S02ToolDispatchDemo` 顶部的 `SYSTEM_PROMPT` 静态变量，方便读者先看到模型被要求使用工具池解决任务。

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

mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.S03PermissionDemo
```

真实 API smoke test：

试试这些 prompt：

1. `在当前目录创建一个名为 test.txt 的文件`
2. `删除 /tmp 目录中的所有临时文件`
3. `当前目录里有哪些文件？`
4. `尝试把一个文件写入 /etc/something`

预期观察：

- 工作区内普通写文件可以直接通过。
- 只读查询可以直接通过。
- `rm`、写入 `/etc` 等危险或越界操作会被权限管线拦截；需要确认时会出现 `Permission>` 和 `Allow? [y/N]`。

### 源码注释补充

本章为核心源码补充了中文注释，重点解释 `PermissionManager` 的三层权限模型和 `ApprovalPrompter` 的交互流程。

### 提示词位置调整

本章将 system prompt 作为 `S03PermissionDemo` 顶部的 `SYSTEM_PROMPT` 静态变量。真正的权限边界仍由 `PermissionManager` 执行，不受提示词影响。

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

mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.S04HooksDemo
```

真实 API smoke test：

试试这些 prompt：

1. `读取 README.md`
2. `创建一个名为 test.txt 的文件`
3. `删除 /tmp 目录中的所有临时文件`

预期观察：

- 每次工具执行前会出现 `[HOOK]` 日志。
- 普通读写通过后会触发 `PostToolUse`。
- 危险命令会被权限 hook 拦截，Agent 循环本身不写死权限规则。

### 源码注释补充

本章为核心源码补充了中文注释，重点解释四类 Hook 事件的触发时机和 `HookManager` 的顺序执行机制。

### 提示词位置调整

本章将 system prompt 作为 `S04HooksDemo` 顶部的 `SYSTEM_PROMPT` 静态变量，提示词内容保持参考项目的工具使用约束。

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

mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.S05TodoDemo
```

真实 API smoke test：

试试这些 prompt：

1. `重构 target/s05-example/hello.py：如果文件不存在，先创建一个简单 hello 函数，然后补充类型标注、docstring 和 main guard`
2. `在 target/s05-example/demo_pkg 下创建一个 Python package，包含 __init__.py、utils.py 和 tests/test_utils.py`
3. `检查 target/s05-example 下的 Python 文件，并修复明显的风格问题`

预期观察：

- 第一次工具调用应优先出现 `Tool> todo_write ...`。
- TODO 会被拆成多步，并且执行过程中状态从 `pending` 变成 `in_progress` / `completed`。
- 后续才会出现写文件、读文件、bash 验证等工具调用。

### 源码注释补充

本章为核心源码补充了中文注释，重点解释 `todo_write` 工具如何在不修改 Agent 循环的前提下为模型提供计划能力。

### 提示词位置调整

本章将 system prompt 作为 `S05TodoDemo` 顶部的 `SYSTEM_PROMPT` 静态变量，提示词内容保持参考项目的计划约束。

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

mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.S06SubagentDemo
```

真实 API smoke test：

试试这些 prompt：

1. `使用一个子任务找出这个项目使用什么构建工具和测试框架`
2. `委托子 Agent 读取 src/main/java/org/miniclaudecode/core 下的 Java 文件，并总结每个文件的作用`
3. `用 task 创建 target/s06-example/string_tools.py，里面包含 slugify(text: str) 函数，然后由父 Agent 验证它`

预期观察：

- 父 Agent 调用 `Tool> task ...` 后出现 `[Subagent spawned]` / `[Subagent done]`。
- 子 Agent 的工具调用以 `[sub] Tool> ...` 输出。
- 父 Agent 不接收子 Agent 的完整过程，只继续处理子 Agent 返回的最终摘要。

### 源码注释补充

本章为核心源码补充了中文注释，重点解释 `TaskTool` 如何创建独立上下文的子 Agent 以及父子 Agent 的消息隔离机制。

### 提示词位置调整

本章将父 Agent 和子 Agent 的 system prompt 分别作为 `PARENT_SYSTEM_PROMPT` 与 `SUBAGENT_SYSTEM_PROMPT` 静态变量，提示词内容保持参考项目的委托边界。

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

mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.S07SkillLoadingDemo
```

真实 API smoke test：

试试这些 prompt：

1. `有哪些技能可用？`
2. `加载 code-review 技能，并遵循它的说明`
3. `我需要做一次代码审查，请先加载相关技能`

预期观察：

- Agent 可以先从 system prompt 中的技能目录知道有哪些技能。
- 需要完整规范时会出现 `Tool> load_skill ...`。
- 加载后，工具结果以 `<skill name="code-review">` 开头，回答会使用对应 skill 的说明。

### 源码注释补充

本章为核心源码补充了中文注释，重点解释 `SkillRegistry` 的两层加载机制和 `load_skill` 工具的按需注入策略。

### 提示词位置调整

本章将 system prompt 模板作为 `S07SkillLoadingDemo` 顶部的 `SYSTEM_PROMPT_TEMPLATE` 静态变量，运行时只填入技能目录。

## s08：上下文总会满，要有办法腾地方

**教学分支：** `s08-context-compact`

s08 解决的问题是：工具输出和多轮对话会持续挤占上下文，Agent 需要在 LLM 调用前主动腾地方。最小解法是把压缩做成一条挂在循环前的管线，再用 `compact` 工具提供一次显式触发入口。

本章新增：

- `MessageInspector`：集中判断 `tool_use`、`tool_result` 和消息大小。
- `ToolResultStore`：把超大的工具结果写到 `.task_outputs/tool-results/`。
- `TranscriptStore`：压缩前把完整历史保存到 `.transcripts/`。
- `CompactionPipeline`：按固定顺序执行四层压缩。
- `CompactTool`：暴露 `compact` 工具定义，真正压缩由循环处理。
- `CompactingAgentLoop`：s08 专用循环，在 LLM 前运行压缩管线，并特殊处理 `compact`。
- `S08ContextCompactDemo`：注册 `bash/read_file/write_file/edit_file/glob/load_skill/compact`。

### 四层压缩顺序

s08 的压缩不是按编号跑，而是按成本和信息保真度跑：

```text
toolResultBudget -> snipCompact -> microCompact -> compactHistory
```

含义分别是：

- `toolResultBudget`：最后一条 `tool_result` 太大时，先把原文落盘，只把文件路径和预览留在上下文。
- `snipCompact`：消息数量太多时裁掉中间历史，但不能拆散 `assistant(tool_use)` 和后续 `user(tool_result)`。
- `microCompact`：保留最近 3 条工具结果，旧工具结果改成短占位符。
- `compactHistory`：最后才额外调用 LLM 生成摘要，并在摘要前保存完整 transcript。

这个顺序很重要：便宜的、保真度高的先做，昂贵的摘要最后才做。

### compact 工具

`compact` 工具只提供模型可见的工具定义：

```json
{
  "name": "compact",
  "description": "Summarize earlier conversation to free context space.",
  "input_schema": {
    "type": "object",
    "properties": {
      "focus": {"type": "string"}
    }
  }
}
```

普通工具拿不到 `messages`，所以 `CompactTool.execute()` 不真正压缩。`CompactingAgentLoop` 看到模型调用 `compact` 后，会先摘要历史，再把 compact 的 `tool_use/tool_result` 配对补回消息，避免下一轮 Anthropic API 收到不完整工具调用。

### 验证

启动命令：

```sh
git switch s08-context-compact

export ANTHROPIC_BASE_URL='https://api.deepseek.com/anthropic'
export MODEL_ID='deepseek-v4-pro'
export ANTHROPIC_API_KEY='你的 API Key'

mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.S08ContextCompactDemo
```

真实 API smoke test：

试试这些 prompt：

1. `读取 README.md，然后读取 changelog.md，再读取 src/main/java/org/miniclaudecode/demo/s01/S01AgentLoopDemo.java`
2. `读取 src/main/java/org/miniclaudecode/compact 下的每个文件`
3. 反复对话 20 轮以上，观察是否出现 `[auto compact]` 或 `[reactive compact]`

预期观察：

- 旧 `tool_result` 会被压成 `[Earlier tool result compacted. Re-run if needed.]`。
- 大工具结果会落盘到 `.task_outputs/tool-results/`。
- 自动或手动压缩会保存 `.transcripts/transcript_*.jsonl`。

本次自动验证使用更短的显式 compact prompt：

```text
请调用 compact 工具，focus 参数写 s08-smoke，然后只回答压缩已完成。
```

预期控制台出现 `Tool> compact ...`、`[transcript saved: ...]` 和 `[Compacted. History summarized.]`。

## s09：记住该记的，忘掉该忘的

**教学分支：** `s09-memory`

s09 解决的问题是：Agent 不能把所有历史都塞进上下文，但也不能忘掉稳定偏好、项目事实和用户反馈。最小解法是把记忆存成文件，用索引常驻 prompt，用正文按需注入。

本章新增：

- `Memory`：普通 Java 数据类，保存文件名、名称、描述、类型和正文。
- `MemoryStore`：管理 `.memory/*.md` 和 `.memory/MEMORY.md` 索引。
- `MemorySelector`：根据当前对话选择最多 5 条相关记忆，LLM 选择失败时用关键词降级。
- `MemoryExtractor`：每轮结束后从近期对话提取用户偏好、约束、项目事实或引用。
- `MemoryConsolidator`：记忆文件达到阈值后合并去重。
- `MemoryManager`：组合筛选、提取、整理三个子系统。
- `MemoryAgentLoop`：包住 s08 的压缩循环，在停止后触发记忆提取。
- `S09MemoryDemo`：注册 `bash/read_file/write_file/edit_file/glob/task`，并在每轮开始重建带记忆索引的 system prompt。

### 三个子系统

s09 的记忆系统拆成三块：

```text
筛选：根据当前请求，从 MEMORY.md 索引中选择相关记忆文件
提取：每轮结束后，从压缩前上下文提取新记忆
整理：文件数量达到阈值后，合并重复或过期记忆
```

`MEMORY.md` 只是一份便宜索引，格式类似：

```markdown
- [user-preference-tabs](user-preference-tabs.md) - User prefers tabs for indentation
```

完整正文仍保存在独立 Markdown 文件里：

```markdown
---
name: user-preference-tabs
description: User prefers tabs for indentation
type: user
---

User prefers using tabs, not spaces, for indentation.
```

这样每轮 system prompt 只带索引，真正相关时才把正文包进 `<relevant_memories>`。这和 s07 的技能加载思路相同：先列目录，用到时再展开。

### 循环位置

本章复用 s08 的 `CompactingAgentLoop`，但在外层新增 `MemoryAgentLoop`：

```text
用户输入
  -> 注入相关记忆
  -> 保存压缩前快照
  -> 运行 s08 压缩循环
  -> 本轮结束后提取记忆
```

提取使用压缩前快照，是因为摘要可能抹掉用户偏好和反馈里的细节。教学版没有实现后台 Dream、锁和时间门控，整理只用“文件数量达到 10”这个简单阈值，便于直接观察。

### 验证

启动命令：

```sh
git switch s09-memory

export ANTHROPIC_BASE_URL='https://api.deepseek.com/anthropic'
export MODEL_ID='deepseek-v4-pro'
export ANTHROPIC_API_KEY='你的 API Key'

mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.S09MemoryDemo
```

真实 API smoke test：

试试这些 prompt（分多轮输入，观察记忆的累积和加载）：

1. `我喜欢使用 tabs 缩进，而不是 spaces。请记住这一点。`
2. `创建一个名为 test.py 的 Python 文件`
3. `我之前告诉过你哪些偏好？`
4. `我还喜欢字符串使用单引号，而不是双引号。`

预期观察：

- 第一轮结束后出现 `[Memory: extracted N new memories]`。
- `.memory/` 目录下生成独立记忆文件。
- `.memory/MEMORY.md` 包含偏好索引。
- 后续对话会自动加载相关记忆，并在回答或写文件时体现偏好。

本次自动验证使用更短的两轮 prompt：

```text
请记住：我喜欢 Java demo 使用 tabs 缩进。只回答已记录。
我之前说过什么 Java demo 偏好？请根据记忆回答。
```

预期控制台出现 `[Memory: extracted ...]`，第二轮回答提到 `Java demo` 和 `tabs`。

### 源码注释补充

本章为核心源码补充了中文注释，重点解释为什么索引常驻 prompt、为什么正文按需注入，以及为什么提取要使用压缩前快照。

### 提示词位置调整

本章将父 Agent 和子 Agent 的 system prompt 分别作为 `S09MemoryDemo` 顶部的 `SYSTEM_PROMPT_TEMPLATE` 与 `SUBAGENT_SYSTEM_PROMPT` 静态变量。

父 Agent 的提示词对齐参考实现：说明当前工作目录、可用记忆索引、相关记忆会被注入下方，以及遇到明确偏好或 remember 请求时应提取为记忆。

## s10：大目标拆成小任务，排好序，持久化

**教学分支：** `s10-task-system`

s10 对齐参考项目的 `s12_task_system`。本章解决的问题是：TodoWrite 适合当前会话里的执行清单，但大目标需要跨会话恢复、表达依赖顺序，并让后续多 Agent 协作有一个共同任务图。

本章新增：

- `TaskRecord`：普通 Java 数据类，对应 `.tasks/{id}.json`。
- `TaskStore`：管理 `.tasks/` 目录，负责任务 JSON 读写和列表扫描。
- `TaskService`：实现 `pending -> in_progress -> completed` 状态机和 `blockedBy` 依赖检查。
- `CreateTaskTool`：创建持久化任务，可带上游依赖。
- `ListTasksTool`：列出任务状态、owner 和依赖。
- `GetTaskTool`：读取单个任务完整 JSON。
- `ClaimTaskTool`：认领可开始的任务，设置 owner 并改为 `in_progress`。
- `CompleteTaskTool`：完成任务，并报告刚刚解锁的下游任务。
- `S10TaskSystemDemo`：注册 `bash/read_file/write_file/create_task/list_tasks/get_task/claim_task/complete_task`。

### Task System vs TodoWrite

TodoWrite 和 Task System 是两个独立层：

| | TodoWrite (s05) | Task System (s10) |
|---|---|---|
| 定位 | 当前任务的执行清单 | 可恢复的任务图 |
| 存储 | 会话内存 | `.tasks/{id}.json` |
| 依赖 | 无 | `blockedBy` |
| 生命周期 | 当前会话 | 跨会话保留 |
| 分工 | 不负责任务认领 | `owner` / claim |

所以 s10 没有复用 `todo_write`，也没有注册 s06 的 `task` 子 Agent 工具。本章只展示“持久化任务图”这一件事。

### 持久化格式

每个任务是一个 JSON 文件：

```json
{
  "id": "task_1781770000000_0427",
  "subject": "setup database schema",
  "description": "Create initial database schema",
  "status": "pending",
  "owner": null,
  "blockedBy": []
}
```

`TaskStore` 只扫描 `.tasks/task_*.json`，并按文件名排序。单个坏文件会被跳过，避免一个损坏任务让 demo 无法启动。

### 依赖和状态机

任务状态只有三种：

```text
pending -> in_progress -> completed
```

`claim_task` 是唯一把 `pending` 推进到 `in_progress` 的动作。认领前必须先检查 `blockedBy`：

- 依赖任务不存在：视为 blocked。
- 任一依赖不是 `completed`：视为 blocked。
- 所有依赖都完成：允许认领。

`complete_task` 只允许完成 `in_progress` 任务。完成后会扫描其他 `pending` 任务，把新解锁的下游任务列在 `Unblocked:` 后面。

### 五个工具

`create_task`：

```json
{
  "name": "create_task",
  "description": "Create a new task with optional blockedBy dependencies.",
  "input_schema": {
    "type": "object",
    "properties": {
      "subject": {"type": "string"},
      "description": {"type": "string"},
      "blockedBy": {"type": "array", "items": {"type": "string"}}
    },
    "required": ["subject"]
  }
}
```

`list_tasks` 列出所有任务摘要；`get_task` 返回完整 JSON；`claim_task` 设置 owner 并改为 `in_progress`；`complete_task` 标记完成并报告解锁结果。

### 验证

启动命令：

```sh
git switch s10-task-system

export ANTHROPIC_BASE_URL='https://api.deepseek.com/anthropic'
export MODEL_ID='deepseek-v4-pro'
export ANTHROPIC_API_KEY='你的 API Key'

mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.S10TaskSystemDemo
```

真实 API smoke test：

试试这些 prompt：

1. `创建任务：设置数据库 schema、创建 API endpoints（依赖 schema）、编写测试（依赖 endpoints）、编写文档（依赖 schema）`
2. `列出所有任务和状态`
3. `认领第一个未被阻塞的任务并完成它`
4. `再次列出任务，哪些任务现在被解锁了？`

预期观察：

- 控制台出现 `Tool> create_task`、`Tool> list_tasks`、`Tool> claim_task`、`Tool> complete_task`。
- `.tasks/` 目录下生成多个 `task_*.json` 文件。
- 完成 schema 任务后，依赖 schema 的 API endpoints 和 docs 会出现在 `Unblocked:` 中。

### 源码注释补充

本章为任务核心类补充了中文注释，重点解释为什么存储层不处理状态机、为什么缺失依赖也视为 blocked，以及为什么任务工具只做参数转换和服务调用。

### 提示词位置调整

本章将 system prompt 放在 `S10TaskSystemDemo` 顶部的 `SYSTEM_PROMPT` 静态变量中，内容对齐参考实现的三段：身份、可用工具、工作目录。

## s11：慢操作丢后台，agent 继续思考

**教学分支：** `s11-background-tasks`

本章解决的问题是：`npm install` 要 3 分钟，Agent 干等就是浪费。最小解法是把慢操作扔到 daemon 线程执行，先回占位结果让 Agent 继续工作，后台完成后通知注入下一轮。

本章对齐 `learn-claude-code/s13_background_tasks`。

### 核心变化

s11 在 s10 任务系统基础上新增后台执行路径：

```text
工具调用 → should_run_background?
  ├─ false → 同步执行 → tool_result 立即返回
  └─ true  → daemon 线程 → 占位 tool_result → 下轮 <task_notification>
```

同步 vs 后台：

| | 同步 (s10) | 后台 (s11) |
|---|---|---|
| 慢操作 | Agent 干等 | 后台线程执行 |
| Agent 空闲 | 是 | 否，继续处理 |
| 结果 | 立即返回 | 下轮注入通知 |
| 判断标准 | — | `run_in_background` 参数（模型显式请求），启发式兜底 |

### 工作流程

```
Turn 1:
  LLM → bash "npm install" (run_in_background=true)
  → start_background_task → bg_0001
  → tool_result: "[Background task bg_0001 started]..."
  → LLM: "已把 npm install 放到后台，我先读一下 package.json"

Turn 2:
  LLM → read_file "package.json" (同步，毫秒级)
  → tool_result: 文件内容
  → collect_background_results: bg_0001 完成！注入 <task_notification>
  → LLM 在同一条消息里看到: package.json 内容 + npm install 完成通知
```

Agent 没有干等 `npm install`，后台执行期间它去读了配置文件。

### 本章新增

- `BackgroundTask`：后台任务状态数据类（`bgId`、`toolUseId`、`command`、`status`）。
- `BackgroundDecider`：判断逻辑——显式 `run_in_background=true` 优先，关键词启发式兜底（`install/build/test/deploy/compile/docker build/pip install/npm install/cargo build/pytest/make`）。
- `BackgroundTasks`：daemon 线程管理器，`ConcurrentHashMap` 保证线程安全，`AtomicInteger` 生成唯一 bg_id。
- `BackgroundAgentLoop`：s11 专用循环，内置两条执行路径（同步/后台）和通知注入。
- `S11BackgroundTasksDemo`：注册 8 个工具（bash + 5 任务工具 + read/write），bash 新增 `run_in_background` 参数。

### 判断逻辑

两层判断：

1. 模型显式设置 `run_in_background=true` → 走后台（主路径）。
2. 命令含慢操作关键词 → 关键词兜底（保底路径）。

只对 `bash` 工具生效。其他工具（read_file、write_file 等）永远同步执行。

### 后台任务管理器

`BackgroundTasks`：

- `start(block, registry)`：创建 daemon 线程执行工具，立即返回 `bg_0001` 格式的 ID。
- `collectNotifications()`：收集已完成任务，返回 `<task_notification>` XML 文本列表，从 map 中移除已完成任务。
- 线程安全：`ConcurrentHashMap` + `AtomicInteger`，不加显式锁。
- 超时：bash 工具自身有 120 秒超时，教学版不额外加后台超时控制。
- daemon 线程：进程退出时自动终止。

### 通知格式

后台完成后以 `<task_notification>` XML 注入，不复用原始 `tool_use_id`：

```xml
<task_notification>
  <task_id>bg_0001</task_id>
  <status>completed</status>
  <command>npm install</command>
  <summary>added 245 packages in 45s</summary>
</task_notification>
```

不复用 `tool_use_id` 的原因：原始 tool call 已经用占位 `tool_result` 回复了，后台完成是独立事件，这符合 Messages API 的工具配对语义（一个 `tool_use` 只对应一个 `tool_result`）。

### 循环集成

`BackgroundAgentLoop` 是独立类（不继承 `AgentLoop`），参照 `CompactingAgentLoop` 的模式：

- 每轮 LLM 调用前，先调用 `collectNotifications()` 注入上轮后台完成通知。
- 工具执行时，`BackgroundDecider.shouldRunBackground()` 判断走同步还是后台路径。
- 后台路径：`backgroundTasks.start()` → 占位 tool_result。
- 同步路径：直接 `tool.execute()`。

### 验证

启动命令：

```sh
git switch s11-background-tasks

export ANTHROPIC_BASE_URL='https://api.deepseek.com/anthropic'
export MODEL_ID='deepseek-v4-pro'
export ANTHROPIC_API_KEY='你的 API Key'

mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.S11BackgroundTasksDemo
```

真实 API smoke test，试试这些 prompt：

1. `后台运行 pip list，同时查找当前目录中的所有 Python 文件`
2. `使用 run_in_background 运行 npm install，在等待的同时读取 package.json`
3. `创建一个设置项目的任务，然后在后台运行 pip list`

观察重点：

- 慢操作是否出现 `[background] dispatched bg_0001: ...`？
- 后台执行期间 Agent 是否继续处理其他工具调用？
- 后台完成后是否出现 `[background done] bg_0001: ...` 和 `[inject]`？
- 下一轮是否注入 `<task_notification>`？

### 源码注释补充

本章为核心源码补充了中文注释，重点解释 `shouldRunBackground` 的两层判断、daemon 线程的生命周期、`ConcurrentHashMap` 的线程安全策略，以及为什么通知不复用原始 `tool_use_id`。

### 提示词位置调整

本章将 system prompt 作为 `S11BackgroundTasksDemo` 顶部的 `SYSTEM_PROMPT` 静态变量，提示词中明确告知模型 bash 工具有可选的 `run_in_background` 参数。

## s12：定时触发，不需要人推

**教学分支：** `s12-cron-scheduler`

本章解决的问题是：Agent 只有在用户输入时才会工作。如果想让 Agent 每天早上 9 点自动检查构建状态呢？最小解法是用 cron 表达式定义触发时间，到点自动注入 `[Scheduled]` 消息并运行 Agent。

本章对齐 `learn-claude-code/s14_cron_scheduler`。

### 核心变化

s12 在 s11 后台任务基础上新增定时触发源：

```text
schedule_cron("* * * * *", "检查构建")
  → CronScheduler.schedule()
  → Hutool CronUtil 定时触发
  → CronTask.execute()
  → onFire callback
  → agentLock 内注入 [Scheduled] 消息
  → BackgroundAgentLoop.run()
```

### 本章新增

- `CronJob`：cron 任务数据类（id、cron、prompt、recurring、durable）。
- `CronStore`：只负责 `.scheduled_tasks.json` 读写，只保存 durable=true 的任务。
- `CronScheduler`：委托 Hutool CronUtil 注册/取消任务，触发时回调 `Consumer<CronJob>`。两层 cron 校验：5 段式检查 + `CronPattern.of()` 解析。
- `ScheduleCronTool`、`ListCronsTool`、`CancelCronTool`：三个 cron 工具。
- `S12CronSchedulerDemo`：注册 11 个工具（s11 的 8 个 + 3 个 cron 工具），用 `agentLock` 保护共享 history，cron 触发和用户输入不能同时运行 Agent。

### 设计选择

采用 Hutool CronUtil + Map 方案（方案 C），不引入新依赖、不自写 cron 解析、不写队列和 queue processor。

### 持久化

durable job 写入 `.scheduled_tasks.json`：

```json
[
  {
    "id": "cron_49c8c6",
    "cron": "* * * * *",
    "prompt": "DURABLE_OK",
    "recurring": true,
    "durable": true
  }
]
```

启动时从文件加载并重新注册到 Hutool，恢复定时触发。

### 验证

启动命令：

```sh
git switch s12-cron-scheduler
export ANTHROPIC_BASE_URL='https://api.deepseek.com/anthropic'
export MODEL_ID='deepseek-v4-pro'
export ANTHROPIC_API_KEY='你的 API Key'
mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.S12CronSchedulerDemo
```

真实 API smoke test，试试这些 prompt：

1. `安排一个任务，每 2 分钟打印当前日期`
2. `列出所有 cron jobs`
3. `创建一个 1 分钟后的一次性提醒，用来检查构建状态`
4. `取消这个周期性任务，并用 list_crons 确认`

观察重点：
- 是否出现 `[cron register]` 和 `[cron fire]`？
- cron 到点后即使没有新用户输入，是否自动注入 `[Scheduled]` 并运行 Agent？
- durable job 是否写入并从 `.scheduled_tasks.json` 恢复？
- cancel 后是否出现 `[cron cancel]`？

### 源码注释补充

本章为核心源码补充了中文注释，重点解释 Hutool CronUtil 的委托关系、为什么验证分 5 段检查 + CronPattern 解析两层、以及为什么 cron 触发和用户输入需要用 agentLock 串行化。

### 提示词位置调整

本章将 system prompt 作为 `S12CronSchedulerDemo` 顶部的 `SYSTEM_PROMPT` 静态变量，提示词中新增 cron 三个工具的说明。
