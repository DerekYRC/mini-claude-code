package org.miniclaudecode.task;

import com.alibaba.fastjson.JSON;

import java.util.ArrayList;
import java.util.List;

/**
 * 任务状态机。
 *
 * TaskStore 只管文件，TaskService 才负责 pending -> in_progress -> completed 和依赖检查。
 */
public class TaskService {

	private static final String PENDING = "pending";

	private static final String IN_PROGRESS = "in_progress";

	private static final String COMPLETED = "completed";

	private final TaskStore store;

	public TaskService(TaskStore store) {
		this.store = store;
	}

	public TaskRecord createTask(String subject, String description, List<String> blockedBy) {
		if (subject == null || subject.isBlank()) {
			throw new IllegalArgumentException("subject is required");
		}
		TaskRecord task = new TaskRecord(store.nextId(),
				subject.trim(),
				description == null ? "" : description,
				PENDING,
				null,
				cleanBlockedBy(blockedBy));
		return store.save(task);
	}

	public String listTasks() {
		List<TaskRecord> tasks = store.list();
		if (tasks.isEmpty()) {
			return "No tasks. Use create_task to add some.";
		}
		StringBuilder builder = new StringBuilder();
		for (TaskRecord task : tasks) {
			if (builder.length() > 0) {
				builder.append("\n");
			}
			builder.append("[")
					.append(task.getStatus())
					.append("] ")
					.append(task.getId())
					.append(": ")
					.append(task.getSubject());
			if (task.getOwner() != null && !task.getOwner().isBlank()) {
				builder.append(" [").append(task.getOwner()).append("]");
			}
			if (!task.getBlockedBy().isEmpty()) {
				builder.append(" (blockedBy: ").append(String.join(", ", task.getBlockedBy())).append(")");
			}
		}
		return builder.toString();
	}

	public List<TaskRecord> scanUnclaimedTasks() {
		List<TaskRecord> available = new ArrayList<>();
		for (TaskRecord task : store.list()) {
			if (PENDING.equals(task.getStatus())
					&& (task.getOwner() == null || task.getOwner().isBlank())
					&& blockingDependencies(task).isEmpty()) {
				available.add(task);
			}
		}
		return available;
	}

	public String getTask(String taskId) {
		return JSON.toJSONString(store.load(taskId), true);
	}

	public boolean canStart(String taskId) {
		return blockingDependencies(store.load(taskId)).isEmpty();
	}

	public String claimTask(String taskId, String owner) {
		TaskRecord task = store.load(taskId);
		if (!PENDING.equals(task.getStatus())) {
			return "Task " + taskId + " is " + task.getStatus() + ", cannot claim";
		}
		if (task.getOwner() != null && !task.getOwner().isBlank()) {
			return "Task " + taskId + " already owned by " + task.getOwner();
		}
		List<String> blocked = blockingDependencies(task);
		if (!blocked.isEmpty()) {
			return "Blocked by: " + blocked;
		}
		task.setOwner(owner == null || owner.isBlank() ? "agent" : owner);
		task.setStatus(IN_PROGRESS);
		store.save(task);
		return "Claimed " + task.getId() + " (" + task.getSubject() + ")";
	}

	public String completeTask(String taskId) {
		TaskRecord task = store.load(taskId);
		if (!IN_PROGRESS.equals(task.getStatus())) {
			return "Task " + taskId + " is " + task.getStatus() + ", cannot complete";
		}
		task.setStatus(COMPLETED);
		store.save(task);
		List<String> unblocked = findUnblockedSubjects();
		String message = "Completed " + task.getId() + " (" + task.getSubject() + ")";
		if (!unblocked.isEmpty()) {
			message += "\nUnblocked: " + String.join(", ", unblocked);
		}
		return message;
	}

	private List<String> findUnblockedSubjects() {
		List<String> unblocked = new ArrayList<>();
		for (TaskRecord task : store.list()) {
			if (PENDING.equals(task.getStatus()) && !task.getBlockedBy().isEmpty()
					&& blockingDependencies(task).isEmpty()) {
				unblocked.add(task.getSubject());
			}
		}
		return unblocked;
	}

	private List<String> blockingDependencies(TaskRecord task) {
		List<String> blocked = new ArrayList<>();
		for (String depId : task.getBlockedBy()) {
			if (!store.exists(depId)) {
				blocked.add(depId);
				continue;
			}
			TaskRecord dep = store.load(depId);
			if (!COMPLETED.equals(dep.getStatus())) {
				blocked.add(depId);
			}
		}
		return blocked;
	}

	private List<String> cleanBlockedBy(List<String> blockedBy) {
		List<String> cleaned = new ArrayList<>();
		if (blockedBy == null) {
			return cleaned;
		}
		for (String depId : blockedBy) {
			if (depId != null && !depId.isBlank()) {
				cleaned.add(depId.trim());
			}
		}
		return cleaned;
	}
}
