package org.luckyraven.gangland.data.plugin;

/** One unit of scheduled data cleanup. Modules register beans; {@link PluginDataCleanupService} runs them all. */
public interface DataCleanupTask {

	String name();

	/** @return records cleared, for the debug log line */
	int cleanup();
}
