package org.luckyraven.gangland.turf.config;

import lombok.Getter;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;

/**
 * Turf drawing rules read from {@code turf/turf_rules.yml} (0.17.0). {@code Create.Snap_To_Chunks} snaps a new turf
 * outward to whole chunks; {@code Create.Min_Side_Blocks} and {@code Create.Max_Side_Blocks} bound each side of a new
 * turf after snapping. Existing turfs are never changed by these keys.
 */
public class TurfRules implements FileInitializer {

	static final int DEFAULT_MIN_SIDE_BLOCKS = 16;
	static final int DEFAULT_MAX_SIDE_BLOCKS = 512;

	private static final String FILE_NAME        = "turf_rules";
	private static final String SNAP_KEY         = "Create.Snap_To_Chunks";
	private static final String MIN_SIDE_KEY     = "Create.Min_Side_Blocks";
	private static final String MAX_SIDE_KEY     = "Create.Max_Side_Blocks";

	private final FileHandler fileHandler;

	@Getter
	private boolean snapToChunks;
	@Getter
	private int     minSideBlocks;
	@Getter
	private int     maxSideBlocks;

	public TurfRules(FileManager fileManager) {
		this.fileHandler = fileManager.getFile(FILE_NAME);
		initialize();
	}

	@Override
	public void initialize() {
		var config = fileHandler.getFileConfiguration();
		snapToChunks  = config.getBoolean(SNAP_KEY, true);
		minSideBlocks = Math.max(1, config.getInt(MIN_SIDE_KEY, DEFAULT_MIN_SIDE_BLOCKS));
		maxSideBlocks = Math.max(minSideBlocks, config.getInt(MAX_SIDE_KEY, DEFAULT_MAX_SIDE_BLOCKS));
	}

	@Override
	public FileHandler getFileHandler() {
		return fileHandler;
	}

}
