package me.luckyraven.feature.wanted;

import me.luckyraven.copsncrooks.wanted.Wanted;
import me.luckyraven.copsncrooks.wanted.WantedEvent;
import me.luckyraven.data.economy.EconomyException;
import me.luckyraven.data.economy.EconomyHandler;
import me.luckyraven.data.user.User;
import me.luckyraven.feature.Executor;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.timer.Timer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

public class WantedExecutor extends Executor {

	private final WantedEvent                   event;
	private final User<? extends OfflinePlayer> user;

	public WantedExecutor(JavaPlugin plugin, WantedEvent wantedEvent, User<? extends OfflinePlayer> user) {
		super(plugin, "wanted");

		this.event = wantedEvent;
		this.user  = user;
	}

	@Override
	public Timer createTimer() {
		Wanted wanted = user.getWanted();

		double pow = 1D;
		if (SettingAddon.isWantedTimerMultiplierEnabled()) {
			pow = Math.pow(SettingAddon.getWantedTimerMultiplierAmount(), wanted.getLevel());
		}

		double time     = SettingAddon.getWantedTimerTime() * pow;
		long   interval = (long) time;

		return wanted.createTimer(getPlugin(), interval, this::execute);
	}

	@Override
	protected void execute(Timer timer) {
		Wanted wanted = user.getWanted();

		if (isWanted(timer, wanted)) return;

		double takeAmount = SettingAddon.getWantedTakeMoneyAmount();

		double moneyTaken = 0;

		if (takeAmount > 0) {
			moneyTaken = takeAmount * Math.pow(SettingAddon.getWantedTakeMoneyMultiplier(), wanted.getLevel());
		}

		Bukkit.getPluginManager().callEvent(event);

		if (event.isCancelled()) return;

		EconomyHandler economy = user.getEconomy();

		if (moneyTaken != 0) {
			try {
				economy.withdraw(moneyTaken);
			} catch (EconomyException exception) {
				// take the rest of the money
				moneyTaken = economy.getBalance();
				economy.withdraw(moneyTaken);
			}
		}

		wanted.decrementLevel();

		Player player = user.getUser().getPlayer();

		if (player == null) return;

		String string = MessageAddon.WANTED_DECREASED.toString();
		String replace = string.replace("%level%", String.valueOf(wanted.getLevel()))
							   .replace("%stars%", wanted.getLevelStars());

		user.sendMessage(replace);

		if (moneyTaken == 0) return;

		String message = "&c&l-" + SettingAddon.getMoneySymbol() + SettingAddon.formatDouble(moneyTaken);
		user.sendMessage(ChatUtil.color(message));

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
