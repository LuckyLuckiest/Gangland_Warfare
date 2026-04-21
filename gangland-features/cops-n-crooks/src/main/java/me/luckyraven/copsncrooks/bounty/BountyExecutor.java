package me.luckyraven.copsncrooks.bounty;

import me.luckyraven.copsncrooks.events.bounty.BountyEvent;
import me.luckyraven.economy.bank.Currency;
import me.luckyraven.util.feature.Executor;
import me.luckyraven.util.timer.Timer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.math.BigDecimal;

/**
 * Drives the periodic bounty-increase cycle for a single entity.
 * <p>
 * Configuration is supplied via {@link BountySettings} and the owning entity's data via {@link BountyContext}, keeping
 * this class free of direct {@code gangland-impl} dependencies.
 */
public class BountyExecutor extends Executor {

	private final BountyEvent    event;
	private final BountyContext  context;
	private final BountySettings settings;

	public BountyExecutor(JavaPlugin plugin, BountyEvent event, BountyContext context, BountySettings settings) {
		super(plugin, "bounty");

		this.event    = event;
		this.context  = context;
		this.settings = settings;
	}

	@Override
	public Timer createTimer() {
		Bounty bounty   = context.getBounty();
		int    interval = settings.getTimeInterval();

		return bounty.createTimer(getPlugin(), interval, this::execute);
	}

	@Override
	public void execute(Timer timer) {
		Bounty bounty = context.getBounty();

		if (hasBounty(timer, bounty)) return;

		BigDecimal oldAmount = bounty.getAmount();

		BigDecimal currentBounty;
		if (oldAmount.signum() == 0) {
			double multiple = settings.getTimerMultiple();
			currentBounty = settings.getEachKillValue()
			                        .divide(BigDecimal.valueOf(multiple), Currency.SCALE, Currency.ROUNDING_MODE);
		} else {
			currentBounty = oldAmount;
		}

		if (oldAmount.compareTo(BigDecimal.valueOf(settings.getTimerMax())) >= 0) {
			timer.stop();
			return;
		}

		BigDecimal baseIncrease   = Currency.multiply(currentBounty, settings.getTimerMultiple());
		BigDecimal scaledIncrease = bounty.calculateLevelScaledBounty(baseIncrease, context.getUserLevel());

		event.setAmountApplied(scaledIncrease.subtract(currentBounty));

		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) return;

		bounty.setAmount(scaledIncrease);

		hasBounty(timer, bounty);
	}

	private boolean hasBounty(Timer timer, Bounty bounty) {
		if (!bounty.hasBounty()) {
			timer.stop();
			return true;
		}
		return false;
	}

}
