package me.luckyraven.scoreboard;

import com.viaversion.viaversion.api.ViaAPI;
import me.luckyraven.Gangland;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.scoreboard.configuration.ScoreboardAddon;
import me.luckyraven.scoreboard.driver.DriverHandler;
import me.luckyraven.scoreboard.driver.version.DriverV1;
import me.luckyraven.scoreboard.driver.version.DriverV2;
import me.luckyraven.scoreboard.driver.version.DriverV3;
import me.luckyraven.scoreboard.part.Line;
import me.luckyraven.util.Placeholder;
import me.luckyraven.util.utilities.ReflectionUtil;
import org.bukkit.entity.Player;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public class ScoreboardManager {

	private static final List<String> DRIVERS = new ArrayList<>();

	static {
		String        packageName = "me.luckyraven.scoreboard.driver.version";
		Set<Class<?>> classes     = ReflectionUtil.findClasses(packageName, Gangland.class.getClassLoader());

		for (Class<?> clazz : classes) {
			if (!(DriverHandler.class.isAssignableFrom(clazz) && !clazz.isInterface() &&
				  !Modifier.isAbstract(clazz.getModifiers()))) continue;

			@SuppressWarnings("unchecked") // it is known that clazz is a subclass of DriverHandler
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
		final ViaAPI<?> viaAPI          = gangland.getViaAPI();
		ScoreboardAddon scoreboardAddon = gangland.getInitializer().getScoreboardAddon();
		Placeholder     placeholder     = gangland.getInitializer().getPlaceholderService();

		List<Line> lines = scoreboardAddon.getLines();
		Line       title = scoreboardAddon.getTitle();
		return switch (SettingAddon.getScoreboardDriver().toLowerCase()) {
			case "driver_v3" -> new DriverV3(placeholder, viaAPI, player, title, lines);
			case "driver_v2" -> new DriverV2(placeholder, viaAPI, player, title, lines);
			default -> new DriverV1(placeholder, viaAPI, player, title, lines);
		};
	}

}
