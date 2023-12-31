package me.luckyraven.bukkit.scoreboard;

import me.luckyraven.bukkit.scoreboard.driver.DriverHandler;
import me.luckyraven.util.timer.RepeatingTimer;

public class Scoreboard {

	private final DriverHandler  driver;
	private final RepeatingTimer timer;

	public Scoreboard(DriverHandler driver) {
		this.driver = driver;

		// repeating the task each tick
		this.timer = new RepeatingTimer(driver.getGangland(), 0L, 1L, time -> this.driver.update());
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
