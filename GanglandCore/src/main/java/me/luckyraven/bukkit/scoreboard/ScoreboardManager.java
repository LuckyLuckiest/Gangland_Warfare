package me.luckyraven.bukkit.scoreboard;

import me.luckyraven.Gangland;
import me.luckyraven.bukkit.scoreboard.driver.DriverHandler;
import me.luckyraven.bukkit.scoreboard.driver.sub.DriverV1;
import me.luckyraven.bukkit.scoreboard.driver.sub.DriverV2;
import me.luckyraven.file.configuration.SettingAddon;
import org.bukkit.entity.Player;
import org.reflections.Reflections;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ScoreboardManager {

	private static final List<String> DRIVERS = new ArrayList<>();

	static {
		String packageName = "me.luckyraven.bukkit.scoreboard.driver.sub";
		Reflections reflections = new Reflections(new ConfigurationBuilder().forPackage(packageName)
																			.filterInputsBy(
																					new FilterBuilder().includePackage(
																							packageName)));
		Set<Class<?>> modules = reflections.getSubTypesOf(Object.class);

		for (Class<?> clazz : modules) {
			if (!(DriverHandler.class.isAssignableFrom(clazz) && !clazz.isInterface() &&
				  !Modifier.isAbstract(clazz.getModifiers()))) continue;

			@SuppressWarnings("unchecked") // it is known that clazz is a subclass of DriverHandler
			Class<? extends DriverHandler> driverClass = (Class<? extends DriverHandler>) clazz;
			DRIVERS.add(driverClass.getSimpleName());
		}

		// TODO make this work!
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
