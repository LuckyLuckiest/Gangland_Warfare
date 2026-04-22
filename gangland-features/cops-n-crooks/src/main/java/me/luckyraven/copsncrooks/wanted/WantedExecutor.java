package me.luckyraven.copsncrooks.wanted;

import me.luckyraven.copsncrooks.events.wanted.WantedEvent;
import me.luckyraven.core.feature.Executor;
import me.luckyraven.core.timer.Timer;
import me.luckyraven.economy.Currency;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;

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

		BigDecimal takeAmount = settings.getTakeMoneyAmount();
		BigDecimal moneyTaken = Currency.ZERO;

		if (takeAmount.signum() > 0) {
			double factor = Math.pow(settings.getTakeMoneyMultiplier(), wanted.getLevel());
			moneyTaken = Currency.multiply(takeAmount, factor);
		}

		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) return;

		if (moneyTaken.signum() != 0) {
			moneyTaken = context.withdraw(moneyTaken);
		}

		// Compute the post-decrement level up-front: Wanted.setLevel reschedules to the
		// primary thread when called off-thread (this executor runs async), so reading
		// wanted.getLevel() right after decrementLevel() would return the stale value.
		int newLevel = Math.max(0, wanted.getLevel() - 1);

		wanted.decrementLevel();

		String message = settings.getWantedDecreasedMessageTemplate()
		                         .replace("%level%", String.valueOf(newLevel))
		                         .replace("%stars%", Wanted.buildStars(newLevel, wanted.getMaxLevel()));

		context.sendMessage(message);

		if (moneyTaken.signum() != 0) {
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
