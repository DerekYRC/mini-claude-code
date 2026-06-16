package org.miniclaudecode.core;

/**
 * Anthropic Messages 里的 content block 基类。
 */
public abstract class ContentBlock {

	private String type;

	protected ContentBlock(String type) {
		this.type = type;
	}

	public String getType() {
		return type;
	}

	public void setType(String type) {
		this.type = type;
	}
}
