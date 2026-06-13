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

## 分支学习

每章对应一个教学分支：

- `s01-agent-loop`
- `s02-tool-dispatch`
- `s03-permission`
- `s04-hooks`
- `s05-todo`

切换示例：

```sh
git switch s01-agent-loop
```

详细讲解见 `changelog.md`。

## 运行 s01

先编译并生成依赖 classpath：

```sh
mvn package -DskipTests
mvn -q dependency:build-classpath -Dmdep.outputFile=target/classpath.txt
```

再运行 demo：

```sh
java -cp "target/classes:$(cat target/classpath.txt)" org.miniclaudecode.demo.s01.S01AgentLoopDemo
```

可以试这个输入，观察是否出现 `Tool> bash`：

```text
请务必调用 bash 工具执行：pwd。然后只回答工具输出。
```

## 运行 s02

s02 在 s01 基础上加入 `ToolRegistry`，并注册 `bash/read_file/write_file/edit_file/glob` 五个工具。

```sh
java -cp "target/classes:$(cat target/classpath.txt)" org.miniclaudecode.demo.s02.S02ToolDispatchDemo
```

可以试这个输入，观察是否出现 `Tool> write_file`、`Tool> edit_file`、`Tool> read_file` 和 `Tool> glob`：

```text
请依次使用 write_file、edit_file、read_file、glob 工具：先写入 target/s02-demo.txt，内容为 old hello；再把 old 替换为 new；然后读取文件内容；最后用 glob 查找 target/s02-*.txt，并回答最终文件内容和匹配结果。
```

## 运行 s03

s03 在工具执行前加入权限管线：硬阻止列表、规则匹配、用户确认。

```sh
java -cp "target/classes:$(cat target/classpath.txt)" org.miniclaudecode.demo.s03.S03PermissionDemo
```

可以试这个输入，观察是否出现 `Permission>`：

```text
请调用 bash 工具执行：chmod 777 target/s02-demo.txt。出现 Allow? 时等待我的输入。
```

## 参考项目

本项目参考 [shareAI-lab/learn-claude-code](https://github.com/shareAI-lab/learn-claude-code)。

建议这样搭配学习：

1. 先阅读 `learn-claude-code` 对应章节，理解 Claude Code harness 机制的设计动机。
2. 再切换到 `mini-claude-code` 对应分支，阅读 Java 最小实现。
3. 最后对照本项目 `changelog.md`，查看本章保留了哪些核心代码、删掉了哪些无关机制。

`learn-claude-code` 更适合理解完整机制和 Python 教学实现；`mini-claude-code` 更适合用 Java 阅读最小源码、运行测试和观察分支快照。
