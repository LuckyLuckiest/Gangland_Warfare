package me.luckyraven.util.timer;

import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Consumer;

public class RepeatingTimer extends Timer {

	private final Consumer<RepeatingTimer> task;

	public RepeatingTimer(JavaPlugin plugin, long period, Consumer<RepeatingTimer> task) {
		this(plugin, 0L, period, task);
	}

	public RepeatingTimer(JavaPlugin plugin, long delay, long period, Consumer<RepeatingTimer> task) {
		super(plugin, delay, period);
		this.task = task;
	}

	@Override
	public void run() {
		if (!isRunning()) {
			stop();
			return;
		}

		runTask();
	}

	public void runTask() {
		task.accept(this);
	}

}
