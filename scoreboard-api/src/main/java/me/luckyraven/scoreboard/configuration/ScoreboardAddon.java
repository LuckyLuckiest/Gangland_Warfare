package me.luckyraven.scoreboard.configuration;

import lombok.Getter;
import me.luckyraven.exception.PluginException;
import me.luckyraven.file.FileManager;
import me.luckyraven.scoreboard.part.Line;
import me.luckyraven.scoreboard.part.StaticLine;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ScoreboardAddon {

	private final FileConfiguration scoreboard;

	private final @Getter List<Line> lines;
	private final @Getter Line       title;

	public ScoreboardAddon(FileManager fileManager) {
		this.lines = new ArrayList<>();

		try {
			fileManager.checkFileLoaded("scoreboard");
			this.scoreboard = Objects.requireNonNull(fileManager.getFile("scoreboard")).getFileConfiguration();
		} catch (IOException exception) {
			throw new PluginException(exception);
		}

		// initializing the title
		List<String> lines    = getLines("Title");
		long         interval = scoreboard.getLong("Board.Title.Interval");

		if (lines.size() == 1) title = new StaticLine();
		else title = new Line(interval);
		title.addAllContents(lines);

		// initializing the rows
		initializeRows();
	}

	private List<String> getLines(String section) {
		return Objects.requireNonNull(scoreboard.getConfigurationSection("Board." + section)).getStringList("Lines");
	}

	private void initializeRows() {
		ConfigurationSection section;
		int                  index = 0;

		do {
			int row = index + 1;
			section = scoreboard.getConfigurationSection("Board.Rows." + row);
			if (section != null) {
				List<String> lines    = getLines("Rows." + row);
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