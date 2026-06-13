package org.miniclaudecode.core;

public class ThinkingBlock extends ContentBlock {

	private String thinking;

	private String signature;

	public ThinkingBlock() {
		super("thinking");
	}

	public ThinkingBlock(String thinking, String signature) {
		super("thinking");
		this.thinking = thinking;
		this.signature = signature;
	}

	public String getThinking() {
		return thinking;
	}

	public void setThinking(String thinking) {
		this.thinking = thinking;
	}

	public String getSignature() {
		return signature;
	}

	public void setSignature(String signature) {
		this.signature = signature;
	}
}
