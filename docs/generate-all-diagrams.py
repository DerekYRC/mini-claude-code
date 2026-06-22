#!/usr/bin/env python3
"""Generate all 16 chapter diagrams + embed into changelog."""
import json, os, subprocess, sys

ASSETS = "assets"
SCRIPT = ".agents/skills/fireworks-tech-graph/scripts/generate-from-template.py"
CHANGELOG = "changelog.md"

os.makedirs(ASSETS, exist_ok=True)

# ── Colour Palette (Style 1 Flat Icon) ──────────────────────
BLUE   = {"fill":"#eff6ff","stroke":"#2563eb","flow":"control"}
GREEN  = {"fill":"#f0fdf4","stroke":"#16a34a","flow":"read"}
PURPLE = {"fill":"#f5f3ff","stroke":"#8b5cf6","flow":"async"}
ORANGE = {"fill":"#fff7ed","stroke":"#f97316","flow":"data"}
RED    = {"fill":"#fef2f2","stroke":"#dc2626","flow":"feedback"}
GRAY   = {"fill":"#f9fafb","stroke":"#6b7280","flow":"neutral"}

C = {"blue":BLUE,"green":GREEN,"purple":PURPLE,"orange":ORANGE,"red":RED,"gray":GRAY}

def n(id, label, x, y, w, h, color="blue", kind="rect", sublabel="", type_label=""):
    """Build a node dict."""
    node = {"id":id, "kind":kind, "x":x, "y":y, "width":w, "height":h,
            "label":label, "fill":C[color]["fill"], "stroke":C[color]["stroke"]}
    if sublabel: node["sublabel"] = sublabel
    if type_label: node["type_label"] = type_label
    return node

def a(src, tgt, color="blue", label="", sp="right", tp="left", dashed=False):
    """Build an arrow dict."""
    arr = {"source":src, "target":tgt, "source_port":sp, "target_port":tp, "flow":C[color]["flow"]}
    if label: arr["label"] = label
    if dashed: arr["dashed"] = True
    return arr

def container(x, y, w, h, label, color="blue"):
    return {"x":x,"y":y,"width":w,"height":h,"label":label,"stroke":C[color]["stroke"],"fill":C[color]["fill"]}

def lg(*pairs):
    """Build legend from (flow_color, 'label') pairs."""
    return [{"flow":C[c]["flow"],"label":l} for c,l in pairs]

W, H = 960, 600

# ═══════════════════════════════════════════════════════════════
#  s01  Agent Loop
# ═══════════════════════════════════════════════════════════════
def s01():
    return [json.dumps({
        "template_type":"architecture","style":1,"width":W,"height":H,
        "title":"s01 Agent Loop — ReAct",
        "nodes":[
            n("user","User Input",400,30,160,44,"blue","user_avatar"),
            n("loop","AgentLoop\n(maxTurns: 20)",370,100,220,60,"blue","rounded_rect",type_label="CORE"),
            n("llm","LLM Client\n(Anthropic API)",355,200,250,56,"purple","double_rect",type_label="MODEL"),
            n("bash","BashTool\n/bin/sh -c <command>",370,300,220,56,"green","rect",type_label="TOOL"),
            n("result","Final Response",390,400,180,44,"green","rounded_rect"),
        ],
        "arrows":[
            a("user","loop","blue","prompt"),
            a("loop","llm","blue","① chat(history,tools)"),
            a("llm","bash","blue","② tool_use","right","top"),
            a("bash","llm","green","③ tool_result","left","bottom",True),
            a("llm","loop","blue","④ next turn / end_turn","right","right"),
            a("loop","result","green","stop_reason=\"end_turn\""),
        ],
        "legend":lg(("blue","Control flow"),("green","Tool result")),
        "legend_position":"bottom-left","legend_y":540
    })]

# ═══════════════════════════════════════════════════════════════
#  s02  Tool Dispatch
# ═══════════════════════════════════════════════════════════════
def s02():
    return [json.dumps({
        "template_type":"architecture","style":1,"width":960,"height":560,
        "title":"s02 Tool Dispatch — ToolRegistry",
        "nodes":[
            n("loop","AgentLoop",390,24,180,44,"blue","rounded_rect",type_label="CORE"),
            n("reg","ToolRegistry\n(LinkedHashMap)",360,100,240,56,"purple","rounded_rect",type_label="REGISTRY"),
            n("bash","BashTool",40,220,130,48,"green","rect",type_label="TOOL"),
            n("read","ReadFileTool",200,220,140,48,"green","rect",type_label="TOOL"),
            n("write","WriteFileTool",370,220,140,48,"green","rect",type_label="TOOL"),
            n("edit","EditFileTool",540,220,140,48,"green","rect",type_label="TOOL"),
            n("glob","GlobTool",710,220,140,48,"green","rect",type_label="TOOL"),
            n("pg","PathGuard\nresolve() canonical check",340,340,280,56,"orange","rect",type_label="SAFETY"),
        ],
        "arrows":[
            a("loop","reg","blue","registry.find(name)"),
            a("reg","bash","green","dispatch","bottom","top"),
            a("reg","read","green","dispatch","bottom","top"),
            a("reg","write","green","dispatch","bottom","top"),
            a("reg","edit","green","dispatch","bottom","top"),
            a("reg","glob","green","dispatch","bottom","top"),
            a("read","pg","orange","resolve(path)","bottom","top"),
            a("write","pg","orange","resolve(path)","bottom","top"),
            a("edit","pg","orange","resolve(path)","bottom","top"),
        ],
        "legend":lg(("blue","registry.find()"),("green","tool dispatch"),("orange","path check")),
        "legend_position":"bottom-left","legend_y":500
    })]

# ═══════════════════════════════════════════════════════════════
#  s03  Permission
# ═══════════════════════════════════════════════════════════════
def s03():
    return [json.dumps({
        "template_type":"flowchart","style":1,"width":960,"height":620,
        "title":"s03 Permission — Three Gates",
        "nodes":[
            n("start","tool_use",390,24,160,44,"blue","rounded_rect"),
            n("dispatch","PermissionManager.check()\n按工具类型分发",340,96,260,56,"purple","rounded_rect",type_label="DISPATCH"),
            n("bash_chk","bash 工具?\ndenyList → askPatterns",110,210,220,56,"red","rect",type_label="CHECK"),
            n("file_chk","write_file / edit_file?\ncanonical path 越界检查",600,210,260,56,"orange","rect",type_label="CHECK"),
            n("other","其他工具\n(read_file, glob...)",470,310,200,44,"green","rounded_rect"),
            n("deny","DENY\n拒绝原因 → tool_result",120,340,200,56,"red","rounded_rect"),
            n("user_ask","User y/N?",120,440,200,44,"orange","diamond"),
            n("allow","ALLOW\n→ execute()",470,440,180,56,"green","rounded_rect"),
        ],
        "arrows":[
            a("start","dispatch","blue"),
            a("dispatch","bash_chk","red","bash"),
            a("dispatch","file_chk","orange","file write"),
            a("dispatch","other","green","other"),
            a("bash_chk","deny","red","denyList 命中","left","top"),
            a("bash_chk","user_ask","orange","askPattern 命中","bottom","top"),
            a("file_chk","deny","red","越界","right","top"),
            a("file_chk","allow","green","safe","right","right"),
            a("user_ask","allow","green","y","right","top"),
            a("user_ask","deny","red","N","bottom","right"),
            a("other","allow","green"),
        ],
        "legend":lg(("blue","dispatch"),("green","allow"),("orange","confirm"),("red","deny")),
        "legend_position":"bottom-left","legend_y":570
    })]

# ═══════════════════════════════════════════════════════════════
#  s04  Hooks
# ═══════════════════════════════════════════════════════════════
def s04():
    return [json.dumps({
        "template_type":"architecture","style":1,"width":960,"height":600,
        "title":"s04 Hooks — 4 Event Points",
        "containers":[
            container(380,100,200,420,"AgentLoop", "blue"),
        ],
        "nodes":[
            n("hm","HookManager\nMap<String,List<Hook>>\nregister() / trigger()",310,24,340,60,"purple","rounded_rect",type_label="MANAGER"),
            n("u","UserPromptSubmit\n(外部触发)",80,120,200,50,"blue","rounded_rect",type_label="EVENT"),
            n("pre","PreToolUse\n可阻止执行",80,240,200,50,"orange","rounded_rect",type_label="EVENT"),
            n("exec","Tool.execute()",420,240,160,44,"green","rect"),
            n("post","PostToolUse\n只读观察",80,360,200,50,"purple","rounded_rect",type_label="EVENT"),
            n("stop","Stop\n统计收集",80,460,200,50,"blue","rounded_rect",type_label="EVENT"),
        ],
        "arrows":[
            a("hm","u","blue","register"),
            a("hm","pre","orange","register"),
            a("hm","post","purple","register"),
            a("hm","stop","blue","register"),
            a("u","pre","blue","pass"),
            a("pre","exec","green","pass()","left","left"),
            a("pre","hm","orange","block(msg) → tool_result","bottom","right",True),
            a("exec","post","blue","done"),
            a("post","stop","blue","pass"),
        ],
        "legend":lg(("blue","pass-through"),("green","execute"),("orange","block"),("purple","observe only")),
        "legend_position":"bottom-left","legend_y":540
    })]

# ═══════════════════════════════════════════════════════════════
#  s05  Todo
# ═══════════════════════════════════════════════════════════════
def s05():
    return [json.dumps({
        "template_type":"flowchart","style":1,"width":960,"height":600,
        "title":"s05 Todo — Plan then Execute",
        "nodes":[
            n("task","Multi-step Task\n\"重构 hello.py\"",370,24,220,52,"blue","rounded_rect"),
            n("write","todo_write\n(全量替换内存)",370,112,220,52,"purple","rounded_rect",type_label="TOOL"),
            n("p1","[pending] step 1\n检查文件是否存在",80,220,200,50,"blue","rect"),
            n("p2","[pending] step 2\n补充类型标注",320,220,200,50,"blue","rect"),
            n("p3","[pending] step 3\n添加 docstring",560,220,200,50,"blue","rect"),
            n("ip","[in_progress]\nbash ls → edit_file",320,320,200,56,"orange","rect"),
            n("done","[completed] × 3\ntodo_write 更新全部完成",320,430,280,52,"green","rounded_rect"),
        ],
        "arrows":[
            a("task","write","blue","plan first"),
            a("write","p1","blue","split"),
            a("write","p2","blue","split"),
            a("write","p3","blue","split"),
            a("p1","ip","orange","start"),
            a("p2","ip","orange","start"),
            a("p3","ip","orange","start"),
            a("ip","done","green","complete all"),
        ],
        "legend":lg(("blue","todo_write"),("orange","in_progress"),("green","completed")),
        "legend_position":"bottom-left","legend_y":540
    })]

# ═══════════════════════════════════════════════════════════════
#  s06  Subagent
# ═══════════════════════════════════════════════════════════════
def s06():
    return [json.dumps({
        "template_type":"architecture","style":1,"width":960,"height":560,
        "title":"s06 Subagent — Delegation with Clean Context",
        "nodes":[
            n("parent","Parent Agent\nmessages[...]\ntools incl. task",360,24,240,64,"blue","rounded_rect",type_label="PARENT"),
            n("task","TaskTool\nexecute()",400,120,160,44,"purple","rounded_rect",type_label="DELEGATE"),
            n("child","Subagent\nmessages = [description]\nmaxTurns = 30",330,210,300,68,"green","rounded_rect",sublabel="clean context",type_label="CHILD"),
            n("ctools","Tools: bash/read/write/edit/glob\n(no task — cannot recurse)",300,320,360,56,"green","rect",type_label="TOOLS"),
            n("summary","Final Text Summary\n→ parent tool_result",340,430,280,56,"orange","rounded_rect",type_label="RESULT"),
        ],
        "arrows":[
            a("parent","task","blue","task(description)"),
            a("task","child","purple","new AgentLoop(client,subTools,listener,30)"),
            a("child","ctools","green","execute in isolation","bottom","top"),
            a("child","summary","green","return text summary"),
            a("summary","parent","orange","parent sees summary only"),
        ],
        "legend":lg(("blue","parent → task"),("purple","delegation"),("green","isolated execution"),("orange","result")),
        "legend_position":"bottom-left","legend_y":500
    })]

# ═══════════════════════════════════════════════════════════════
#  s07  Skill Loading
# ═══════════════════════════════════════════════════════════════
def s07():
    return [json.dumps({
        "template_type":"data-flow","style":1,"width":960,"height":560,
        "title":"s07 Skill Loading — Index in Prompt, Body on Demand",
        "nodes":[
            n("scan","Startup: SkillRegistry\nscan skills/*/SKILL.md\nparse frontmatter → {name,description,body}",310,24,340,68,"purple","rounded_rect",type_label="REGISTRY"),
            n("dir","Cheap Layer\nsystem prompt = directory\n- code-review: 代码审查\n- java-cli: Java CLI 规范",290,132,380,64,"green","rect",type_label="PROMPT"),
            n("load","load_skill(\"code-review\")\nregistry.find(name)",340,240,280,52,"blue","rounded_rect",type_label="TOOL"),
            n("inject","Expensive Layer\n<skill name=\"code-review\">\nBODY\n</skill>\n→ tool_result 注入对话",300,340,360,80,"orange","rect",type_label="INJECT"),
            n("apply","Model follows\nskill rules",370,460,220,44,"green","rounded_rect"),
        ],
        "arrows":[
            a("scan","dir","purple","directory → prompt","bottom","top"),
            a("dir","load","green","model picks skill"),
            a("load","inject","blue","find → return body"),
            a("inject","apply","orange","context injection"),
        ],
        "legend":lg(("purple","scan + store"),("green","cheap layer"),("blue","on-demand load"),("orange","inject into context")),
        "legend_position":"bottom-left","legend_y":500
    })]

# ═══════════════════════════════════════════════════════════════
#  s08  Context Compact
# ═══════════════════════════════════════════════════════════════
def s08():
    return [json.dumps({
        "template_type":"flowchart","style":1,"width":960,"height":640,
        "title":"s08 Context Compact — 4-Layer Pipeline",
        "nodes":[
            n("pre","beforeLlm(messages)\n每轮 LLM 调用前",320,24,320,48,"blue","rounded_rect"),
            n("l1","L1: toolResultBudget\nlast result > 200KB → persist to disk\ncontext keeps path only",290,100,380,56,"green","rect",type_label="LAYER 1"),
            n("l2","L2: snipCompact\n> 50 messages → cut middle\nhead=3, tail=47, protect pairs",290,184,380,56,"green","rect",type_label="LAYER 2"),
            n("l3","L3: microCompact\nkeep recent 3, old →\n\"[Earlier tool result compacted]\"",290,268,380,56,"green","rect",type_label="LAYER 3"),
            n("l4","L4: compactHistory\n> 50KB → save transcript\n→ LLM summary",340,352,280,56,"orange","rect",type_label="LAYER 4"),
            n("re","prompt_is_too_long?\nreactiveCompact\nsummary + keep last 10 → retry",300,448,360,56,"red","rounded_rect"),
        ],
        "arrows":[
            a("pre","l1","blue"),
            a("l1","l2","green","cheap first"),
            a("l2","l3","green","then finer"),
            a("l3","l4","orange","still too big → LLM"),
            a("l4","re","red","API error → reactive"),
        ],
        "legend":lg(("blue","entry"),("green","cheap/fidelity"),("orange","expensive"),("red","fallback")),
        "legend_position":"bottom-left","legend_y":590
    })]

# ═══════════════════════════════════════════════════════════════
#  s09  Memory
# ═══════════════════════════════════════════════════════════════
def s09():
    return [json.dumps({
        "template_type":"memory","style":1,"width":960,"height":620,
        "title":"s09 Memory — 3 Subsystems",
        "nodes":[
            n("idx","MEMORY.md Index\n常驻 system prompt",340,24,280,48,"blue","rounded_rect",type_label="INDEX"),
            n("sel","1. MemorySelector\nselect ≤ 5 relevant\nLLM fallback → keyword",120,120,240,60,"green","rect",type_label="SUBSYSTEM"),
            n("inj","<relevant_memories>\n...bodies...\n</relevant_memories>",120,220,240,60,"green","rect",type_label="INJECT"),
            n("snap","pre-compact\nsnapshot(messages)",540,120,240,56,"purple","rect",type_label="SNAPSHOT"),
            n("ext","2. MemoryExtractor\nLLM extract from snapshot\n→ new .memory/*.md",540,230,280,64,"orange","rect",type_label="SUBSYSTEM"),
            n("con","3. MemoryConsolidator\n≥ 10 files → merge dedup\nupdate MEMORY.md",330,370,300,60,"red","rect",type_label="SUBSYSTEM"),
        ],
        "arrows":[
            a("idx","sel","blue","pick"),
            a("sel","inj","green","inject bodies"),
            a("sel","snap","purple","session context","right","top"),
            a("snap","ext","purple","pre-compact data"),
            a("ext","con","orange","write files"),
            a("con","idx","red","update index"),
        ],
        "legend":lg(("blue","index"),("green","selector"),("purple","snapshot"),("orange","extract"),("red","consolidate")),
        "legend_position":"bottom-left","legend_y":560
    })]

# ═══════════════════════════════════════════════════════════════
#  s10  Task System
# ═══════════════════════════════════════════════════════════════
def s10():
    return [json.dumps({
        "template_type":"state-machine","style":1,"width":960,"height":560,
        "title":"s10 Task System — State Machine",
        "nodes":[
            n("create","create_task\nsubject, description,\nblockedBy: []",340,24,280,56,"blue","rounded_rect",type_label="CREATE"),
            n("p","pending\nowner: null",140,180,180,56,"blue","rect",type_label="STATE"),
            n("ip","in_progress\nowner: \"agent\"",420,180,200,56,"orange","rect",type_label="STATE"),
            n("c","completed",700,180,160,56,"green","rect",type_label="STATE"),
            n("store","TaskStore\n.tasks/{id}.json",400,320,200,52,"purple","rounded_rect",type_label="PERSIST"),
            n("unblock","scan unblocked\n→ report \"Unblocked: ...\"",370,430,260,52,"green","rounded_rect",type_label="SCAN"),
        ],
        "arrows":[
            a("create","p","blue","persist"),
            a("p","ip","orange","claim_task\n[blockedBy all done?]"),
            a("ip","c","green","complete_task"),
            a("p","store","blue","save"),
            a("ip","store","orange","save"),
            a("c","store","green","save"),
            a("c","unblock","green","find unblocked"),
            a("unblock","p","blue","notify"),
        ],
        "legend":lg(("blue","pending"),("orange","in_progress"),("green","completed"),("purple","persist")),
        "legend_position":"bottom-left","legend_y":500
    })]

# ═══════════════════════════════════════════════════════════════
#  s11  Background Tasks
# ═══════════════════════════════════════════════════════════════
def s11():
    return [json.dumps({
        "template_type":"architecture","style":1,"width":960,"height":560,
        "title":"s11 Background Tasks — Sync & Async Paths",
        "nodes":[
            n("call","Tool Call (bash)",400,24,160,44,"blue","rounded_rect"),
            n("decide","BackgroundDecider\nshouldRunBackground()\n① run_in_background = true?\n② keyword heuristic",310,100,340,72,"purple","rounded_rect",type_label="DECIDER"),
            n("sync","Sync Path\nread_file / write_file / glob\nexecute() → tool_result 立即返回",140,230,300,64,"green","rect",type_label="SYNC"),
            n("bg","Background Path\nnpm install / docker build...\ndaemon thread → placeholder result",520,230,340,64,"orange","rect",type_label="ASYNC"),
            n("next","Next Turn\ncollectNotifications()\n→ <task_notification> inject",300,370,360,64,"blue","rounded_rect"),
        ],
        "arrows":[
            a("call","decide","blue"),
            a("decide","sync","green","fast"),
            a("decide","bg","orange","slow"),
            a("sync","next","green","immediate"),
            a("bg","next","orange","done → notify","bottom","bottom"),
        ],
        "legend":lg(("blue","entry"),("green","sync"),("orange","background")),
        "legend_position":"bottom-left","legend_y":500
    })]

# ═══════════════════════════════════════════════════════════════
#  s12  Cron Scheduler
# ═══════════════════════════════════════════════════════════════
def s12():
    return [json.dumps({
        "template_type":"architecture","style":1,"width":960,"height":560,
        "title":"s12 Cron Scheduler — Timed Trigger",
        "nodes":[
            n("reg","schedule_cron\n(\"0 9 * * *\", prompt,\nrecurring, durable)",290,24,380,64,"blue","rounded_rect",type_label="SCHEDULE"),
            n("hutool","Hutool CronUtil\nCronUtil.schedule()\nCronPattern.of() validate",330,124,300,64,"purple","rounded_rect",type_label="ENGINE"),
            n("fire","CronTask.execute()\n→ onFire callback\none-shot → auto cancel",320,230,320,56,"orange","rect",type_label="FIRE"),
            n("lock","agentLock (synchronized)\ninject \"[Scheduled]\" + prompt",310,330,340,56,"red","rounded_rect",type_label="LOCK"),
            n("agent","BackgroundAgentLoop\n.run()",370,430,220,48,"green","rounded_rect"),
            n("store","CronStore\n.scheduled_tasks.json\ndurable=true → persist",310,520,340,52,"purple","rect",type_label="PERSIST"),
        ],
        "arrows":[
            a("reg","hutool","blue","register"),
            a("hutool","fire","purple","cron match"),
            a("fire","lock","orange","callback"),
            a("lock","agent","red","run agent"),
            a("reg","store","blue","durable → save"),
            a("store","hutool","purple","startup → reload"),
        ],
        "legend":lg(("blue","schedule"),("purple","Hutool"),("orange","fire"),("red","lock")),
        "legend_position":"bottom-left","legend_y":500
    })]

# ═══════════════════════════════════════════════════════════════
#  s13  Agent Teams
# ═══════════════════════════════════════════════════════════════
def s13():
    return [json.dumps({
        "template_type":"architecture","style":1,"width":960,"height":600,
        "title":"s13 Agent Teams — Lead + Teammates",
        "nodes":[
            n("lead","Lead Agent\ntools: bash,read,write,\nspawn_teammate,send,\ncheck_inbox",330,24,300,72,"blue","rounded_rect",type_label="LEAD"),
            n("spawn","SpawnTeammateTool\nactiveTeammates\n防同名重复",390,132,180,48,"purple","rounded_rect",type_label="SPAWN"),
            n("alice","Alice (backend)\nbash/read/write/send\nmaxTurns=10, daemon",80,250,240,64,"green","rounded_rect",type_label="TEAMMATE"),
            n("bob","Bob (tester)\nbash/read/write/send\nmaxTurns=10, daemon",360,250,240,64,"green","rounded_rect",type_label="TEAMMATE"),
            n("charlie","Charlie (frontend)\nbash/read/write/send\nmaxTurns=10, daemon",640,250,240,64,"green","rounded_rect",type_label="TEAMMATE"),
            n("bus","MessageBus\n.mailboxes/{name}.jsonl\nsend(): append JSONL line\nreadInbox(): read + delete",290,390,380,80,"orange","rounded_rect",type_label="COMMS"),
        ],
        "arrows":[
            a("lead","spawn","blue","spawn_teammate(name,role,prompt)"),
            a("spawn","alice","purple","daemon thread"),
            a("spawn","bob","purple","daemon thread"),
            a("spawn","charlie","purple","daemon thread"),
            a("alice","bus","green","send_msg → lead"),
            a("bob","bus","green","send_msg → lead"),
            a("charlie","bus","green","send_msg → lead"),
            a("bus","lead","orange","readInbox → <inbox> inject"),
        ],
        "legend":lg(("blue","lead control"),("purple","spawn"),("green","teammate work"),("orange","message bus")),
        "legend_position":"bottom-left","legend_y":540
    })]

# ═══════════════════════════════════════════════════════════════
#  s14  Team Protocols
# ═══════════════════════════════════════════════════════════════
def s14():
    return [json.dumps({
        "template_type":"sequence","style":1,"width":960,"height":600,
        "title":"s14 Team Protocols — Request/Response with request_id",
        "nodes":[
            n("lead","Lead",80,100,140,40,"blue","rounded_rect",type_label="LEAD"),
            n("proto","ProtocolService\npendingRequests\n(ConcurrentHashMap)",380,80,220,60,"purple","rounded_rect",type_label="PROTOCOL"),
            n("alice","Alice",720,100,140,40,"green","rounded_rect",type_label="TEAMMATE"),
        ],
        "arrows":[
            a("lead","proto","blue","request_shutdown(\"alice\")","right","top","y_override=140"),
            a("proto","alice","purple","shutdown_request\n+ metadata.request_id","right","left","y_override=200"),
            a("alice","proto","green","shutdown_response\n+ request_id","left","right","y_override=260"),
            a("proto","lead","purple","match → approved","left","top","y_override=320"),
            a("lead","proto","blue","request_plan(\"bob\")","right","top","y_override=380"),
            a("proto","alice","purple","plan_approval_request","right","left","y_override=440"),
            a("alice","proto","green","review_plan(req_id, approve)","left","right","y_override=500"),
        ],
        "legend":lg(("blue","lead request"),("purple","protocol message"),("green","teammate response")),
        "legend_position":"bottom-left","legend_y":540
    })]

# ═══════════════════════════════════════════════════════════════
#  s15  Autonomous Agents
# ═══════════════════════════════════════════════════════════════
def s15():
    return [json.dumps({
        "template_type":"flowchart","style":1,"width":960,"height":640,
        "title":"s15 Autonomous Agents — Self-Organization",
        "nodes":[
            n("lead","Lead: create_task × 3\nspawn_teammate(alice, bob)\n(autonomous=true)",290,24,380,56,"blue","rounded_rect",type_label="LEAD"),
            n("work","WORK phase\n完成当前任务",380,112,200,44,"green","rounded_rect"),
            n("poll","IDLE: every 5s",390,190,180,44,"orange","rounded_rect"),
            n("inbox","① read inbox\nyes → WORK",140,280,180,44,"purple","diamond"),
            n("scan","② scanUnclaimedTasks()\npending + no owner\n+ blockedBy all done",540,270,300,64,"purple","rect"),
            n("claim","③ claimTask(id, name)\nowner = harness fixed\ninject <auto-claimed>",540,380,300,64,"green","rect"),
            n("exit","EXIT\n60s timeout\nor shutdown",380,500,200,52,"red","rounded_rect"),
        ],
        "arrows":[
            a("lead","work","blue","start"),
            a("work","poll","green","task done"),
            a("poll","inbox","orange"),
            a("inbox","scan","purple","no msgs"),
            a("inbox","work","green","msg received"),
            a("scan","claim","green","found task"),
            a("scan","poll","orange","no task → sleep 5s"),
            a("claim","work","green","→ WORK"),
            a("poll","exit","red","60s idle"),
            a("inbox","exit","red","shutdown_request"),
        ],
        "legend":lg(("blue","lead"),("green","work"),("orange","idle poll"),("purple","check"),("red","exit")),
        "legend_position":"bottom-left","legend_y":590
    })]

# ═══════════════════════════════════════════════════════════════
#  s16  MCP Plugin
# ═══════════════════════════════════════════════════════════════
def s16():
    return [json.dumps({
        "template_type":"architecture","style":1,"width":960,"height":600,
        "title":"s16 MCP Plugin — Dynamic Tool Pool",
        "nodes":[
            n("pool","McpToolPool\n.assemble() per turn",330,24,300,52,"blue","rounded_rect",type_label="POOL"),
            n("builtin","builtin tools\nbash / read_file\n/ write_file",120,130,220,60,"green","rect",type_label="STATIC"),
            n("connect","connect_mcp\n(name) → discover tools",420,130,220,60,"purple","rounded_rect",type_label="CONNECT"),
            n("mcp_tools","connected MCP tools\nmcp__time__get_current_time\nmcp__weather__get_current_weather",570,240,360,64,"orange","rect",type_label="DYNAMIC"),
            n("loop","DynamicMcpAgentLoop\nregistry = pool.assemble()\nper LLM call",340,360,280,64,"blue","rounded_rect",type_label="LOOP"),
            n("mock","Mock MCP Servers\ntime (java.time)\nweather (mock data)",570,460,320,64,"purple","rect",type_label="MOCK"),
        ],
        "arrows":[
            a("pool","builtin","green","register"),
            a("pool","connect","purple","register"),
            a("pool","mcp_tools","orange","register connected"),
            a("builtin","loop","green","dispatch"),
            a("connect","mcp_tools","purple","discover → prefix mcp__{srv}__{tool}","bottom","top"),
            a("mcp_tools","loop","orange","dispatch"),
            a("mock","mcp_tools","purple","tools/list → tools/call","top","right",True),
        ],
        "legend":lg(("green","builtin"),("purple","connect"),("orange","MCP tools"),("blue","loop")),
        "legend_position":"bottom-left","legend_y":540
    })]

# ═══════════════════════════════════════════════════════════════
#  Generate all
# ═══════════════════════════════════════════════════════════════
builders = {
    "s01-agent-loop": (s01, "architecture"),
    "s02-tool-dispatch": (s02, "architecture"),
    "s03-permission": (s03, "flowchart"),
    "s04-hooks": (s04, "architecture"),
    "s05-todo": (s05, "flowchart"),
    "s06-subagent": (s06, "architecture"),
    "s07-skill-loading": (s07, "data-flow"),
    "s08-context-compact": (s08, "flowchart"),
    "s09-memory": (s09, "memory"),
    "s10-task-system": (s10, "state-machine"),
    "s11-background-tasks": (s11, "architecture"),
    "s12-cron-scheduler": (s12, "architecture"),
    "s13-agent-teams": (s13, "architecture"),
    "s14-team-protocols": (s14, "sequence"),
    "s15-autonomous-agents": (s15, "flowchart"),
    "s16-mcp-plugin": (s16, "architecture"),
}

for name, (builder, tmpl) in builders.items():
    svg_path = f"{ASSETS}/{name}.svg"
    png_path = f"{ASSETS}/{name}.png"
    data = builder()[0]

    # The script takes JSON as string arg (4th positional)
    result = subprocess.run(
        ["python3", SCRIPT, tmpl, svg_path, data],
        capture_output=True, text=True
    )
    if result.returncode != 0:
        print(f"FAIL {name}: {result.stderr[:200]}")
        continue

    # Validate SVG
    r = subprocess.run(["python3","-c",
        f"import xml.etree.ElementTree as ET; ET.parse('{svg_path}'); print('✓')"],
        capture_output=True, text=True)
    if "✓" not in r.stdout:
        print(f"XML INVALID {name}")
        continue

    # Export 2x PNG
    # cairosvg can't handle CJK well, but try; fall back to rsvg-convert
    r = subprocess.run(["python3","-c",
        f"import cairosvg; cairosvg.svg2png(url='{svg_path}', write_to='{png_path}', scale=2)"],
        capture_output=True, text=True)
    if r.returncode != 0:
        # fallback: rsvg-convert
        subprocess.run(["rsvg-convert","-z","2","-o",png_path,svg_path], check=True)

    # Clean up the JSON data file that the template script writes
    data_file = svg_path.replace(".svg",".json")
    if os.path.exists(data_file):
        pass  # keep it for debugging if needed

    print(f"✓ {name} → {svg_path} + {png_path}")

print(f"\nDone. {len(builders)} diagrams in {ASSETS}/")
