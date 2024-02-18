package me.luckyraven.bukkit.scoreboard;

import me.luckyraven.Gangland;
import me.luckyraven.bukkit.scoreboard.driver.DriverHandler;
import me.luckyraven.bukkit.scoreboard.driver.sub.DriverV1;
import me.luckyraven.bukkit.scoreboard.driver.sub.DriverV2;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ReflectionUtil;
import org.bukkit.entity.Player;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ScoreboardManager {

	private static final List<String> DRIVERS = new ArrayList<>();

	static {
		List<Class<?>> classes = new ArrayList<>(
				ReflectionUtil.getAllClasses("me.luckyraven.bukkit.scoreboard.driver.sub"));

		Gangland.getLog4jLogger().info(classes);

		for (Class<?> clazz : classes) {
			if (!(DriverHandler.class.isAssignableFrom(clazz) && !clazz.isInterface() &&
				  !Modifier.isAbstract(clazz.getModifiers()))) continue;

			@SuppressWarnings("unchecked") // We know clazz is a subclass of DriverHandler
			Class<? extends DriverHandler> driverClass = (Class<? extends DriverHandler>) clazz;
			DRIVERS.add(driverClass.getSimpleName());
		}
	}

	private final Gangland gangland;

	public ScoreboardManager(Gangland gangland) {
		this.gangland = gangland;
	}

	public static List<String> getDrivers() {
		return Collections.unmodifiableList(DRIVERS);
	}

	public DriverHandler getDriverHandler(Player player) {
		return switch (SettingAddon.getScoreboardDriver().toLowerCase()) {
			case "driver_v2" -> new DriverV2(gangland, player);
			default -> new DriverV1(gangland, player);
		};
	}

}
