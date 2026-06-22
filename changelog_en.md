# mini-claude-code Changelog

## s01 Agent Loop

**Teaching branch:** `s01-agent-loop`

### Problem

An LLM only outputs text — it can't interact with the real environment. What's the minimum needed for a coding agent?

### Function

One Bash tool + one ReAct loop = minimal Agent.

New (18 files):

- **Message model**: `ContentBlock` (abstract base), `TextBlock`, `ThinkingBlock`, `ToolUseBlock`, `ToolResultBlock`, `UnknownBlock`, `Message`, `AssistantMessage`
- **Tool interface**: `Tool` (interface, `getDefinition()` + `execute()`), `ToolDefinition`, `ToolResult`
- **LLM client**: `LlmClient` (interface), `AnthropicLlmClient` (Hutool HTTP implementation), `AnthropicConfig`
- **Loop**: `AgentLoop` (main loop, 101 lines), `AgentLoopListener` (callback interface)
- **Tool**: `BashTool` (the only real tool, `/bin/sh -c <command>`)

### Design

Uses the ReAct pattern. The loop doesn't care what tools are — it does three things:

```text
LLM → tool_use → execute → tool_result → LLM → ... → text reply
```

1. Append user input to history (history managed externally by demo, supporting continuous conversation)
2. Call `LlmClient.chat()` with message history and tool definitions
3. Model returns `tool_use` (indicated by `stop_reason == "tool_use"`) → find tool by name from internal `Map<String, Tool>` → execute → wrap result as `tool_result` appended to history → back to step 2
4. Model returns other `stop_reason` (e.g. `end_turn`) → loop ends, return `AssistantMessage`

Key design decisions:

- **Internal Map dispatch only**: no ToolRegistry (deferred to s02), AgentLoop converts `List<Tool>` to `Map<String, Tool>` in constructor
- **No permission checks**: BashTool executes directly without command auditing. Permission boundary deferred to s03
- **No Anthropic SDK**: uses Hutool HTTP to call Messages API directly, compatible with DeepSeek and other Anthropic-compatible endpoints
- **Thinking block preservation**: compatible endpoints may return `thinking` content blocks — parsed with `thinking` and `signature` preserved, serialized back as-is in subsequent assistant history

### Implementation

`AgentLoop.run(List<Message> messages)` — main loop, s01 branch real code (101 lines):

```java
public AssistantMessage run(List<Message> messages) {
    for (int turn = 0; turn < 20; turn++) {
        // One agent loop round: LLM -> tool_use -> tool_result -> next LLM round
        AssistantMessage response = llmClient.chat(messages, toolDefinitions());
        listener.onAssistantMessage(response);
        messages.add(Message.assistant(response.getContent()));

        // Execute tool calls requested by the model
        List<ToolResultBlock> toolResults = executeToolUses(response);
        if (!"tool_use".equals(response.getStopReason()) || toolResults.isEmpty()) {
            listener.onStop(response);
            return response;
        }

        // tool_result returned with user role — required by Anthropic Messages protocol
        messages.add(Message.toolResults(toolResults));
    }
    throw new IllegalStateException("Agent loop reached max turns");
}

private ToolResult executeTool(ToolUseBlock toolUse) {
    Tool tool = tools.get(toolUse.getName());  // s01: internal Map dispatch, s02 extracts ToolRegistry
    if (tool == null) {
        return new ToolResult("Unknown tool: " + toolUse.getName());
    }
    return tool.execute(toolUse.getInput());
}
```

`BashTool.execute()` — s01 real code (ProcessBuilder, no timeout):

```java
public ToolResult execute(JSONObject input) {
    String command = input == null ? "" : input.getString("command");
    if (command == null || command.isBlank()) {
        return new ToolResult("No command provided");
    }
    try {
        Process process = new ProcessBuilder("/bin/sh", "-c", command)
                .directory(workdir)
                .redirectErrorStream(true)  // stderr merged into stdout
                .start();
        String output = readOutput(process);  // read line by line with BufferedReader
        int exitCode = process.waitFor();
        return new ToolResult("exit_code=" + exitCode + "\n" + output);
    } catch (IOException e) {
        return new ToolResult("Command failed to start: " + e.getMessage());
    } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return new ToolResult("Command interrupted");
    }
}
```

### Next

- Tool dispatch (`Map<String, Tool>`) coupled inside AgentLoop → **s02 ToolRegistry**
- Dangerous commands (`rm -rf /`) execute without a permission gate → **s03 Permission**
- No extension points in the loop — logging/stats only possible via Listener hardcoded in demo → **s04 Hooks**

## s02 Tool Dispatch

**Teaching branch:** `s02-tool-dispatch`

### Problem

s01 has only one Bash tool. Adding `read_file` or `write_file` means modifying AgentLoop — more tools, more changes. How to add tools without touching the loop?

Also, theoretically `cat`, `echo`, `sed`, `find` could all be done via bash — why add dedicated tools?

- **Token cost**: bash output can be huge (e.g. `cat` a large file); dedicated tools can limit lines and truncate, reducing context waste
- **Reliability**: `sed` text replacement is error-prone with regex escaping; `edit_file` takes exact `old_text` and `new_text`, no regex needed
- **Security**: dedicated tools enforce path constraints at the code level (canonical path check); bash command strings are harder to audit and easier to inject
- **Model-friendly**: structured `input_schema` (JSON Schema) is less error-prone than natural language command descriptions; models are better at filling in JSON parameters

Real Claude Code has **19 tools** (bash, read, write, edit, glob, grep, task, todo_write, web_search, web_fetch, skill, ask_user_question, etc.). This chapter adds only the 4 most representative file operation tools.

### Function

Introduce `ToolRegistry` as a tool registration hub. Adding a new tool only requires implementing the `Tool` interface + `registry.register()`. The loop only looks up by name, no longer caring how tools are sourced.

New:

- `ToolRegistry`: `LinkedHashMap` implementation, `register()` returns `this` for chaining, `find()` looks up by name, `definitions()` exports to LLM
- `PathGuard`: path security component — `resolve(path)` does canonical path check, prevents `../` escape
- `ReadFileTool`: reads UTF-8 text files, supports `limit` to cap lines
- `WriteFileTool`: writes file content, auto-creates parent directories
- `EditFileTool`: replaces first occurrence of exact text in a file
- `GlobTool`: finds files by glob pattern

### Design

```text
ToolRegistry
  bash       → BashTool
  read_file  → ReadFileTool
  write_file → WriteFileTool
  edit_file  → EditFileTool
  glob       → GlobTool
```

Core principle: **loop unchanged, only extract dispatch**. s01's AgentLoop used `Map<String, Tool>` for dispatch internally; s02 extracts it into an independent `ToolRegistry` class. The ReAct loop structure is fully preserved — the only change is `tools.get(name)` → `registry.find(name)`.

Adding a new tool only takes two steps, no AgentLoop changes:

1. Write a class implementing the `Tool` interface
2. In the demo: `registry.register(new XxxTool(...))`

All file tools uniformly use `PathGuard` for workdir path constraints — `PathGuard.resolve(path)` converts relative paths to canonical paths, compares prefix with the workdir canonical path, throws on boundary violation. All file tools create `new PathGuard(workdir)` in their constructor and call `pathGuard.resolve(path)` in `execute`.

### Implementation

`ToolRegistry` — `register()` returns `this`, supports chaining:

```java
public class ToolRegistry {
    private final Map<String, Tool> tools = new LinkedHashMap<>();

    public ToolRegistry register(Tool tool) {
        tools.put(tool.getDefinition().getName(), tool);
        return this;
    }

    public Tool find(String name) {
        return tools.get(name);
    }

    public List<ToolDefinition> definitions() {
        List<ToolDefinition> definitions = new ArrayList<>();
        for (Tool tool : tools.values()) {
            definitions.add(tool.getDefinition());
        }
        return definitions;
    }
}
```

`PathGuard` — path security component for all file tools (introduced in s02):

```java
public class PathGuard {
    private final File workdir;

    public File resolve(String path) throws IOException {
        File target = new File(workdir, path).getCanonicalFile();
        if (!target.toPath().startsWith(workdir.getCanonicalFile().toPath())) {
            throw new IOException("Path escapes workspace: " + path);
        }
        return target;
    }
}
```

`ReadFileTool.execute()` — delegates path validation to PathGuard, appends remaining line count when truncated:

```java
public ToolResult execute(JSONObject input) {
    String path = input.getString("path");
    if (path == null || path.isBlank()) {
        return new ToolResult("Error: No path provided");
    }
    try {
        File target = pathGuard.resolve(path);
        Integer limit = input.getInteger("limit");
        List<String> lines = Files.readAllLines(target.toPath(), StandardCharsets.UTF_8);
        if (limit != null && limit > 0 && limit < lines.size()) {
            List<String> limited = new ArrayList<>(lines.subList(0, limit));
            limited.add("... (" + (lines.size() - limit) + " more lines)");
            lines = limited;
        }
        return new ToolResult(String.join("\n", lines));
    } catch (IOException e) {
        return new ToolResult("Error: " + e.getMessage());
    }
}
```

### Next

- Tools can execute whatever they want, no permission gate → **s03 Permission**
- No extension points in the loop — logging, stats, permission checks still require modifying AgentLoop → **s04 Hooks**

## s03 Permission

**Teaching branch:** `s03-permission`

### Problem

s02's tools can do anything — `rm -rf /`, write to `/etc/`, `chmod 777`. The agent won't ask you before executing. A permission gate is needed to intercept dangerous operations before tool execution.

### Function

Insert a three-tier permission pipeline before tool execution: hard deny → rule match → user confirmation. Dangerous commands are outright rejected; sensitive operations require user approval.

New:

- `PermissionManager`: dispatch checks by tool type (bash → deny/ask, file writes → boundary check, others → allow)
- `PermissionDecision`: `allow()` or `deny(message)`, denial messages are passed back to the model
- `ApprovalPrompter` (interface) + `ConsoleApprovalPrompter` (Scanner-based y/N implementation)

### Design

PermissionManager dispatches by tool type, not blanket string matching for all tools:

```text
PermissionManager.check(toolUse)
  ├─ tool == "bash"       → checkBash(): deny list → ask patterns → allow
  ├─ tool == "write_file" → checkFileWrite(): canonical path boundary check
  │   or "edit_file"
  └─ other tools          → allow() directly (read_file, glob are non-destructive)
```

Bash tool has three gates:

```text
bash command → 1. denyList hit? (rm -rf /, sudo, shutdown, mkfs, dd if=, > /dev/sda)
                   ├─ hit → deny(), don't ask user
                   └─ no hit → 2. askPatterns hit? (rm , > /etc/, chmod 777)
                                   ├─ hit → ConsoleApprovalPrompter.approve(), wait for user y/N
                                   └─ no hit → 3. allow()
```

File write tools have one gate: canonical path comparison, boundary violation → deny directly.

Flow: PermissionManager sits between AgentLoop's tool_use detection and execute:

```text
LLM → tool_use → PermissionManager.check() → pass → execute → tool_result
                                            → deny → denial reason as tool_result
```

When denied, no exception is thrown — the denial reason is returned as `tool_result` to the model, so the model can see why it wasn't executed.

### Implementation

`PermissionManager.check()` — dispatches by tool type:

```java
public PermissionDecision check(ToolUseBlock toolUse) {
    if ("bash".equals(toolUse.getName())) {
        return checkBash(toolUse);       // deny list + ask patterns
    }
    if ("write_file".equals(toolUse.getName()) || "edit_file".equals(toolUse.getName())) {
        return checkFileWrite(toolUse);  // canonical path boundary check
    }
    return PermissionDecision.allow();   // read_file, glob, etc. pass through
}

private PermissionDecision checkBash(ToolUseBlock toolUse) {
    String command = toolUse.getInput().getString("command");
    // Tier 1: hard deny list (don't ask user)
    for (String pattern : denyList) {
        if (command != null && command.contains(pattern)) {
            return PermissionDecision.deny("Permission denied: '" + pattern + "' is on the deny list");
        }
    }
    // Tier 2: rule match → user confirmation
    for (String pattern : askPatterns) {
        if (command != null && command.contains(pattern)) {
            return ask(toolUse, "Potentially destructive command");
        }
    }
    return PermissionDecision.allow();
}
```

`ConsoleApprovalPrompter` — teaching-grade approver, only accepts y/yes:

```java
public boolean approve(ToolUseBlock toolUse, String reason) {
    System.out.println("Permission> " + reason);
    System.out.println("Tool> " + toolUse.getName() + " " + toolUse.getInput());
    System.out.print("Allow? [y/N] ");
    String choice = scanner.nextLine().trim().toLowerCase();
    return "y".equals(choice) || "yes".equals(choice);
}
```

Demo wiring:

```java
PermissionManager permissionManager = new PermissionManager(workdir, new ConsoleApprovalPrompter(scanner));
AgentLoop loop = new AgentLoop(new AnthropicLlmClient(config), registry, listener, permissionManager);
```

### Next

- Permission rules hardcoded in `PermissionManager` — adding new rules means changing core code → **s04 Hooks** can turn permission into a hook, decoupling rules from the loop
- Logging, stats, output checks — these cross-cutting concerns are also scattered in the loop or permission code → **s04 Hooks** provides unified extension points

## s04 Hooks

**Teaching branch:** `s04-hooks`

### Problem

s03's permission rules live inside `PermissionManager`. Logging, stats, output checks are scattered throughout AgentLoop. Every cross-cutting capability added means modifying the main loop. How to add capabilities without touching the loop?

### Function

Decouple cross-cutting logic from AgentLoop by firing event hooks at fixed points in the loop. Permission, logging, and output checks can all be registered as hooks. The loop only fires triggers, not caring about the logic.

New:

- `Hook`: functional interface `HookDecision run(HookContext context)`
- `HookEvent`: constants class, four event points `USER_PROMPT_SUBMIT`, `PRE_TOOL_USE`, `POST_TOOL_USE`, `STOP`
- `HookContext`: POJO with optional fields per event type (userPrompt / toolUse / toolResult / messages)
- `HookDecision`: `pass()` to allow or `block(message)` to block, `isBlocked()` to check
- `HookManager`: `Map<String, List<Hook>>` storage, `register(event, hook)` returns `this`, `trigger(event, context)` executes in order

### Design

Four event points covering the full lifecycle of a conversation:

```text
User input → UserPromptSubmit (triggered externally by demo, not inside AgentLoop)
Before tool execution → PreToolUse (permission checks, logging — can block execution)
After tool execution → PostToolUse (output logging, read-only)
Loop stopped → Stop (count tool invocations)
```

Key design decisions:

- **PreToolUse can block**: returning `HookDecision.block(message)` passes the message as tool_result back to the model. Subsequent hooks for the same event are skipped (short-circuit)
- **PostToolUse is read-only**: tool already executed, hook can only observe, not undo
- **UserPromptSubmit triggered outside AgentLoop**: the demo manually fires it before calling `loop.run()`, since the demo handles user input, not the AgentLoop
- **Permission migrates from s03's standalone class to s04's hook**: in the s04 demo, permission logic lives inline as a `PreToolUse` hook in the `permissionHook()` method — the `PermissionManager` class is no longer used. Demonstrates "don't change the loop, just add/remove hooks"
- **Hooks inlined in demo**: teaching version puts hooks in the entry class so readers can see at a glance how extension points are wired

### Implementation

`HookManager` — stored by String key, `trigger()` returns first block or final pass:

```java
public class HookManager {
    private final Map<String, List<Hook>> hooks = new LinkedHashMap<>();

    public HookManager register(String event, Hook hook) {
        hooks.computeIfAbsent(event, key -> new ArrayList<>()).add(hook);
        return this;
    }

    public HookDecision trigger(String event, HookContext context) {
        List<Hook> eventHooks = hooks.get(event);
        if (eventHooks == null) {
            return HookDecision.pass();
        }
        for (Hook hook : eventHooks) {
            HookDecision decision = hook.run(context);
            if (decision != null && decision.isBlocked()) {
                return decision;
            }
        }
        return HookDecision.pass();
    }
}
```

In s04, permission becomes a hook (inlined in `permissionHook()` method, no more PermissionManager class):

```java
private static HookDecision permissionHook(HookContext context, Scanner scanner) {
    ToolUseBlock toolUse = context.getToolUse();
    if (!"bash".equals(toolUse.getName())) {
        return HookDecision.pass();  // non-bash tools pass through
    }
    String command = toolUse.getInput().getString("command");
    // Hard deny list
    for (String pattern : Arrays.asList("rm -rf /", "sudo", "shutdown", "reboot", "mkfs", "dd if=")) {
        if (command.contains(pattern)) {
            return HookDecision.block("Permission denied by hook: " + pattern);
        }
    }
    // Rule match → user confirmation
    for (String pattern : Arrays.asList("rm ", "> /etc/", "chmod 777")) {
        if (command.contains(pattern)) {
            System.out.print("Allow? [y/N] ");
            String choice = scanner.nextLine().trim().toLowerCase();
            if (!"y".equals(choice) && !"yes".equals(choice)) {
                return HookDecision.block("Permission denied by hook");
            }
        }
    }
    return HookDecision.pass();
}
```

Demo registers four hook types:

```java
HookManager hooks = new HookManager();
hooks.register(HookEvent.USER_PROMPT_SUBMIT, ctx -> {
    System.out.println("[HOOK] UserPromptSubmit: working in " + workdir.getAbsolutePath());
    return HookDecision.pass();
});
hooks.register(HookEvent.PRE_TOOL_USE, ctx -> permissionHook(ctx, scanner));     // permission
hooks.register(HookEvent.PRE_TOOL_USE, ctx -> {                                   // logging
    System.out.println("[HOOK] PreToolUse: " + ctx.getToolUse().getName());
    return HookDecision.pass();
});
hooks.register(HookEvent.POST_TOOL_USE, ctx -> {                                  // output
    System.out.println("[HOOK] PostToolUse: " + ctx.getToolUse().getName());
    return HookDecision.pass();
});
hooks.register(HookEvent.STOP, ctx -> {                                           // stats
    System.out.println("[HOOK] Stop: session used " + toolResultCount(ctx.getMessages()) + " tool calls");
    return HookDecision.pass();
});

AgentLoop loop = new AgentLoop(llmClient, registry, listener, hookManager);  // no PermissionManager passed
```

### Next

- Agent can freely call tools but lacks task planning — complex tasks are done ad-hoc, forgetting next steps → **s05 Todo**

## s05 Todo

**Teaching branch:** `s05-todo`

### Problem

The Agent can freely call tools, but when facing multi-step tasks it has no planning capability — it wings it and forgets what comes next halfway through. How to make the Agent plan first, then execute?

### Function

Turn planning into a tool `todo_write`, letting the model explicitly write down the current task list with statuses. The loop doesn't change — planning capability comes from the tool itself, continuing s02's "add one tool, add one handler" principle.

New:

- `TodoItem`: data class holding `content` and `status`
- `TodoWriteTool`: tool name `todo_write`, in-memory storage, accepts full list replacing current state

### Design

```text
User: refactor hello.py with type annotations and docstrings
  → Agent first calls todo_write, writes the plan:
      [pending] Check if file exists
      [pending] Add type annotations
      [pending] Add docstrings
      [pending] Verify syntax
  → Execute items one by one, updating status to completed as done
```

Key design decisions:

- **Planning IS a tool call**: no hardcoded planning logic in AgentLoop. `todo_write` is just like any other tool — register and use
- **Full replacement**: `todo_write` receives the complete task list, replaces in-memory state (not append/merge), avoiding stale task residue
- **Three statuses**: `pending` → `in_progress` → `completed`, the model decides transitions
- **Only tracks, doesn't execute**: `todo_write` only records the plan; actual execution relies on bash, write_file, and other tools
- System prompt requires "plan first for multi-step tasks, update status during execution"

### Implementation

`TodoWriteTool.execute()` — full replacement with validation:

```java
public ToolResult execute(JSONObject input) {
    JSONArray todos = todosArray(input);
    if (todos == null) {
        return new ToolResult("Error: todos must be an array");
    }
    List<TodoItem> nextTodos = new ArrayList<>();
    for (int i = 0; i < todos.size(); i++) {
        JSONObject item = todos.getJSONObject(i);
        String content = item.getString("content");
        String status = item.getString("status");
        if (content == null || content.isBlank()) {
            return new ToolResult("Error: todos[" + i + "] missing content");
        }
        if (!Arrays.asList("pending", "in_progress", "completed").contains(status)) {
            return new ToolResult("Error: todos[" + i + "] has invalid status: " + status);
        }
        nextTodos.add(new TodoItem(content, status));
    }
    currentTodos.clear();
    currentTodos.addAll(nextTodos);  // full replacement, not append
    return new ToolResult("Updated " + currentTodos.size() + " tasks\n" + render());
}
```

Input format:

```json
{
  "todos": [
    {"content": "Check if file exists", "status": "in_progress"},
    {"content": "Add type annotations", "status": "pending"},
    {"content": "Verify syntax", "status": "pending"}
  ]
}
```

### Next

- Todo only exists in memory, gone when the session ends — no cross-session recovery → **s10 Task System** for persistent task graph
- Complex subtasks crammed into the same context get messier → **s06 Subagent** for clean-context independent execution

## s06 Subagent

**Teaching branch:** `s06-subagent`

### Problem

When the parent Agent's context is already crowded, cramming a complex subtask into the same `messages` makes things messier — tool calls and results interleave, the model's attention is scattered by irrelevant history. How to let a subtask execute in a clean context independently, only bringing back a result summary?

### Function

Turn "delegate subtask" into a `task` tool. The sub-agent runs with fresh `messages` and an independent `AgentLoop`; the parent Agent only receives the final text summary, never seeing intermediate steps.

New:

- `TaskTool`: tool name `task`, input `description`, internally launches sub-agent, returns summary
- `AgentLoop` adds `maxTurns` constructor parameter (default 20, sub-agent set to 30)

### Design

```text
Parent messages[] → task(description)
                  → Subagent messages[] = [description] (fresh context)
                  → Subagent tools: bash/read_file/write_file/edit_file/glob
                  → Independent loop, no shared history
                  → summary text → Parent tool_result
```

Key boundary design:

- **No recursion**: sub-agent does not register the `task` tool, preventing infinite delegation
- **Simplified toolset**: sub-agent only has basic file operations + bash, no todo, permission, hooks or other parent-level tools
- **Context isolation**: sub-agent's intermediate tool calls and message history are never brought back to the parent; the parent only receives the final text summary
- **More turns**: sub-agent `maxTurns=30` (parent default 20), giving subtasks more execution headroom

### Implementation

`TaskTool` — sub-agent tools and LLM client injected via constructor (not created inside execute):

```java
public class TaskTool implements Tool {
    private final LlmClient subagentClient;
    private final ToolRegistry subagentTools;

    public TaskTool(LlmClient subagentClient, ToolRegistry subagentTools) {
        this.subagentClient = subagentClient;
        this.subagentTools = subagentTools;
    }

    public ToolResult execute(JSONObject input) {
        String description = input.getString("description");
        if (description == null || description.isBlank()) {
            return new ToolResult("Error: No description provided");
        }
        System.out.println("[Subagent spawned]");
        // Sub-agent does NOT reuse parent history — this is the core of "clean context"
        AgentLoop subLoop = new AgentLoop(subagentClient, subagentTools, listener, 30);
        AssistantMessage answer = subLoop.run(description);
        System.out.println("[Subagent done]");
        // Parent only gets final text summary, not sub-agent's full intermediate messages
        return new ToolResult(extractText(answer));
    }
}
```

Demo wiring — sub tool pool excludes `task`, TaskTool injected with sub-agent's client and tools:

```java
// Sub tool pool: basic file ops + bash, excluding task (prevents recursive delegation)
ToolRegistry subTools = baseTools(workdir);
// Parent tool pool: base tools + task (injected with sub-agent's client and tools)
ToolRegistry parentTools = baseTools(workdir)
        .register(new TaskTool(new AnthropicLlmClient(subConfig), subTools));
```

### Next

- Sub-agents carry no skill instructions — specialized tasks lack guidance on how to proceed → **s07 Skill Loading**
- Context still grows continuously, no proactive compaction mechanism → **s08 Context Compact**

## s07 Skill Loading

**Teaching branch:** `s07-skill-loading`

### Problem

An Agent may have many skill instructions (code review, test specs, deployment flows), but stuffing every `SKILL.md` into the system prompt wastes context — each skill body could be thousands of words. How to let the Agent know what skills are available but only load the body when needed?

### Function

At startup, inject only the skill directory (name + description); load the body on demand via the `load_skill` tool when actually needed. Two-tier loading: cheap tier stays in prompt; expensive tier expands on demand.

New:

- `Skill`: data class holding `name`, `description`, `body`
- `SkillRegistry`: scans `skills/*/SKILL.md`, parses YAML frontmatter, generates catalog from name/description
- `LoadSkillTool`: tool name `load_skill`, returns `<skill name="...">body</skill>` by skill name

### Design

Two-tier loading — same "index in prompt, body fetched on demand" approach as s09 Memory:

```text
system prompt: "Available skills: code-review (code review), java-cli (Java CLI conventions)"
                                        ↓
Model needs code-review → load_skill("code-review")
                                        ↓
Returns <skill name="code-review">Check naming, exception handling, test coverage...</skill>
                                        ↓
Injected into current conversation, model executes per skill conventions
```

Key design decisions:

- **Catalog only, no bodies**: `SkillRegistry.getDescriptions()` outputs only "name: description" list into system prompt, barely consuming tokens
- **Lookup by name, path traversal prevented**: `load_skill` only accepts already-scanned skill names, not file paths
- **XML tag wrapping**: loaded skill body is wrapped in `<skill name="...">` so the model clearly knows it's a skill instruction

### Implementation

`SkillRegistry` — scans `skills/*/SKILL.md`, parses YAML frontmatter:

```java
public class SkillRegistry {
    private final Map<String, Skill> skills = new LinkedHashMap<>();

    public SkillRegistry(File skillsDir) {
        File[] dirs = skillsDir.listFiles(File::isDirectory);
        for (File dir : dirs) {
            File skillFile = new File(dir, "SKILL.md");
            if (!skillFile.isFile()) continue;
            String raw = Files.readString(skillFile.toPath(), StandardCharsets.UTF_8);
            Skill skill = parse(dir.getName(), raw);  // fallbackName = directory name
            skills.put(skill.getName(), skill);
        }
    }

    private Skill parse(String fallbackName, String raw) {
        String name = fallbackName;
        String body = raw;
        if (raw.startsWith("---")) {
            String[] parts = raw.split("---", 3);
            // Parse name: / description: from frontmatter
            for (String line : parts[1].split("\\R")) {
                if (line.startsWith("name:")) name = line.substring(5).trim();
                else if (line.startsWith("description:")) description = line.substring(12).trim();
            }
            body = parts[2].trim();  // content after frontmatter is body
        }
        return new Skill(name, description, body);
    }

    public String getDescriptions() {
        // Catalog only has skill name and one-line description
        StringBuilder sb = new StringBuilder();
        for (Skill skill : skills.values()) {
            sb.append("  - ").append(skill.getName()).append(": ").append(skill.getDescription()).append("\n");
        }
        return sb.toString();
    }
}
```

`LoadSkillTool.execute()` — lookup by name, returned with `<skill>` tag wrapping:

```java
public ToolResult execute(JSONObject input) {
    String name = input.getString("name");
    Skill skill = skillRegistry.find(name);
    if (skill == null) {
        return new ToolResult("Skill not found: " + name);
    }
    return new ToolResult("<skill name=\"" + skill.getName() + "\">\n"
        + skill.getBody() + "\n</skill>");
}
```

Example skill file `skills/code-review/SKILL.md`:

```markdown
---
name: code-review
description: Code review skill
---
When reviewing code, check the following:
1. Naming conventions
2. Exception handling
3. Test coverage
4. Potential performance issues
```

### Next

- Conversation history keeps growing — even with on-demand skill loading, context still expands → **s08 Context Compact**
- Skill files are static — no persistent memory of user preferences and project facts → **s09 Memory**

## s08 Context Compact

**Teaching branch:** `s08-context-compact`

### Problem

Tool outputs and multi-turn conversations keep filling the context window. Models have context limits (e.g. 200K tokens); exceeding them causes `prompt_is_too_long` errors or silent truncation. How to proactively free space before LLM calls?

### Function

Insert a four-layer compaction pipeline before AgentLoop calls the LLM, executing from lowest to highest cost and fidelity. Also provides a `compact` tool for the model to explicitly trigger summarization.

New:

- `CompactingAgentLoop`: s08-specific loop, runs compaction pipeline before LLM calls, intercepts `compact` tool for special handling
- `CompactionPipeline`: executes four compaction layers in fixed order
- `MessageInspector`: determines if messages contain tool_use/tool_result, estimates message size
- `ToolResultStore`: writes large tool results to disk at `.task_outputs/tool-results/`
- `TranscriptStore`: saves full history to `.transcripts/transcript_*.jsonl` before compaction
- `CompactTool`: exposes `compact` tool definition; actual compaction is handled by the loop

### Design

Four compaction layers, executed lowest-to-highest cost:

```text
1. toolResultBudget (last tool_result > 200KB)
   → Save original to disk, keep only file path + truncated preview in context
2. snipCompact (message count > 50)
   → Cut middle history, keep first 3 and last 47 messages
   → Protect assistant(tool_use) and user(tool_result) pairs from being split
3. microCompact (too many old tool_results)
   → Keep last 3 complete results, replace older ones with "[Earlier tool result compacted. Re-run if needed.]"
4. compactHistory (still > 50KB)
   → Last resort: call LLM to generate summary, save full transcript to .transcripts/ beforehand
```

Key design decisions:

- **Order is fixed**: cheap, high-fidelity layers run first; expensive LLM summarization is the last resort
- **Pair protection**: snipCompact must not split `assistant(tool_use)` and the immediately following `user(tool_result)`, otherwise the Anthropic API rejects the request due to unpaired tool calls
- **compact tool special handling**: when the model calls `compact`, the loop itself performs compaction (the tool doesn't actually execute) and manually patches the `tool_use` and `tool_result` pair back into messages, preventing the next API round from receiving an incomplete tool call
- **Reactive compaction**: in addition to the four-layer proactive pipeline, when LLM returns `prompt_is_too_long`, the loop automatically executes `compactHistory` summarization and retries — the error is never exposed to the user

### Implementation

`CompactionPipeline` — four private methods + two public entry points:

```java
public class CompactionPipeline {
    private static final int MAX_MESSAGES = 50;
    private static final int KEEP_RECENT_TOOL_RESULTS = 3;
    private static final int TOOL_RESULT_BUDGET = 200_000;   // 200KB
    private static final int AUTO_COMPACT_THRESHOLD = 50_000; // 50KB

    // Automatically executed before each LLM call (best-effort, tries to free space)
    public void beforeLlm(List<Message> messages) {
        toolResultBudget(messages);   // Layer 1
        snipCompact(messages);        // Layer 2
        microCompact(messages);       // Layer 3
        if (inspector.estimateSize(messages) > AUTO_COMPACT_THRESHOLD) {
            replaceAll(messages, compactHistory(messages, "auto compact"));  // Layer 4
        }
    }

    // Triggered when model calls compact tool or prompt_is_too_long
    public List<Message> compactHistory(List<Message> messages, String focus) {
        transcriptStore.write(messages);  // Save full transcript first
        AssistantMessage summary = summaryClient.chat(
            List.of(Message.user("Summarize: ..." + focus)), List.of());
        return List.of(Message.user("[Compacted]\n\n" + extractText(summary)));
    }

    // Reactive compaction after prompt_is_too_long: keep last 10 + summary
    public List<Message> reactiveCompact(List<Message> messages) {
        List<Message> compacted = new ArrayList<>(compactHistory(messages, "reactive"));
        int start = Math.max(0, messages.size() - 10);
        for (int i = start; i < messages.size(); i++) compacted.add(messages.get(i));
        return compacted;
    }
}
```

`CompactingAgentLoop` — runs pipeline before LLM calls + catches `prompt_is_too_long`:

```java
// Before each LLM call
pipeline.beforeLlm(messages);

try {
    response = llmClient.chat(messages, toolDefinitions());
} catch (PromptTooLongException e) {
    // Reactive compaction: summary + keep recent history
    messages = pipeline.reactiveCompact(messages);
    response = llmClient.chat(messages, toolDefinitions());
}
```

### Next

- Compaction loses implicit information like user preferences and feedback → **s09 Memory** extracts memories before compaction
- Compaction pipeline parameters (thresholds, retention counts) are hardcoded → production systems need configurability

## s09 Memory

**Teaching branch:** `s09-memory`

### Problem

Context compaction throws away user preferences ("I prefer tab indentation"), project facts ("this project uses Java 17"), and feedback ("that approach didn't work last time"). This information shouldn't only live in conversation history — history gets compacted and cleared. How to persist what should be remembered and forget what should be forgotten?

### Function

Store memories as Markdown files under `.memory/`, with `MEMORY.md` as an index always in the prompt, and bodies injected on demand. Three subsystems each have their role: selection (pick relevant memories), extraction (extract new memories from conversation), and consolidation (dedup and merge).

New:

- `Memory`: data class, filename, name, description, type, body
- `MemoryStore`: manages `.memory/*.md` files and `.memory/MEMORY.md` index
- `MemorySelector`: selects up to 5 relevant memories for the current request, keyword fallback on LLM failure
- `MemoryExtractor`: extracts new memories from pre-compaction snapshot after each turn
- `MemoryConsolidator`: merges and deduplicates when memory file count reaches 10
- `MemoryManager`: combines the three subsystems (select, extract, consolidate)
- `MemoryAgentLoop`: wraps s08 compaction loop, injects memory logic before and after each turn

### Design

Same approach as s07 skill loading — index in prompt, body fetched on demand:

```text
Start of each turn:
  1. Inject MEMORY.md index into system prompt (cheap)
  2. MemorySelector picks up to 5 relevant memories for current request
  3. Relevant memory bodies wrapped in <relevant_memories> and injected

End of each turn:
  4. Snapshot messages before compaction
  5. MemoryExtractor extracts new memories from snapshot (using pre-compaction snapshot to preserve nuance)
  6. Write to .memory/{name}.md, update MEMORY.md index

Consolidation:
  7. When memory file count reaches 10, trigger MemoryConsolidator to merge and dedup
```

Memory file format (`.memory/user-preference-tabs.md`):

```markdown
---
name: user-preference-tabs
description: User prefers tabs for indentation
type: user
---

User prefers using tabs, not spaces, for indentation.
```

`MEMORY.md` index format:

```markdown
- [user-preference-tabs](user-preference-tabs.md) — User prefers tabs for indentation
```

Key design decisions:

- **Extraction uses pre-compaction snapshot**: compaction erases detail — user preferences and feedback nuance can be swallowed by summarization, so extraction must happen before compaction
- **Keyword fallback**: MemorySelector prioritizes LLM-based selection, falls back to keyword matching if LLM call fails
- **Dedup, not overwrite**: before writing new memories, compare name + description with existing ones; skip duplicates rather than overwriting
- **Simple consolidation threshold**: teaching version uses "file count reaches 10" to trigger consolidation; no time-gating or background Dream phase

### Implementation

`MemoryAgentLoop.run()` — minimal wrapper, snapshot passed in externally, extraction delegated to MemoryManager:

```java
public class MemoryAgentLoop {
    private final CompactingAgentLoop compactingLoop;
    private final MemoryManager memoryManager;

    public AssistantMessage run(List<Message> messages, List<Message> preCompactSnapshot) {
        AssistantMessage answer = compactingLoop.run(messages);
        // Memory extraction must use pre-compaction context to avoid summaries erasing detail
        memoryManager.afterTurn(preCompactSnapshot);
        return answer;
    }

    // snapshot() method called in demo, deep-copies all ContentBlock types
    public List<Message> snapshot(List<Message> messages) { ... }
}
```

Demo call sequence:

```java
// Start of turn: inject relevant memories into system prompt
List<Memory> relevant = memoryManager.selectRelevant(query);
// ... rebuild system prompt, wrap in <relevant_memories>

// Snapshot before compaction
List<Message> snapshot = memoryLoop.snapshot(history);

// Run MemoryAgentLoop (internally calls s08 compaction loop)
AssistantMessage answer = memoryLoop.run(history, snapshot);
// afterTurn auto-called inside run(), extracts memories from snapshot
```

### Next

- Memories are only extracted at end of session — no background Dream phase for deep reflection and consolidation → production needs background memory processing
- Todo is still in-memory — task lists can't survive across sessions → **s10 Task System** for persistent task graph

## s10 Task System

**Teaching branch:** `s10-task-system`

### Problem

s05's TodoWrite is in-memory only — gone when the session ends. Larger goals need cross-session persistence, task dependency ordering (A must complete before B), and a shared task graph for future multi-agent collaboration. How to upgrade Todo from "in-session checklist" to "persistent task graph"?

### Function

Introduce a persistent task system: each task stored as `.tasks/task_{id}.json`, supporting state machine (pending → in_progress → completed), dependency checks (blockedBy), and owner claiming.

New:

- `TaskRecord`: data class, id, subject, description, status, owner, blockedBy
- `TaskStore`: manages `.tasks/` directory, task JSON read/write and list scanning
- `TaskService`: state machine + blockedBy dependency checks
- `CreateTaskTool`: creates persistent tasks, optionally with upstream dependencies
- `ListTasksTool`: lists all task statuses, owners, and dependencies
- `GetTaskTool`: reads a single task's full JSON
- `ClaimTaskTool`: claims a ready task, sets owner and transitions to in_progress
- `CompleteTaskTool`: completes a task, reports newly unblocked downstream tasks

### Design

Positioning vs. s05 TodoWrite:

| | TodoWrite (s05) | Task System (s10) |
|---|---|---|
| Purpose | Current task execution checklist | Cross-session recoverable task graph |
| Storage | Session memory | `.tasks/{id}.json` persistent |
| Dependencies | None | `blockedBy` array |
| Lifecycle | Current session | Cross-session |
| Ownership | No task assignment | `owner` + claim |

State machine has only three states:

```text
pending → in_progress → completed
```

`claim_task` is the only entry point from pending to in_progress. Before claiming, all blockedBy are checked:
- Dependency task doesn't exist → blocked
- Any dependency not completed → blocked
- All dependencies completed → claim allowed

`complete_task` only allows completing in_progress tasks. After completion, automatically scans all pending tasks and reports which ones had their dependencies just satisfied (Unblocked).

Task persistence format (`.tasks/task_1781770000000_0427.json`):

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

### Implementation

`TaskService` — state machine + dependency checks (returns message strings, doesn't throw):

```java
public class TaskService {
    private final TaskStore store;

    public String claimTask(String taskId, String owner) {
        TaskRecord task = store.load(taskId);
        if (!"pending".equals(task.getStatus())) {
            return "Task " + taskId + " is " + task.getStatus() + ", cannot claim";
        }
        List<String> blocked = blockingDependencies(task);
        if (!blocked.isEmpty()) {
            return "Blocked by: " + blocked;
        }
        task.setOwner(owner == null || owner.isBlank() ? "agent" : owner);
        task.setStatus("in_progress");
        store.save(task);
        return "Claimed " + task.getId() + " (" + task.getSubject() + ")";
    }

    public String completeTask(String taskId) {
        TaskRecord task = store.load(taskId);
        if (!"in_progress".equals(task.getStatus())) {
            return "Task " + taskId + " is " + task.getStatus() + ", cannot complete";
        }
        task.setStatus("completed");
        store.save(task);
        List<String> unblocked = findUnblockedSubjects();
        String msg = "Completed " + task.getId();
        if (!unblocked.isEmpty()) {
            msg += "\nUnblocked: " + String.join(", ", unblocked);
        }
        return msg;
    }

    private List<String> blockingDependencies(TaskRecord task) {
        List<String> blocked = new ArrayList<>();
        for (String depId : task.getBlockedBy()) {
            if (!store.exists(depId) || !"completed".equals(store.load(depId).getStatus()))
                blocked.add(depId);
        }
        return blocked;
    }
}
```

### Next

- Slow operations (npm install, docker build) block the Agent synchronously → **s11 Background Tasks**
- Claim has no concurrency protection — multiple agents could claim the same task simultaneously → teaching version uses owner check as guard; production needs file locks

## s11 Background Tasks

**Teaching branch:** `s11-background-tasks`

### Problem

`npm install` takes 3 minutes, `docker build` even longer. The Agent waits synchronously for results, doing nothing in the meantime. How to let slow operations run in the background while the Agent continues working?

### Function

Introduce a background execution path for the bash tool. Slow operations are dispatched to daemon threads, immediately returning a placeholder result for the Agent to continue thinking. When background work completes, results are injected as `<task_notification>` XML in the next turn.

New:

- `BackgroundTask`: data class, bgId, toolUseId, command, status
- `BackgroundDecider`: two-tier judgment logic (explicit parameter + keyword fallback)
- `BackgroundTasks`: daemon thread manager, ConcurrentHashMap + AtomicInteger for thread safety
- `BackgroundAgentLoop`: s11-specific loop with built-in sync/background dual execution paths and notification injection

### Design

Two execution paths, determined by BackgroundDecider:

```text
Tool call → BackgroundDecider.shouldRunBackground()
                ├─ false → synchronous execution → tool_result returned immediately
                └─ true  → daemon thread → placeholder tool_result → next turn <task_notification>
```

Timeline example — Agent doesn't wait for npm install:

```text
Turn 1: LLM → bash "npm install" (run_in_background=true)
        → [background] dispatched bg_0001
        → tool_result: "[Background task bg_0001 started]"
        → LLM: "Put npm install in background, I'll read package.json first"

Turn 2: LLM → read_file "package.json" (synchronous, milliseconds)
        → [background done] bg_0001 completed
        → Inject <task_notification>
        → LLM sees both package.json content + npm install completion notification
```

Two-tier judgment:

1. Model explicitly sets `run_in_background=true` → directly background (primary path)
2. Command contains keywords like `install/build/test/deploy/compile/docker/pip/npm/cargo/pytest/make` → fallback

Only applies to bash; read_file/write_file and other tools always execute synchronously.

Notification format doesn't reuse the original `tool_use_id` — the original call was already answered with a placeholder tool_result. Background completion is an independent event:

```xml
<task_notification>
  <task_id>bg_0001</task_id>
  <status>completed</status>
  <command>npm install</command>
  <summary>added 245 packages in 45s</summary>
</task_notification>
```

### Implementation

`BackgroundAgentLoop.executeToolUses()` — core dispatch logic:

```java
private List<ToolResultBlock> executeToolUses(AssistantMessage response) {
    List<ToolResultBlock> results = new ArrayList<>();
    for (ContentBlock block : response.getContent()) {
        if (block instanceof ToolUseBlock) {
            ToolUseBlock toolUse = (ToolUseBlock) block;
            ToolResult result;
            if (BackgroundDecider.shouldRunBackground(toolUse.getName(), toolUse.getInput())) {
                // Background path: daemon thread + placeholder tool_result
                String bgId = backgroundTasks.start(toolUse, toolRegistry);
                result = new ToolResult("[Background task " + bgId + " started] ...");
            } else {
                // Synchronous path
                result = executeTool(toolUse);
            }
            results.add(new ToolResultBlock(toolUse.getId(), result.getContent()));
        }
    }
    return results;
}

// Before each LLM call, collect completed notifications and inject as one user message
private void injectBackgroundNotifications(List<Message> messages) {
    List<String> notifications = backgroundTasks.collectNotifications();
    if (!notifications.isEmpty()) {
        StringBuilder sb = new StringBuilder();
        for (String notif : notifications) sb.append(notif).append("\n");
        messages.add(Message.user(sb.toString().trim()));
    }
}
```

### Next

- Agent only works when the user provides input, no self-triggering on a schedule → **s12 Cron Scheduler**
- Background tasks have no timeout control, risk of thread leaks → teaching version relies on daemon threads to auto-terminate on process exit

## s12 Cron Scheduler

**Teaching branch:** `s12-cron-scheduler`

### Problem

The Agent only works when the user provides input. If you want the Agent to automatically check build status at 9am every day, or trigger a task at a specific time — currently impossible. How to give the Agent scheduled self-triggering capability?

### Function

Introduce a cron expression-driven scheduled trigger. The Agent can register, list, and cancel scheduled tasks. At the appointed time, a `[Scheduled]` message is automatically injected to run the Agent. Supports persistence for recovery after restart.

New:

- `CronJob`: data class, id, cron expression, prompt, recurring, durable
- `CronStore`: persists durable tasks to `.scheduled_tasks.json`
- `CronScheduler`: delegates to Hutool CronUtil, two-tier validation (5-field check + CronPattern.of parsing), fires callback `Consumer<CronJob>` on trigger
- `ScheduleCronTool`: registers cron jobs
- `ListCronsTool`: lists active jobs
- `CancelCronTool`: cancels jobs

### Design

Uses Hutool CronUtil to avoid introducing new dependencies or writing a custom cron parser:

```text
schedule_cron("0 9 * * *", "check build status")
  → CronScheduler.schedule()
  → Hutool CronUtil registers scheduled task
  → Fire callback at appointed time
  → Inside agentLock, inject [Scheduled] check build status
  → BackgroundAgentLoop.run()
```

Key design decisions:

- **agentLock serialization**: cron triggers and user input can't run the Agent simultaneously — they share the same `history`. Protected by `synchronized`; if the Agent is busy when cron fires, the trigger queues and waits
- **Two-tier validation**: first check basic format with 5-field regex, then parse semantics with Hutool `CronPattern.of()` — format errors are caught at registration time
- **Persistence**: `durable=true` jobs are written to `.scheduled_tasks.json`, re-registered with Hutool on startup to restore scheduled triggers
- **Two trigger sources, one loop**: cron triggers construct `[Scheduled] <prompt>` as a user message for injection, going through the same AgentLoop as manual input

### Implementation

`CronScheduler` — constructor takes `Consumer<CronJob>` callback, `schedule()` returns message string:

```java
public class CronScheduler {
    private final CronStore store;
    private final Consumer<CronJob> onFire;
    private final Map<String, CronJob> jobs = new ConcurrentHashMap<>();

    public CronScheduler(CronStore store, Consumer<CronJob> onFire) {
        this.store = store;
        this.onFire = onFire;
    }

    public void start() {
        // Load durable jobs from disk and re-register with Hutool
        for (CronJob job : store.load()) {
            jobs.put(job.getId(), job);
            CronUtil.schedule(job.getId(), job.getCron(), new CronTask(job.getId()));
        }
        CronUtil.setMatchSecond(false);
        CronUtil.start();
    }

    public String schedule(String cron, String prompt, boolean recurring, boolean durable) {
        String err = validateCron(cron);
        if (err != null) return "Error: " + err;
        String id = "cron_" + Integer.toHexString(ThreadLocalRandom.current().nextInt(0x1000000));
        CronJob job = new CronJob(id, cron, prompt, recurring, durable);
        jobs.put(id, job);
        CronUtil.schedule(id, cron, new CronTask(id));
        if (durable) store.save(new ArrayList<>(jobs.values()));
        return "Scheduled " + id + ": " + prompt;
    }

    // Internal CronTask: on fire, look up from jobs map and callback; one-shot jobs auto-cancel
    private class CronTask implements Task {
        public void execute() {
            CronJob job = jobs.get(jobId);
            if (job != null) { onFire.accept(job); }
            if (!job.isRecurring()) cancel(jobId);
        }
    }
}
```

Demo constructs CronScheduler with agentLock-protected callback:

```java
CronScheduler scheduler = new CronScheduler(new CronStore(workdir), job -> {
    synchronized (agentLock) {
        agentLoop.run("[Scheduled] " + job.getPrompt());
    }
});
scheduler.start();
```

### Next

- A single Agent handles everything — complex tasks exceed a single attention span → **s13 Agent Teams**
- Cron triggers and user input share history, user input takes priority → reasonable teaching simplification; production needs independent session management

## s13 Agent Teams

**Teaching branch:** `s13-agent-teams`

### Problem

When a single Agent tackles complex tasks, context and attention become insufficient. For example, writing a backend schema and a frontend component simultaneously — the two directions muddle each other when mixed in one messages list. How to let multiple Agents work in parallel, each in their own independent context?

### Function

Let the Lead Agent launch teammate daemon threads via the `spawn_teammate` tool. Each teammate runs their own AgentLoop with independent messages, communicating with the Lead through file-based mailboxes (`.mailboxes/*.jsonl`).

New:

- `TeamMessage`: data class, from, to, type, content, timestamp
- `MessageBus`: file mailbox, appends to `.mailboxes/{to}.jsonl`, reads-then-deletes (consume pattern)
- `SpawnTeammateTool`: launches teammate daemon thread, activeTeammates prevents duplicate names
- `SendMessageTool`: writes a message to a specified Agent's mailbox
- `CheckInboxTool`: consumes the current Agent's inbox

### Design

```text
Lead: spawn_teammate("alice", "backend developer", "create schema.sql")
  → SpawnTeammateTool starts daemon thread, maxTurns=10
  → alice independent AgentLoop + independent messages
  → alice toolset: bash/read_file/write_file/send_message (no spawn_teammate)
  → alice send_message("lead", "schema.sql is done")
  → MessageBus appends to .mailboxes/lead.jsonl
  → Lead auto-reads inbox after each turn, injects <inbox>...</inbox> into history
```

Key design decisions:

- **File mailbox**: each Agent has one `.mailboxes/{name}.jsonl`, read-then-delete entire file (consume pattern), ensuring messages are never processed twice
- **Teammates can't recursively spawn**: teammate toolsets exclude `spawn_teammate`, preventing infinite fan-out
- **Harness auto-injects inbox**: teammates don't register `check_inbox`; before each LLM call, the harness auto-reads the inbox and injects `<inbox>` tags — teammates don't need to actively check mail
- **Dedup**: `activeTeammates` (ConcurrentHashMap keySet) prevents duplicate teammate names from starting
- **No teammate state persistence**: teaching version teammate runtime state is process-only; lost on exit

### Implementation

`SpawnTeammateTool` — input field is `prompt` (not `task`), teammate runs full independent loop:

```java
public ToolResult execute(JSONObject input) {
    String name = input.getString("name");
    String role = input.getString("role");
    String prompt = input.getString("prompt");  // input field is prompt
    if (!activeTeammates.add(name)) {
        return new ToolResult("Teammate '" + name + "' already exists");
    }
    Thread thread = new Thread(() -> runTeammate(name, role, prompt),
        "mini-claude-code-teammate-" + name);
    thread.setDaemon(true);
    thread.start();
    return new ToolResult("Teammate spawned: " + name);
}

private void runTeammate(String name, String role, String prompt) {
    // Teammate creates its own ToolRegistry + LlmClient (no spawn_teammate)
    ToolRegistry registry = new ToolRegistry()
        .register(new BashTool(workdir))
        .register(new ReadFileTool(workdir))
        .register(new WriteFileTool(workdir))
        .register(new SendMessageTool(bus, name));
    LlmClient client = new AnthropicLlmClient(config(promptTemplate, name, role));
    // Teammate loop: injectTeammateInbox before each turn, model doesn't register check_inbox
    AssistantMessage answer = runTeammateLoop(name, prompt, client, registry);
    bus.send(name, "lead", extractText(answer), "result");
    activeTeammates.remove(name);
}
```

`MessageBus` — `send()` uses four string parameters, `readInbox()` consumes-then-deletes:

```java
public void send(String from, String to, String content, String type) {
    TeamMessage msg = new TeamMessage(from, to, type, content);
    File inbox = new File(mailboxesDir, to + ".jsonl");
    Files.writeString(inbox.toPath(), JSON.toJSONString(msg) + "\n",
        StandardOpenOption.CREATE, StandardOpenOption.APPEND);
}

public List<TeamMessage> readInbox(String agentName) {
    File inbox = new File(mailboxesDir, agentName + ".jsonl");
    if (!inbox.exists()) return Collections.emptyList();
    List<String> lines = Files.readAllLines(inbox.toPath());
    inbox.delete();  // consume then delete
    return lines.stream().map(l -> JSON.parseObject(l, TeamMessage.class)).collect(toList());
}
```

### Next

- Teammates only exchange plain text — Lead can't distinguish "which reply corresponds to which request" → **s14 Team Protocols**
- Teammates are manually assigned tasks by Lead — they don't find work on their own → **s15 Autonomous Agents**

## s14 Team Protocols

**Teaching branch:** `s14-team-protocols`

### Problem

s13's teammates only exchange plain text messages. The Lead can't tell "which reply corresponds to which request." For instance, if the Lead simultaneously asks alice to shut down and submit a plan, two replies arrive with no way to match them. How to add request-response matching to teammate communication?

### Function

Introduce a protocol message system: tag each request with `request_id`, `ProtocolService` maintains a pending request table, responses auto-match on arrival and update status. Supports shutdown and plan protocols.

New:

- `ProtocolState`: data class, requestId, type, sender, target, status, payload, timestamp
- `ProtocolService`: creates requests (auto-increment request_id), matches responses, dispatches by message type
- `RequestShutdownTool`: Lead initiates shutdown handshake (shutdown_request)
- `RequestPlanTool`: Lead asks teammate to submit a plan first
- `SubmitPlanTool`: teammate submits plan to Lead inbox (plan_approval_request)
- `ReviewPlanTool`: Lead approves or rejects plan by request_id (plan_approval_response)
- `ProtocolCheckInboxTool`: Lead routes protocol responses first when reading inbox, preventing state loss

### Design

Shutdown handshake:

```text
Lead request_shutdown("alice", "task complete, you can shut down")
  → ProtocolService creates ProtocolState(req_001, shutdown, pending)
  → MessageBus writes shutdown_request + metadata.request_id=req_001
  → alice idle loop reads inbox
  → ProtocolService.handleTeammateProtocolMessage replies shutdown_response + request_id=req_001
  → Lead consumeLeadInbox matches req_001, status → approved
  → alice exits
```

Plan approval protocol:

```text
Lead request_plan("bob", "refactor auth module")
  → bob submit_plan("will refactor in three steps...")
  → plan_approval_request written to Lead inbox (request_id=req_002)
  → Lead review_plan(req_002, true, "plan approved")
  → plan_approval_response written back to bob inbox
```

Key design decisions:

- **request_id matching**: ProtocolService uses auto-increment counter to generate unique IDs; responses carry back the same ID; Lead's `consumeLeadInbox` auto-matches and updates pending state
- **Teammate idle loop**: after completing current work, teammates don't exit immediately — they poll the inbox every second (`idleUntilMessage`), exit on shutdown, continue working on regular messages
- **Protocol messages routed first**: `ProtocolCheckInboxTool` filters protocol messages before returning regular ones, dispatching them to `ProtocolService` for handling — prevents protocol responses from being missed as regular messages
- **Hard enforcement not implemented**: plans are submitted and approved/rejected, but the teaching version doesn't enforce "can't execute without approval" as a hard gate — relies on prompt constraints

### Implementation

`ProtocolService` — request creation and response matching:

```java
public class ProtocolService {
    private final Map<String, ProtocolState> pending = new ConcurrentHashMap<>();
    private final AtomicInteger requestCounter = new AtomicInteger(1);

    public ProtocolState createRequest(String type, String target, String payload) {
        String requestId = "req_" + requestCounter.getAndIncrement();
        ProtocolState state = new ProtocolState(requestId, type, "lead", target, "pending", payload);
        pending.put(requestId, state);
        return state;
    }

    public ProtocolState matchResponse(String requestId, String status, String payload) {
        ProtocolState state = pending.get(requestId);
        if (state != null) {
            state.setStatus(status);
            state.setPayload(payload);
        }
        return state;
    }

    public void handleTeammateProtocolMessage(TeamMessage msg, Consumer<TeamMessage> replyFn) {
        // Auto-reply based on message type
        if ("shutdown_request".equals(msg.getType())) {
            replyFn.accept(new TeamMessage(msg.getTo(), msg.getFrom(),
                "shutdown_response", "acknowledged", msg.getMetadata().get("request_id")));
        }
    }
}
```

Shutdown tool — Lead initiates handshake:

```java
public ToolResult execute(JSONObject input) {
    String teammate = input.getString("teammate");
    String reason = input.getString("reason");
    ProtocolState state = protocolService.createRequest("shutdown", teammate, reason);
    messageBus.send(new TeamMessage("lead", teammate, "shutdown_request", reason,
        Map.of("request_id", state.getRequestId())));
    return new ToolResult("[Shutdown requested] request_id=" + state.getRequestId());
}
```

### Next

- Teammates poll inbox waiting for messages — they don't actively scan the task board for work → **s15 Autonomous Agents**
- Protocols only cover shutdown and plan scenarios → production needs a more complete Agent Communication Protocol

## s15 Autonomous Agents

**Teaching branch:** `s15-autonomous-agents`

### Problem

s14's teammates poll the inbox waiting for messages — if the Lead doesn't talk, teammates idle. But the task board might have pending tasks with nobody working on them. How to let idle teammates scan the board themselves and claim whatever is available?

### Function

Teammates no longer just wait for messages when idle — every 5 seconds they scan the `.tasks/` board, find pending tasks with no owner and satisfied dependencies, auto-claim and start working. Exit after 60 seconds of no work.

New:

- `TaskService.scanUnclaimedTasks()`: scans for pending, unowned tasks with all dependencies completed
- `ClaimTaskTool` adds `defaultOwner` injection: harness fixes teammate name as owner, preventing poaching
- `SpawnTeammateTool` adds autonomous mode: auto-enabled when TaskService is passed; teammate toolset adds list_tasks/claim_task/complete_task

### Design

Pull model — teammates pull tasks themselves, Lead doesn't individually assign:

```text
Lead create_task("create schema.sql", blockedBy=[])
Lead create_task("create API", blockedBy=["task_schema"])
Lead spawn_teammate("alice", "backend", autonomous=true)
Lead spawn_teammate("bob", "fullstack", autonomous=true)

alice:
  → WORK phase completes initial context
  → IDLE: poll every 5s
      check inbox first (protocol messages priority)
      then scanUnclaimedTasks() → finds "create schema.sql" → claimTask(taskId, "alice")
      → inject <auto-claimed>task: create schema.sql</auto-claimed>
      → back to WORK to execute
      → after completion, claim next unclaimed task
  → 60s no inbox + no tasks → exit

bob:
  → runs simultaneously, but "create API" depends on schema → blocked → won't claim
  → alice completes schema → complete_task reports Unblocked: "create API"
  → bob's next scanUnclaimedTasks → finds "create API" → claim → execute
```

Key design decisions:

- **Dependency-aware**: `scanUnclaimedTasks()` filters out tasks with unsatisfied blockedBy, ensuring teammates don't grab work that isn't ready
- **owner anti-poaching**: teammate's `claim_task` tool has `defaultOwner` forced to the teammate name (e.g. "alice") by the harness; the owner parameter is not exposed to the model in the tool definition
- **Idle dual check**: inbox first (protocol messages priority, shutdown responds immediately), then task board. Only when both inbox and task board are empty is it truly idle
- **No file locks**: teaching version uses only owner check for conflict prevention (already-claimed can't be re-claimed); extreme concurrency cases could have two teammates race for the same task

### Implementation

Teammate idle loop — scan board every 5 seconds:

```java
private String idleUntilWork(String teammateName, TaskService taskService, MessageBus bus) {
    long idleStart = System.currentTimeMillis();
    while (System.currentTimeMillis() - idleStart < 60_000) {
        // Check inbox first
        List<TeamMessage> inbox = bus.readInbox(teammateName);
        for (TeamMessage msg : inbox) {
            if ("shutdown_request".equals(msg.getType())) {
                bus.send(new TeamMessage(teammateName, "lead", "shutdown_response",
                    "ack", msg.getMetadata().get("request_id")));
                return null;  // exit
            }
            // Regular message → continue working
            return msg.getContent();
        }

        // Scan board for unclaimed tasks
        List<TaskRecord> unclaimed = taskService.scanUnclaimedTasks();
        if (!unclaimed.isEmpty()) {
            TaskRecord task = unclaimed.get(0);
            taskService.claimTask(task.getId(), teammateName);
            return "<auto-claimed>task: " + task.getSubject() + "</auto-claimed>";
        }

        try { Thread.sleep(5000); } catch (InterruptedException e) { break; }
    }
    return null;  // 60s no work, exit
}
```

### Next

- No concurrent claim protection — in extreme cases two teammates could claim the same task simultaneously → production needs file locks or database transactions
- Tool pool is fixed; external capabilities (MCP) not connected → **s16 MCP Plugin**

## s16 MCP Plugin

**Teaching branch:** `s16-mcp-plugin`

### Problem

So far, the tool pool is fixed at startup (BashTool, ReadFileTool, etc.). If the Agent needs new capabilities at runtime — like querying weather or operating GitHub Issues — it must stop, add code, and restart. How to let the Agent dynamically connect to external tools at runtime?

### Function

Connect to external MCP Servers via the `connect_mcp` tool, discover and register their tools. The tool pool is reassembled dynamically each turn — after connecting a new Server, its tools are available in the next turn. Uses mock servers for demonstration, no real MCP service dependency.

New:

- `McpManager`: maintains map of connected MCP Servers
- `McpClient`: interface, getName() / getTools() / callTool()
- `McpToolDefinition`: name, description, inputSchema, readOnly
- `McpToolName`: prefix naming `mcp__{server}__{tool}`, preventing conflicts
- `McpTool`: adapter converting MCP tool calls to the project's `Tool` interface
- `McpToolPool`: each turn assembles builtin + connect_mcp + connected MCP tools
- `DynamicMcpAgentLoop`: s16-specific loop, reassembles tool pool before each LLM call
- `MockMcpServers`: two mock servers — time (`get_current_time`) and weather (`get_current_weather`)

### Design

```text
Turn 1: model → connect_mcp("weather")
        → McpManager.connect("weather")
        → Calls McpClient.getTools() → discovers get_current_weather
        → Registers with McpManager
        → tool_result: "Connected to weather. Tools: mcp__weather__get_current_weather (readOnly)"

Turn 2: McpToolPool.assemble()
        → builtin tools: bash/read_file/write_file
        → + connect_mcp
        → + mcp__weather__get_current_weather (newly connected tool auto-appears)
        → ToolRegistry

        model → mcp__weather__get_current_weather({"city":"Shanghai"})
        → McpTool adapter → McpClient.callTool("get_current_weather", {...})
        → Returns mock weather data
```

Two mock MCP Servers:

```text
time
  └─ mcp__time__get_current_time (readOnly) → uses java.time for current time

weather
  └─ mcp__weather__get_current_weather (readOnly) → returns mock weather data
```

Key design decisions:

- **Prefix prevents conflicts**: all MCP tools uniformly named `mcp__{server}__{tool}` — different Servers can't collide, and the model can see the tool's source at a glance
- **Dynamic tool pool**: reassembled before each LLM call; newly connected Server tools auto-appear in the next turn. The loop itself doesn't perceive tool pool changes — it still dispatches by tool name
- **Adapter pattern**: `McpTool` adapts MCP tool definitions and calls to the existing `Tool` interface; the loop and ToolRegistry don't care whether a tool is built-in or MCP-sourced
- **readOnly annotation**: MCP tool definitions carry a readOnly attribute; the model and permission system can use this for safety judgments
- **Mock servers**: the teaching version doesn't depend on real MCP protocol (JSON-RPC, stdio/HTTP transport); mocks keep the learning focus on dynamic tool pools

### Implementation

`McpToolPool.assemble()` — dynamic assembly each turn:

```java
public class McpToolPool {
    private final List<Tool> builtinTools;
    private final McpManager mcpManager;

    public ToolRegistry assemble() {
        ToolRegistry registry = new ToolRegistry();
        // 1. Register builtin tools
        for (Tool tool : builtinTools) {
            registry.register(tool);
        }
        // 2. Register connect_mcp
        registry.register(new ConnectMcpTool(mcpManager));
        // 3. Register all connected MCP Server tools
        for (Map.Entry<String, McpClient> entry : mcpManager.getConnectedServers().entrySet()) {
            String serverName = entry.getKey();
            McpClient client = entry.getValue();
            for (McpToolDefinition def : client.getTools()) {
                String fullName = McpToolName.of(serverName, def.getName());
                registry.register(new McpTool(fullName, def, client));
            }
        }
        return registry;
    }
}
```

`DynamicMcpAgentLoop` — reassembles tool pool each turn:

```java
for (int turn = 0; turn < maxTurns; turn++) {
    // s16: reassemble tool pool each turn
    ToolRegistry registry = toolPool.assemble();

    AssistantMessage msg = llmClient.chat(systemPrompt, history, registry.definitions());
    history.add(msg.toMessage());

    for (ToolUseBlock toolUse : msg.getToolUses()) {
        Tool tool = registry.find(toolUse.getName());
        if (tool != null) {
            ToolResult result = tool.execute(toolUse.getInput());
            history.add(Message.toolResult(toolUse.getId(), result.getContent()));
        }
    }
}
```

`MockMcpServers` — time and weather mocks:

```java
public class MockMcpServers {
    public static McpClient createTimeServer() {
        return new McpClient() {
            public List<McpToolDefinition> getTools() {
                return List.of(new McpToolDefinition(
                    "get_current_time", "Returns the current time", ..., true));
            }
            public String callTool(String name, JSONObject args) {
                return LocalDateTime.now().toString();
            }
        };
    }

    public static McpClient createWeatherServer() {
        return new McpClient() {
            public List<McpToolDefinition> getTools() {
                return List.of(new McpToolDefinition(
                    "get_current_weather", "Returns weather for a city", ..., true));
            }
            public String callTool(String name, JSONObject args) {
                String city = args.getString("city");
                return city + ": 22°C, partly cloudy (mock)";
            }
        };
    }
}
```

### Next

- Uses mock servers — no real MCP protocol implementation (JSON-RPC, stdio/HTTP transport, tools/list and tools/call) → production needs a full MCP client
- MCP tools have no permission control; callable immediately after connection → s03's permission pipeline can be extended to MCP tools

---

*This changelog documents the evolution of mini-claude-code from a minimal ReAct loop to a feature-rich multi-agent coding platform, demonstrating core Agent Harness concepts through 16 incremental chapters.*
