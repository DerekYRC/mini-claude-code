package org.miniclaudecode.tool;

import java.io.File;
import java.io.IOException;

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
