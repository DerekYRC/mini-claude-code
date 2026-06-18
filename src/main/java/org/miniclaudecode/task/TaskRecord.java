package org.miniclaudecode.task;

import java.util.ArrayList;
import java.util.List;

/**
 * 一条持久化任务。
 *
 * s10 使用普通 Java 类对应 .tasks/{id}.json，字段名保持和参考实现一致。
 */
public class TaskRecord {

	private String id;

	private String subject;

	private String description;

	private String status;

	private String owner;

	private List<String> blockedBy = new ArrayList<>();

	public TaskRecord() {
	}

	public TaskRecord(String id, String subject, String description, String status, String owner,
			List<String> blockedBy) {
		this.id = id;
		this.subject = subject;
		this.description = description;
		this.status = status;
		this.owner = owner;
		setBlockedBy(blockedBy);
	}

	public String getId() {
		return id;
	}

	public void setId(String id) {
		this.id = id;
	}

	public String getSubject() {
		return subject;
	}

	public void setSubject(String subject) {
		this.subject = subject;
	}

	public String getDescription() {
		return description;
	}

	public void setDescription(String description) {
		this.description = description;
	}

	public String getStatus() {
		return status;
	}

	public void setStatus(String status) {
		this.status = status;
	}

	public String getOwner() {
		return owner;
	}

	public void setOwner(String owner) {
		this.owner = owner;
	}

	public List<String> getBlockedBy() {
		if (blockedBy == null) {
			blockedBy = new ArrayList<>();
		}
		return blockedBy;
	}

	public void setBlockedBy(List<String> blockedBy) {
		this.blockedBy = blockedBy == null ? new ArrayList<>() : new ArrayList<>(blockedBy);
	}
}
