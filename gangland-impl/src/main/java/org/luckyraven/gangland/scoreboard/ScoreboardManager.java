package org.luckyraven.gangland.scoreboard;

import com.viaversion.viaversion.api.ViaAPI;
import org.bukkit.entity.Player;
import org.luckyraven.gangland.Gangland;
import org.luckyraven.keystone.util.Placeholder;
import org.luckyraven.keystone.util.ReflectionUtil;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.scoreboard.configuration.ScoreboardAddon;
import org.luckyraven.gangland.scoreboard.driver.DriverHandler;
import org.luckyraven.gangland.scoreboard.driver.version.DriverV1;
import org.luckyraven.gangland.scoreboard.driver.version.DriverV2;
import org.luckyraven.gangland.scoreboard.driver.version.DriverV3;
import org.luckyraven.gangland.scoreboard.part.Line;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ScoreboardManager {

	private static final List<String> DRIVERS = new ArrayList<>();

	static {
		String        packageName = "org.luckyraven.gangland.scoreboard.driver.version";
		Set<Class<?>> classes     = ReflectionUtil.findClasses(packageName, Gangland.class.getClassLoader());

		for (Class<?> clazz : classes) {
			if (!(DriverHandler.class.isAssignableFrom(clazz) && !clazz.isInterface() &&
			      !Modifier.isAbstract(clazz.getModifiers()))) continue;

			@SuppressWarnings("unchecked") // it is known that clazz is a subclass of DriverHandler
			Class<? extends DriverHandler> driverClass = (Class<? extends DriverHandler>) clazz;
			DRIVERS.add(driverClass.getSimpleName());
		}
	}

	private final Gangland    gangland;
	private final Placeholder placeholder;

	private final ScoreboardAddon scoreboardAddon;

	public ScoreboardManager(Gangland gangland, Placeholder placeholder, ScoreboardAddon scoreboardAddon) {
		this.gangland        = gangland;
		this.placeholder     = placeholder;
		this.scoreboardAddon = scoreboardAddon;
	}

	public static List<String> getDrivers() {
		return Collections.unmodifiableList(DRIVERS);
	}

	public DriverHandler getDriverHandler(Player player) {
		final ViaAPI<?> viaAPI = gangland.getViaAPI();

		List<Line> lines = scoreboardAddon.getLines();
		Line       title = scoreboardAddon.getTitle();
		return switch (Settings.getScoreboardDriver().toLowerCase()) {
			case "driver_v3" -> new DriverV3(placeholder, viaAPI, player, title, lines);
			case "driver_v2" -> new DriverV2(placeholder, viaAPI, player, title, lines);
			default -> new DriverV1(placeholder, viaAPI, player, title, lines);
		};
	}

}
