package me.luckyraven.copsncrooks.bounty;

import me.luckyraven.copsncrooks.events.bounty.BountyEvent;
import me.luckyraven.util.feature.Executor;
import me.luckyraven.util.timer.Timer;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

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

		double oldAmount = bounty.getAmount();

		double currentBounty = oldAmount == 0D ? settings.getEachKillValue() / settings.getTimerMultiple() : oldAmount;

		if (oldAmount >= settings.getTimerMax()) {
			timer.stop();
			return;
		}

		double baseIncrease   = currentBounty * settings.getTimerMultiple();
		double scaledIncrease = bounty.calculateLevelScaledBounty(baseIncrease, context.getUserLevel());
		double amount         = currentBounty + (scaledIncrease - currentBounty);

		event.setAmountApplied(amount - currentBounty);

		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) return;

		bounty.setAmount(amount);

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
