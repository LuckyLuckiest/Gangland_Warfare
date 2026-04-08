package me.luckyraven.scoreboard.configuration;

import lombok.Getter;
import me.luckyraven.exception.PluginException;
import me.luckyraven.persistence.FileHandler;
import me.luckyraven.persistence.FileInitializer;
import me.luckyraven.persistence.FileManager;
import me.luckyraven.scoreboard.part.Line;
import me.luckyraven.scoreboard.part.StaticLine;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ScoreboardAddon implements FileInitializer {

	private final FileHandler fileHandler;

	private final @Getter List<Line> lines;
	private @Getter       Line       title;

	public ScoreboardAddon(FileManager fileManager) {
		this.lines = new ArrayList<>();

		try {
			fileManager.checkFileLoaded("scoreboard");
			this.fileHandler = Objects.requireNonNull(fileManager.getFile("scoreboard"));
		} catch (IOException exception) {
			throw new PluginException(exception);
		}
	}

	@Override
	public FileHandler getFileHandler() {
		return fileHandler;
	}

	@Override
	public void initialize() {
		FileConfiguration scoreboard = fileHandler.getFileConfiguration();

		this.lines.clear();
		this.title = null;

		// initializing the title
		List<String> titleLines = getLines(scoreboard, "Title");
		long         interval   = scoreboard.getLong("Board.Title.Interval");

		if (titleLines.size() == 1) this.title = new StaticLine();
		else this.title = new Line(interval);
		this.title.addAllContents(titleLines);

		// initializing the rows
		initializeRows(scoreboard);
	}

	private List<String> getLines(FileConfiguration scoreboard, String section) {
		return Objects.requireNonNull(scoreboard.getConfigurationSection("Board." + section)).getStringList("Lines");
	}

	private void initializeRows(FileConfiguration scoreboard) {
		ConfigurationSection section;
		int                  index = 0;

		do {
			int row = index + 1;
			section = scoreboard.getConfigurationSection("Board.Rows." + row);
			if (section != null) {
				List<String> lines    = getLines(scoreboard, "Rows." + row);
				long         interval = section.getLong("Interval");

				Line line;
				if (interval == 0L) line = new StaticLine(index++);
				else line = new Line(interval, index++);

				line.addAllContents(lines);

				this.lines.add(line);
			}
		} while (section != null);
	}

}