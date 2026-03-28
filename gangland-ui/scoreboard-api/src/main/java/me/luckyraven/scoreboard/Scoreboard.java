package me.luckyraven.scoreboard;

import me.luckyraven.scoreboard.driver.DriverHandler;
import me.luckyraven.util.placeholder.effect.FlashPlaceholderWrapper;
import me.luckyraven.util.timer.RepeatingTimer;
import org.bukkit.plugin.java.JavaPlugin;

public class Scoreboard {

	private final DriverHandler  driver;
	private final RepeatingTimer timer;

	public Scoreboard(JavaPlugin plugin, DriverHandler driver) {
		this.driver = driver;

		// repeating the task each tick
		this.timer = new RepeatingTimer(plugin, 0L, 1L, time -> {
			FlashPlaceholderWrapper.setCurrentTick(time.getTickCount());

			this.driver.update();
		});
	}

	public void start() {
		if (timer == null) return;

		timer.start(true);
	}

	public void end() {
		if (timer == null) return;

		timer.stop();
		driver.getFastBoard().delete();
	}

	@Override
	public String toString() {
		return String.format("Scoreboard{driver=%s,timer=%s}", driver, timer);
	}

}
