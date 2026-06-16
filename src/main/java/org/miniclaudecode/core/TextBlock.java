package org.miniclaudecode.core;

/**
 * 普通文本 content block。
 */
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
