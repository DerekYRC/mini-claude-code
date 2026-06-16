package org.miniclaudecode.core;

import com.alibaba.fastjson.JSONObject;

/**
 * 兼容未来或非标准 content block，避免解析时直接丢掉未知数据。
 */
public class UnknownBlock extends ContentBlock {

	private JSONObject raw;

	public UnknownBlock(String type, JSONObject raw) {
		super(type);
		this.raw = raw;
	}

	public JSONObject getRaw() {
		return raw;
	}

	public void setRaw(JSONObject raw) {
		this.raw = raw;
	}
}
