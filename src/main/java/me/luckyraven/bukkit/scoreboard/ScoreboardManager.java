package me.luckyraven.bukkit.scoreboard;

import fr.mrmicky.fastboard.FastBoard;
import lombok.Getter;
import me.luckyraven.Gangland;
import me.luckyraven.bukkit.scoreboard.driver.DriverHandler;
import me.luckyraven.bukkit.scoreboard.driver.sub.DriverV1;
import me.luckyraven.bukkit.scoreboard.part.Line;
import me.luckyraven.bukkit.scoreboard.part.StaticLine;
import me.luckyraven.exception.PluginException;
import me.luckyraven.file.FileManager;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class ScoreboardManager {

	private final         Gangland          gangland;
	private final         FileConfiguration scoreboard;
	private final @Getter List<Line>        lines;

	private @Getter Line title;
	private         int  index;

	public ScoreboardManager(Gangland gangland) {
		this.gangland = gangland;

		FileManager fileManager = gangland.getInitializer().getFileManager();

		try {
			fileManager.checkFileLoaded("scoreboard");
			this.scoreboard = Objects.requireNonNull(fileManager.getFile("scoreboard")).getFileConfiguration();
			this.lines = new ArrayList<>();

			initialize();
			this.index = 0;
		} catch (IOException exception) {
			throw new PluginException(exception);
		}
	}

	public DriverHandler getDriverHandler(Player player) {
		FastBoard fastBoard = new FastBoard(player);

		return switch (SettingAddon.getScoreboardDriver()) {
			default -> new DriverV1(gangland, fastBoard);
		};
	}

	private void initialize() {
		initializeTitle();
		initializeRows();
	}

	private void initializeTitle() {
		List<String> lines = Objects.requireNonNull(scoreboard.getConfigurationSection("Board.Title")).getStringList(
				"Lines");
		long interval = scoreboard.getLong("Board.Title.Interval");

		if (lines.size() == 1) this.title = new StaticLine();
		else this.title = new Line(interval);
		title.addAllContents(lines);
	}

	private void initializeRows() {
		ConfigurationSection section;

		do {
			int row = index + 1;
			section = scoreboard.getConfigurationSection("Board.Rows." + row);
			if (section != null) {
				List<String> lines = Objects.requireNonNull(scoreboard.getConfigurationSection("Board.Rows." + row))
				                            .getStringList("Lines");
				long interval = scoreboard.getLong("Board.Rows." + row + ".Interval");

				Line line;
				if (lines.size() == 1) line = new StaticLine(index++);
				else line = new Line(interval, index++);

				line.addAllContents(lines);

				this.lines.add(line);
			}
		}
		while (section != null);
	}

}
