package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.data.economy.EconomyHandler;
import me.luckyraven.data.user.User;
import me.luckyraven.data.user.UserManager;
import me.luckyraven.file.configuration.MessageAddon;
import me.luckyraven.file.configuration.SettingAddon;
import me.luckyraven.util.ChatUtil;
import me.luckyraven.util.datastructure.ScientificCalculator;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.util.placeholder.PlaceholderHandler;
import me.luckyraven.util.utilities.NumberUtil;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

@ListenerHandler
public class PlayerDeath implements Listener {

	private final Initializer         initializer;
	private final UserManager<Player> userManager;
	private final WeaponManager       weaponManager;

	public PlayerDeath(Gangland gangland) {
		this.initializer   = gangland.getInitializer();
		this.userManager   = initializer.getUserManager();
		this.weaponManager = initializer.getWeaponManager();
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerDeath(PlayerDeathEvent event) {
		Player       player = event.getEntity();
		User<Player> user   = userManager.getUser(player);

		// when a player dies, the death counter increases
		user.setDeaths(user.getDeaths() + 1);

		// punish the player if they die
		if (handleCommandExecution(user, player)) return;

		// take money from their balance (NOT THEIR BANK)
		if (handleMoney(user)) return;

		// change the death message according to the weapon
		changeDeathMessage(event, player);
	}

	private boolean handleCommandExecution(User<Player> user, Player player) {
		EconomyHandler economy = user.getEconomy();
		if (economy.getBalance() <= SettingAddon.getDeathThreshold()) return true;

		if (SettingAddon.isDeathMoneyCommandEnabled()) {
			for (String executable : SettingAddon.getDeathMoneyCommandExecutables()) {
				PlaceholderHandler placeholder = initializer.getPlaceholder();

				String exec = placeholder.replacePlaceholder(player, executable.replace("/", ""));
				Bukkit.getServer().dispatchCommand(Bukkit.getServer().getConsoleSender(), exec);
			}

			return true;
		}
		return false;
	}

	private boolean handleMoney(User<Player> user) {
		EconomyHandler economy = user.getEconomy();
		double         deduct  = amountDeduction(user);

		String type;

		// ignore it if there was no money to be deducted
		if (deduct == 0) return true;

		if (SettingAddon.isDeathLoseMoney()) {
			type = "&c&l-";
			economy.withdraw(deduct);
		} else {
			type = "&a&l+";
			economy.deposit(deduct);
		}

		// inform the player
		String info    = type + SettingAddon.getMoneySymbol() + NumberUtil.valueFormat(deduct);
		String message = "&3Death penalty: " + info;

		user.sendMessage(ChatUtil.color(message));
		return false;
	}

	private void changeDeathMessage(PlayerDeathEvent event, Player player) {
		Player killer = player.getKiller();

		if (killer == null) return;

		ItemStack heldItem = killer.getInventory().getItemInMainHand();
		Weapon    weapon   = weaponManager.validateAndGetWeapon(killer, heldItem);

		if (weapon == null) return;

		List<String> messages = MessageAddon.DEAD_USING_WEAPON.toStringList();
		Random       random   = new Random();

		int    index        = random.nextInt(messages.size());
		String deathMessage = messages.get(index);

		String replace = deathMessage.replace("%killer%", killer.getName())
									 .replace("%victim%", player.getName())
									 .replace("%item%", weapon.getName());

		event.setDeathMessage(replace);
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
