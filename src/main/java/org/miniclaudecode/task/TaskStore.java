package org.miniclaudecode.task;

import com.alibaba.fastjson.JSON;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * `.tasks/` 文件仓库。
 *
 * 这里仅负责 JSON 文件读写，任务状态流转放在 TaskService 中，避免存储层知道业务规则。
 */
public class TaskStore {

	private final File tasksDir;

	public TaskStore(File workdir) {
		this.tasksDir = new File(workdir, ".tasks");
	}

	public TaskRecord save(TaskRecord task) {
		ensureTasksDir();
		File file = taskFile(task.getId());
		try {
			Files.writeString(file.toPath(), JSON.toJSONString(task, true), StandardCharsets.UTF_8);
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to write task: " + file, e);
		}
		return task;
	}

	public TaskRecord load(String taskId) {
		File file = taskFile(taskId);
		if (!file.isFile()) {
			throw new IllegalArgumentException("Task " + taskId + " not found");
		}
		try {
			return JSON.parseObject(Files.readString(file.toPath(), StandardCharsets.UTF_8), TaskRecord.class);
		}
		catch (IOException e) {
			throw new IllegalStateException("Failed to read task: " + file, e);
		}
	}

	public boolean exists(String taskId) {
		if (!isSafeTaskId(taskId)) {
			return false;
		}
		return taskFile(taskId).isFile();
	}

	public List<TaskRecord> list() {
		ensureTasksDir();
		File[] files = tasksDir.listFiles(file -> file.isFile()
				&& file.getName().startsWith("task_")
				&& file.getName().endsWith(".json"));
		List<TaskRecord> tasks = new ArrayList<>();
		if (files == null) {
			return tasks;
		}
		Arrays.sort(files);
		for (File file : files) {
			try {
				tasks.add(JSON.parseObject(Files.readString(file.toPath(), StandardCharsets.UTF_8),
						TaskRecord.class));
			}
			catch (RuntimeException | IOException e) {
				System.out.println("Skip task " + file.getName() + ": " + e.getMessage());
			}
		}
		return tasks;
	}

	public String nextId() {
		return String.format("task_%d_%04d", System.currentTimeMillis(),
				ThreadLocalRandom.current().nextInt(10000));
	}

	private File taskFile(String taskId) {
		if (!isSafeTaskId(taskId)) {
			throw new IllegalArgumentException("Invalid task id: " + taskId);
		}
		return new File(tasksDir, taskId + ".json");
	}

	private boolean isSafeTaskId(String taskId) {
		return taskId != null && taskId.matches("[A-Za-z0-9_-]+");
	}

	private void ensureTasksDir() {
		if (!tasksDir.exists() && !tasksDir.mkdirs()) {
			throw new IllegalStateException("Failed to create directory: " + tasksDir);
		}
	}
}
