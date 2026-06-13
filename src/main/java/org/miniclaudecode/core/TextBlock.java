package org.miniclaudecode.core;

public class TextBlock extends ContentBlock {

	private String text;

	public TextBlock() {
		super("text");
	}

	public TextBlock(String text) {
		super("text");
		this.text = text;
	}

	public String getText() {
		return text;
	}

	public void setText(String text) {
		this.text = text;
	}
}
