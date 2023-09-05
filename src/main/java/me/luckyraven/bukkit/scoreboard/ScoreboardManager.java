package me.luckyraven.bukkit.scoreboard;

import fr.mrmicky.fastboard.FastBoard;
import me.luckyraven.Gangland;
import me.luckyraven.bukkit.scoreboard.driver.DriverHandler;
import me.luckyraven.bukkit.scoreboard.driver.sub.DriverV1;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.entity.Player;

public class ScoreboardManager {

	private final Gangland gangland;

	public ScoreboardManager(Gangland gangland) {
		this.gangland = gangland;
	}

	public DriverHandler getDriverHandler(Player player) {
		FastBoard fastBoard = new FastBoard(player);

		return switch (SettingAddon.getScoreboardDriver()) {
			default -> new DriverV1(gangland, fastBoard);
		};
	}

}
