package org.miniclaudecode.core;

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
