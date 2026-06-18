package org.miniclaudecode.background;

import com.alibaba.fastjson.JSONObject;

/**
 * 后台执行判断逻辑。
 *
 * 两条规则：
 * 1. 模型显式设置 run_in_background=true → 后台
 * 2. 命令含慢操作关键词（install/build/test/deploy/compile 等）→ 后台兜底
 *
 * 只对 bash 工具生效，其他工具永远同步。
 */
public class BackgroundDecider {

    private static final String[] SLOW_KEYWORDS = {
        "install", "build", "test", "deploy", "compile",
        "docker build", "pip install", "npm install",
        "cargo build", "pytest", "make"
    };

    /**
     * 判断工具调用是否应走后台执行。
     *
     * @param toolName  工具名
     * @param toolInput 工具输入参数
     * @return true 表示应后台执行
     */
    public static boolean shouldRunBackground(String toolName, JSONObject toolInput) {
        // 规则 1：模型显式请求优先
        if (toolInput != null && toolInput.getBoolean("run_in_background") != null
                && toolInput.getBoolean("run_in_background")) {
            return true;
        }
        // 规则 2：启发式兜底
        return isSlowOperation(toolName, toolInput);
    }

    /**
     * 启发式：命令含慢操作关键词。
     * 只对 bash 工具生效，其他工具直接返回 false。
     */
    private static boolean isSlowOperation(String toolName, JSONObject toolInput) {
        if (!"bash".equals(toolName) || toolInput == null) {
            return false;
        }
        String cmd = toolInput.getString("command");
        if (cmd == null || cmd.isBlank()) {
            return false;
        }
        cmd = cmd.toLowerCase();
        for (String kw : SLOW_KEYWORDS) {
            if (cmd.contains(kw)) {
                return true;
            }
        }
        return false;
    }
}
