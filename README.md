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

试试这些 prompt：

1. `创建一个名为 hello.py 的文件，内容是打印 "Hello, World!"`
2. `列出当前目录中的所有 Python 文件`
3. `当前 git 分支是什么？`

观察重点：什么时候出现 `Tool> bash`，什么时候模型不再调用工具并结束循环。

## 参考项目

本项目参考 [shareAI-lab/learn-claude-code](https://github.com/shareAI-lab/learn-claude-code)。

建议这样搭配学习：

1. 先阅读 `learn-claude-code` 对应章节，理解 Claude Code harness 机制的设计动机。
2. 再切换到 `mini-claude-code` 对应分支，阅读 Java 最小实现。
3. 最后对照本项目 `changelog.md`，查看本章保留了哪些核心代码、删掉了哪些无关机制。

`learn-claude-code` 更适合理解完整机制和 Python 教学实现；`mini-claude-code` 更适合用 Java 阅读最小源码、运行测试和观察分支快照。
