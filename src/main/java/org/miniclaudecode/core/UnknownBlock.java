package org.miniclaudecode.core;

import com.alibaba.fastjson.JSONObject;

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
