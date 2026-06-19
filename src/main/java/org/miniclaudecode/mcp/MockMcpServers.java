package org.miniclaudecode.mcp;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Supplier;

/**
 * 两个 mock MCP server。
 *
 * time/weather 都不访问网络；它们只模拟“外部服务暴露工具”的形态。
 */
public final class MockMcpServers {

	private static final Map<String, Supplier<McpClient>> FACTORIES = new LinkedHashMap<>();

	static {
		FACTORIES.put("time", MockMcpServers::timeServer);
		FACTORIES.put("weather", MockMcpServers::weatherServer);
	}

	private MockMcpServers() {
	}

	public static Set<String> availableNames() {
		return FACTORIES.keySet();
	}

	public static McpClient create(String name) {
		Supplier<McpClient> factory = FACTORIES.get(name);
		return factory == null ? null : factory.get();
	}

	private static McpClient timeServer() {
		McpClient client = new McpClient("time");
		JSONObject properties = new JSONObject()
				.fluentPut("timezone", new JSONObject()
						.fluentPut("type", "string")
						.fluentPut("description", "IANA timezone, for example Asia/Shanghai"));
		JSONObject schema = new JSONObject()
				.fluentPut("type", "object")
				.fluentPut("properties", properties)
				.fluentPut("required", new JSONArray());

		McpToolDefinition tool = new McpToolDefinition("get_current_time",
				"Get current time for a timezone. (readOnly)", schema, true);

		Map<String, McpClient.Handler> handlers = new LinkedHashMap<>();
		handlers.put("get_current_time", input -> {
			String timezone = input.getString("timezone");
			try {
				ZoneId zone = (timezone == null || timezone.isBlank())
						? ZoneId.systemDefault()
						: ZoneId.of(timezone);
				String now = ZonedDateTime.now(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME);
				return "[time] " + now + " (" + zone + ")";
			}
			catch (DateTimeException e) {
				return "Error: invalid timezone '" + timezone + "'";
			}
		});

		client.register(Arrays.asList(tool), handlers);
		return client;
	}

	private static McpClient weatherServer() {
		McpClient client = new McpClient("weather");
		JSONObject properties = new JSONObject()
				.fluentPut("city", new JSONObject()
						.fluentPut("type", "string")
						.fluentPut("description", "City name, for example Shanghai"));
		JSONObject schema = new JSONObject()
				.fluentPut("type", "object")
				.fluentPut("properties", properties)
				.fluentPut("required", new JSONArray().fluentAdd("city"));

		McpToolDefinition tool = new McpToolDefinition("get_current_weather",
				"Get mock current weather for a city. (readOnly)", schema, true);

		Map<String, String> data = new LinkedHashMap<>();
		data.put("beijing", "Beijing: 27C, sunny, north wind");
		data.put("shanghai", "Shanghai: 26C, cloudy, light breeze");
		data.put("hangzhou", "Hangzhou: 25C, light rain, humid");
		data.put("san francisco", "San Francisco: 18C, foggy, west wind");

		Map<String, McpClient.Handler> handlers = new LinkedHashMap<>();
		handlers.put("get_current_weather", input -> {
			String city = input.getString("city");
			if (city == null || city.isBlank()) {
				return "Error: city is required";
			}
			String key = city.trim().toLowerCase();
			return "[weather] " + data.getOrDefault(key,
					city.trim() + ": 22C, partly cloudy, mock weather");
		});

		client.register(Arrays.asList(tool), handlers);
		return client;
	}
}
