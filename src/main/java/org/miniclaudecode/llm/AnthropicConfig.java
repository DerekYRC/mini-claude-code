package org.miniclaudecode.llm;

public class AnthropicConfig {

	private static final int DEFAULT_MAX_TOKENS = 8000;

	private String baseUrl;

	private String apiKey;

	private String model;

	private String systemPrompt = systemPrompt(System.getProperty("user.dir"));

	private int maxTokens = DEFAULT_MAX_TOKENS;

	private int timeoutMillis = 120000;

	public static String systemPrompt(String workdir) {
		return "You are a coding agent at " + workdir + ". Use bash to solve tasks. Act, don't explain.";
	}

	public String getBaseUrl() {
		return baseUrl;
	}

	public void setBaseUrl(String baseUrl) {
		this.baseUrl = baseUrl;
	}

	public String getApiKey() {
		return apiKey;
	}

	public void setApiKey(String apiKey) {
		this.apiKey = apiKey;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

	public String getSystemPrompt() {
		return systemPrompt;
	}

	public void setSystemPrompt(String systemPrompt) {
		this.systemPrompt = systemPrompt;
	}

	public int getMaxTokens() {
		return maxTokens;
	}

	public void setMaxTokens(int maxTokens) {
		this.maxTokens = maxTokens;
	}

	public int getTimeoutMillis() {
		return timeoutMillis;
	}

	public void setTimeoutMillis(int timeoutMillis) {
		this.timeoutMillis = timeoutMillis;
	}
}
