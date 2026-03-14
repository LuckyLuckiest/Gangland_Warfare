package me.luckyraven.copsncrooks.wanted;

import me.luckyraven.copsncrooks.events.wanted.WantedEvent;
import me.luckyraven.util.feature.Executor;
import me.luckyraven.util.timer.Timer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Drives the periodic wanted-level decrease cycle for a single player.
 * <p>
 * Configuration is supplied via {@link WantedSettings} and the owning player's data via {@link WantedContext}, keeping
 * this class free of direct {@code gangland-impl} dependencies.
 */
public class WantedExecutor extends Executor {

	private final WantedEvent    event;
	private final WantedContext  context;
	private final WantedSettings settings;

	public WantedExecutor(JavaPlugin plugin, WantedEvent event, WantedContext context,
						  WantedSettings settings) {
		super(plugin, "wanted");

		this.event    = event;
		this.context  = context;
		this.settings = settings;
	}

	@Override
	public Timer createTimer() {
		Wanted wanted = context.getWanted();

		double pow = 1D;
		if (settings.isTimerMultiplierEnabled()) {
			pow = Math.pow(settings.getTimerMultiplierAmount(), wanted.getLevel());
		}

		double time     = settings.getTimerTime() * pow;
		long   interval = (long) time;

		return wanted.createTimer(interval, this::execute);
	}

	@Override
	protected void execute(Timer timer) {
		Wanted wanted = context.getWanted();

		if (isWanted(timer, wanted)) return;

		double takeAmount = settings.getTakeMoneyAmount();
		double moneyTaken = 0;

		if (takeAmount > 0) {
			moneyTaken = takeAmount * Math.pow(settings.getTakeMoneyMultiplier(), wanted.getLevel());
		}

		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) return;

		if (moneyTaken != 0) {
			moneyTaken = context.withdraw(moneyTaken);
		}

		wanted.decrementLevel();

		String message = settings.getWantedDecreasedMessageTemplate()
								 .replace("%level%", String.valueOf(wanted.getLevel()))
								 .replace("%stars%", wanted.getLevelStars());

		context.sendMessage(message);

		if (moneyTaken != 0) {
			context.sendMessage(settings.formatMoneyLoss(moneyTaken));
		}

		isWanted(timer, wanted);
	}

	private boolean isWanted(Timer timer, Wanted wanted) {
		if (!wanted.isWanted()) {
			timer.stop();
			return true;
		}
		return false;
	}

}
