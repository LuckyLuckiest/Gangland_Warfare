package org.luckyraven.gangland.turf.config;

import lombok.Getter;
import org.luckyraven.keystone.persistence.FileHandler;
import org.luckyraven.keystone.persistence.FileInitializer;
import org.luckyraven.keystone.persistence.FileManager;

/**
 * The {@code /glw map} grid and turf HUD knobs read from {@code turf/turf_map.yml} (0.17.0). Every getter applies the
 * clamps the file documents: the grid size is odd and bounded, the cell size is one of 8, 16, 32 or 64, and the
 * intervals and priority stay in range.
 */
public class TurfMapSettings implements FileInitializer {

	private static final String FILE_NAME = "turf_map";

	private final FileHandler fileHandler;

	@Getter
	private int     width;
	@Getter
	private int     height;
	@Getter
	private int     cellBlocks;
	@Getter
	private int     autoMinIntervalSeconds;
	@Getter
	private boolean bedrockTextForm;
	@Getter
	private boolean hudEnabled;
	@Getter
	private int     hudIntervalSeconds;
	@Getter
	private int     hudPriority;

	public TurfMapSettings(FileManager fileManager) {
		this.fileHandler = fileManager.getFile(FILE_NAME);
		initialize();
	}

	@Override
	public void initialize() {
		var config = fileHandler.getFileConfiguration();

		int w = config.getInt("Map.Width", 41);
		if (w % 2 == 0) {
			w++;
		}
		width = Math.min(61, Math.max(11, w));

		int h = config.getInt("Map.Height", 7);
		if (h % 2 == 0) {
			h++;
		}
		height = Math.min(15, Math.max(3, h));

		int cell = config.getInt("Map.Cell_Blocks", 16);
		cellBlocks = switch (cell) {
			case 8, 16, 32, 64 -> cell;
			default -> 16;
		};

		autoMinIntervalSeconds = Math.max(1, config.getInt("Map.Auto_Min_Interval_Seconds", 5));
		bedrockTextForm        = config.getBoolean("Map.Bedrock_Text_Form", true);

		hudEnabled         = config.getBoolean("Hud.Enabled", true);
		hudIntervalSeconds = Math.max(1, config.getInt("Hud.Interval_Seconds", 2));
		hudPriority        = Math.min(100, Math.max(0, config.getInt("Hud.Priority", 9)));
	}

	@Override
	public FileHandler getFileHandler() {
		return fileHandler;
	}

}
