# mini-claude-code

`mini-claude-code` 是一个 Java 版 Claude Code harness 学习项目。项目按章节拆解 Agent Harness 的核心机制，每章只保留理解当前机制所需的最小代码。

## 环境变量

运行真实模型 demo 前，先在 shell 中设置：

```sh
export ANTHROPIC_BASE_URL="你的 Anthropic 兼容 API Base URL"
export MODEL_ID="你的模型 ID"
export ANTHROPIC_API_KEY="你的 API Key"
```

不要把 API Key 写入仓库文件。

所有章节都直接启动 demo 连接真实 Anthropic 兼容 API 进行验证，不再编写单元测试。不要把 API Key 写入仓库文件。

## 学习路线

阶段一实现：

- s01 Agent Loop
- s02 Tool Dispatch
- s03 Permission
- s04 Hooks
- s05 Todo
- s06 Subagent
- s07 Skill Loading

## 分支学习

每章对应一个教学分支：

- `s01-agent-loop`
- `s02-tool-dispatch`
- `s03-permission`
- `s04-hooks`
- `s05-todo`
- `s06-subagent`
- `s07-skill-loading`

切换示例：

```sh
git switch s01-agent-loop
```

详细讲解见 `changelog.md`。

## 运行 s01

运行 demo：

```sh
mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.s01.S01AgentLoopDemo
```

试试这些 prompt：

1. `创建一个名为 hello.py 的文件，内容是打印 "Hello, World!"`
2. `列出当前目录中的所有 Python 文件`
3. `当前 git 分支是什么？`

观察重点：什么时候出现 `Tool> bash`，什么时候模型不再调用工具并结束循环。

## 运行 s02

s02 在 s01 基础上加入 `ToolRegistry`，并注册 `bash/read_file/write_file/edit_file/glob` 五个工具。

```sh
mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.s02.S02ToolDispatchDemo
```

试试这些 prompt：

1. `读取 README.md，并告诉我这个项目是做什么的`
2. `创建一个名为 test.py 的文件，内容是打印 "hello"，然后再读取它确认内容`
3. `查找当前目录中的所有 Java 文件`
4. `同时读取 README.md 和 pom.xml，然后创建一个 summary.md 总结文件`

观察重点：模型什么时候只调一个工具，什么时候一次调多个工具；多个工具调用的顺序和结果是否正确。

## 运行 s03

s03 在工具执行前加入权限管线：硬阻止列表、规则匹配、用户确认。

```sh
mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.s03.S03PermissionDemo
```

试试这些 prompt：

1. `在当前目录创建一个名为 test.txt 的文件`
2. `删除 /tmp 目录中的所有临时文件`
3. `当前目录里有哪些文件？`
4. `尝试把一个文件写入 /etc/something`

观察重点：哪些操作直接通过，哪些需要你确认，哪些会被直接拒绝。

## 运行 s04

s04 把权限、日志、输出检查、停止统计挂到 Hook 上，主循环只负责触发事件。

```sh
mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.s04.S04HooksDemo
```

试试这些 prompt：

1. `读取 README.md`
2. `创建一个名为 test.txt 的文件`
3. `删除 /tmp 目录中的所有临时文件`

观察重点：每次工具执行前是否出现 `[HOOK]` 日志；权限被拒时，是 hook 拦截的还是循环里硬编码的。

## 运行 s05

s05 加入 `todo_write` 工具，让 Agent 在多步骤任务前先写计划，并在执行中更新状态。

```sh
mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.s05.S05TodoDemo
```

可以试这个输入，观察是否先出现 `Tool> todo_write`：

```text
请务必先调用 todo_write 工具，写入两个任务：检查 s05 demo 为 in_progress，总结结果为 pending。然后只回答 todo_write 的工具结果。
```

## 运行 s06

s06 加入 `task` 工具，让父 Agent 把复杂子任务交给一个干净上下文的子 Agent。子 Agent 不注册 `task`，只返回最终摘要。

```sh
mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.s06.S06SubagentDemo
```

可以试这个输入，观察是否出现 `Tool> task` 和 `[Subagent spawned]`：

```text
请调用 task 工具，description 参数必须完整写成：请调用 bash 工具执行命令 printf s06-subagent-ok，然后返回这个命令的输出。父 Agent 最后只回答子 Agent 摘要。
```

## 运行 s07

s07 启动时扫描 `skills/*/SKILL.md`，只把技能名和一句描述放进 system prompt。模型需要细节时调用 `load_skill`，通过 tool_result 注入 `<skill name="...">正文</skill>`。

```sh
mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.s07.S07SkillLoadingDemo
```

可以试这个输入，观察是否出现 `Tool> load_skill`：

```text
请调用 load_skill 加载 code-review 技能，然后只回答这个技能的 name 和第一句说明。
```

## 参考项目

本项目参考 [shareAI-lab/learn-claude-code](https://github.com/shareAI-lab/learn-claude-code)。

建议这样搭配学习：

1. 先阅读 `learn-claude-code` 对应章节，理解 Claude Code harness 机制的设计动机。
2. 再切换到 `mini-claude-code` 对应分支，阅读 Java 最小实现。
3. 最后对照本项目 `changelog.md`，查看本章保留了哪些核心代码、删掉了哪些无关机制。

`learn-claude-code` 更适合理解完整机制和 Python 教学实现；`mini-claude-code` 更适合用 Java 阅读最小源码、运行 demo 和观察分支快照。
