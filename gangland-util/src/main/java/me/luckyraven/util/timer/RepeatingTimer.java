package me.luckyraven.util.timer;

import org.bukkit.plugin.java.JavaPlugin;

import java.util.function.Consumer;

public class RepeatingTimer extends Timer {

	private final Consumer<RepeatingTimer> task;
	private       boolean                  justStarted;

	public RepeatingTimer(JavaPlugin plugin, long period, Consumer<RepeatingTimer> task) {
		this(plugin, 0L, period, task);
	}

	public RepeatingTimer(JavaPlugin plugin, long delay, long period, Consumer<RepeatingTimer> task) {
		super(plugin, delay, period);
		this.task        = task;
		this.justStarted = true;
	}

	@Override
	public void run() {
		if (!isRunning()) {
			stop();
			return;
		}

		if (!justStarted) {
			task.accept(this);
		}

		if (justStarted) {
			justStarted = false;
		}
	}

}
