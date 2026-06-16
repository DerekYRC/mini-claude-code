package org.miniclaudecode.tool;

import java.io.File;
import java.io.IOException;

/**
 * 文件工具的最小路径边界。
 *
 * s02 还没有权限系统，但文件读写至少要被限制在 workdir 内，
 * 避免模型通过相对路径访问项目目录之外的文件。
 */
public class PathGuard {

	private final File workdir;

	public PathGuard(File workdir) {
		this.workdir = workdir;
	}

	public File resolve(String path) throws IOException {
		File target = new File(workdir, path).getCanonicalFile();
		File root = workdir.getCanonicalFile();
		if (!target.toPath().startsWith(root.toPath())) {
			throw new IOException("Path escapes workspace: " + path);
		}
		return target;
	}
}
