package me.luckyraven.feature.bounty;

import me.luckyraven.data.user.User;
import me.luckyraven.feature.Executor;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.timer.Timer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.plugin.java.JavaPlugin;

public class BountyExecutor extends Executor {

	private final BountyEvent                   event;
	private final User<? extends OfflinePlayer> user;

	public BountyExecutor(JavaPlugin plugin, BountyEvent event, User<? extends OfflinePlayer> user) {
		super(plugin, "bounty");

		this.event = event;
		this.user  = user;
	}

	@Override
	public Timer createTimer() {
		Bounty bounty   = user.getBounty();
		int    interval = SettingAddon.getBountyTimeInterval();

		return bounty.createTimer(getPlugin(), interval, this::execute);
	}

	@Override
	public void execute(Timer timer) {
		Bounty bounty = user.getBounty();

		if (hasBounty(timer, bounty)) return;

		double oldAmount = bounty.getAmount();

		double currentBounty = oldAmount == 0D ?
							   SettingAddon.getBountyEachKillValue() / SettingAddon.getBountyTimerMultiple() :
							   oldAmount;

		if (oldAmount >= SettingAddon.getBountyTimerMax()) {
			timer.stop();
			return;
		}

		double baseIncrease   = currentBounty * SettingAddon.getBountyTimerMultiple();
		double scaledIncrease = bounty.calculateLevelScaledBounty(baseIncrease, user.getLevel().getLevelValue());
		double amount         = currentBounty + (scaledIncrease - currentBounty);

		event.setAmountApplied(amount - currentBounty);

		// call the event
		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) return;

		// change the value
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
