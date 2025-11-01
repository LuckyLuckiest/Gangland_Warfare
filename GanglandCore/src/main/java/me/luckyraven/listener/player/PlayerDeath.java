package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.data.placeholder.PlaceholderHandler;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.datastructure.ScientificCalculator;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.listener.ListenerHandler;
import me.luckyraven.util.ChatUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.HashMap;
import java.util.Map;

@ListenerHandler
public class PlayerDeath implements Listener {

	private final Gangland            gangland;
	private final UserManager<Player> userManager;

	public PlayerDeath(Gangland gangland) {
		this.gangland    = gangland;
		this.userManager = gangland.getInitializer().getUserManager();
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerDeath(PlayerDeathEvent event) {
		Player       player = event.getEntity();
		User<Player> user   = userManager.getUser(player);

		// when a player dies, death counter increases
		user.setDeaths(user.getDeaths() + 1);

		// punish the player if they die
		if (user.getEconomy().getBalance() <= SettingAddon.getDeathThreshold()) return;

		if (SettingAddon.isDeathMoneyCommandEnabled()) {
			for (String executable : SettingAddon.getDeathMoneyCommandExecutables()) {
				PlaceholderHandler placeholder = gangland.getInitializer().getPlaceholder();

				String exec = placeholder.replacePlaceholder(player, executable.replace("/", ""));
				Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), exec);
			}
		} else {
			// take money from their balance (NOT THEIR BANK)
			double deduct = amountDeduction(user);
			String type;

			if (SettingAddon.isDeathLoseMoney()) {
				type = "&c-";
				user.getEconomy().withdraw(deduct);
			} else {
				type = "&a+";
				user.getEconomy().deposit(deduct);
			}

			// inform the player
			player.sendMessage(ChatUtil.color(type + deduct));
		}
	}

	private double amountDeduction(User<Player> user) {
		Map<String, Double> variables = new HashMap<>();

		variables.put("balance", user.getEconomy().getBalance());
		variables.put("level", (double) user.getLevel().getLevelValue());
		variables.put("experience", user.getLevel().getExperience());
		variables.put("bounty", user.getBounty().getAmount());
		variables.put("wanted", (double) user.getWanted().getLevel());

		String formula = SettingAddon.getDeathLoseMoneyFormula();

		ScientificCalculator calculator = new ScientificCalculator(formula, variables);

		return calculator.evaluate();
	}

}
