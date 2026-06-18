# 扁平化章节 Demo 包路径 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 将每章 demo 类从 `org.miniclaudecode.demo.sXX` 移到 `org.miniclaudecode.demo`，并让本地 `run.sh` 按章节号继续运行对应 demo。

**Architecture:** demo 类仍保留 `SXX...Demo` 命名，章节信息由类名前缀承载，不再由包路径承载。`run.sh` 从 `demo` 根目录查找以章节号开头且含 `main` 方法的 Java 文件，再推导全限定类名。

**Tech Stack:** Java、Maven、Bash、Git 章节分支。

---

### Task 1: 基线验证

**Files:**
- Inspect: `/Users/planb4freedom/vscode/hello-claude-code/mini-claude-code/src/main/java/org/miniclaudecode/demo`
- Inspect: `/Users/planb4freedom/vscode/hello-claude-code/mini-claude-code/run.sh`

- [ ] **Step 1: 确认当前分支与干净状态**

Run: `git status --short --branch`
Expected: 当前位于某个章节分支，且没有已跟踪文件的未提交改动。

- [ ] **Step 2: 记录 RED 验证**

Run: `mvn -q compile exec:java -Dexec.mainClass=org.miniclaudecode.demo.S08ContextCompactDemo`
Expected: 失败，原因是新包名下的主类尚不存在。

### Task 2: 逐章节分支扁平化

**Files:**
- Modify: `/Users/planb4freedom/vscode/hello-claude-code/mini-claude-code/src/main/java/org/miniclaudecode/demo/sXX/SXX...Demo.java`
- Modify: `/Users/planb4freedom/vscode/hello-claude-code/mini-claude-code/README.md`

- [ ] **Step 1: 切到章节分支**

Run: `git checkout sXX-...`
Expected: 成功切换到目标章节分支。

- [ ] **Step 2: 移动该分支已有的 demo 类**

将 `src/main/java/org/miniclaudecode/demo/sXX/SXX...Demo.java` 移动到 `src/main/java/org/miniclaudecode/demo/SXX...Demo.java`。
Expected: 原 `sXX` 目录为空或不存在，demo 类位于 `demo` 根目录。

- [ ] **Step 3: 更新包名和文档引用**

将 Java 文件首行从 `package org.miniclaudecode.demo.sXX;` 改成 `package org.miniclaudecode.demo;`。
将 `README.md` 中的 `org.miniclaudecode.demo.sXX.SXX...Demo` 改成 `org.miniclaudecode.demo.SXX...Demo`。

- [ ] **Step 4: 编译验证**

Run: `mvn -q compile`
Expected: 编译通过。

- [ ] **Step 5: 提交章节分支**

Run: `git add src/main/java/org/miniclaudecode/demo README.md`
Run: `git commit -m "refactor: flatten demo package paths"`
Expected: 分支产生一个提交。

### Task 3: 合并回 main

**Files:**
- Modify: `/Users/planb4freedom/vscode/hello-claude-code/mini-claude-code/src/main/java/org/miniclaudecode/demo`
- Modify: `/Users/planb4freedom/vscode/hello-claude-code/mini-claude-code/README.md`

- [ ] **Step 1: 切到 main 并按章节顺序合并**

Run: `git checkout main`
Run: `git merge sXX-...`
Expected: 每个章节分支合并成功；如 README 发生重复冲突，只保留 main 已有前序章节内容并带入当前章节新增内容。

- [ ] **Step 2: 验证 main 汇总结果**

Run: `find src/main/java/org/miniclaudecode/demo -type f -name '*Demo.java' | sort`
Expected: `S01` 到 `S09` demo 都直接位于 `demo` 根目录，没有 `demo/sXX` 子目录。

- [ ] **Step 3: 编译验证**

Run: `mvn -q compile`
Expected: 编译通过。

### Task 4: 更新本地 run.sh

**Files:**
- Modify: `/Users/planb4freedom/vscode/hello-claude-code/mini-claude-code/run.sh`

- [ ] **Step 1: 改为从 demo 根目录查找章节 demo**

`run.sh s08` 应查找 `src/main/java/org/miniclaudecode/demo/S08*.java`，并继续要求只有一个包含 `main` 方法的入口类。

- [ ] **Step 2: 脚本验证**

Run: `bash -n run.sh`
Expected: Bash 语法检查通过。

Run: `./run.sh s08`
Expected: 能定位到 `org.miniclaudecode.demo.S08ContextCompactDemo` 并进入 Maven 执行阶段。
