package me.luckyraven.bukkit.scoreboard;

import me.luckyraven.Gangland;
import me.luckyraven.bukkit.scoreboard.driver.DriverHandler;
import me.luckyraven.bukkit.scoreboard.driver.sub.DriverV1;
import me.luckyraven.bukkit.scoreboard.driver.sub.DriverV2;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.entity.Player;

public class ScoreboardManager {

	private final Gangland gangland;

	public ScoreboardManager(Gangland gangland) {
		this.gangland = gangland;
	}

	public DriverHandler getDriverHandler(Player player) {
		return switch (SettingAddon.getScoreboardDriver().toLowerCase()) {
			case "driver_v2" -> new DriverV2(gangland, player);
			default -> new DriverV1(gangland, player);
		};
	}

}
