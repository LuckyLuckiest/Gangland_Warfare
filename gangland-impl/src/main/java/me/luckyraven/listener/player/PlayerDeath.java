package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.economy.EconomyHandler;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
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

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ListenerHandler
public class PlayerDeath implements Listener {

	private static final long DEATH_DEDUP_WINDOW_MS = 500L;

	private final Initializer         initializer;
	private final UserManager<Player> userManager;
	private final WeaponManager       weaponManager;
	private final Map<UUID, Long>     recentDeaths = new ConcurrentHashMap<>();

	public PlayerDeath(Gangland gangland) {
		this.initializer   = gangland.getInitializer();
		this.userManager   = initializer.getUserManager();
		this.weaponManager = initializer.getWeaponManager();
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerDeath(PlayerDeathEvent event) {
		Player player = event.getEntity();
		UUID   uuid   = player.getUniqueId();
		long   now    = System.currentTimeMillis();

		Long lastDeath = recentDeaths.get(uuid);
		if (lastDeath != null && now - lastDeath < DEATH_DEDUP_WINDOW_MS) {
			event.setDeathMessage(null);
			return;
		}
		recentDeaths.put(uuid, now);

		User<Player> user = userManager.getUser(player);

		if (user == null) return;

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
		if (economy.getBalance() <= Settings.getDeathThreshold()) return true;

		if (Settings.isDeathMoneyCommandEnabled()) {
			for (String executable : Settings.getDeathMoneyCommandExecutables()) {
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

		if (Settings.isDeathLoseMoney()) {
			type = "&c&l-";
			economy.withdraw(deduct);
		} else {
			type = "&a&l+";
			economy.deposit(deduct);
		}

		// inform the player
		String info    = type + Settings.getMoneySymbol() + NumberUtil.valueFormat(deduct);
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

		List<String> messages = Messages.DEAD_USING_WEAPON.toStringList();
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

		String formula = Settings.getDeathLoseMoneyFormula();

		ScientificCalculator calculator = new ScientificCalculator(formula, variables);

		return calculator.evaluate();
	}

}
