package me.luckyraven.listener.player;

import me.luckyraven.Gangland;
import me.luckyraven.Initializer;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.economy.EconomyHandler;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.util.datastructure.ScientificCalculator;
import me.luckyraven.util.downed.PlayerDownedEvent;
import me.luckyraven.util.listener.ListenerHandler;
import me.luckyraven.util.placeholder.PlaceholderHandler;
import me.luckyraven.util.utilities.ChatUtil;
import me.luckyraven.util.utilities.NumberUtil;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponManager;
import me.luckyraven.weapon.types.throwable.ThrowableAction;
import net.citizensnpcs.api.CitizensAPI;
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
		if (CitizensAPI.getNPCRegistry().isNPC(event.getEntity())) return;

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

		// punish the player if they die; if commands ran, skip direct money deduction
		if (!handleCommandExecution(user, player)) {
			// take money from their balance (NOT THEIR BANK)
			handleMoney(user);
		}

		// change the death message according to the weapon (always runs)
		changeDeathMessage(event, player);
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerDowned(PlayerDownedEvent event) {
		Player player = event.getPlayer();
		UUID   uuid   = player.getUniqueId();
		long   now    = System.currentTimeMillis();

		Long lastDeath = recentDeaths.get(uuid);
		if (lastDeath != null && now - lastDeath < DEATH_DEDUP_WINDOW_MS) {
			return;
		}
		recentDeaths.put(uuid, now);

		User<Player> user = userManager.getUser(player);

		if (user == null) return;

		user.setDeaths(user.getDeaths() + 1);

		if (!handleCommandExecution(user, player)) {
			handleMoney(user);
		}

		broadcastDeathMessage(player);
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

	private void handleMoney(User<Player> user) {
		EconomyHandler economy = user.getEconomy();
		double         deduct  = amountDeduction(user);

		String type;

		// ignore it if there was no money to be deducted
		if (deduct == 0) return;

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
	}

	private void changeDeathMessage(PlayerDeathEvent event, Player player) {
		String message = buildDeathMessage(player);
		if (message == null) return;
		event.setDeathMessage(message);
	}

	private void broadcastDeathMessage(Player player) {
		String message = buildDeathMessage(player);
		if (message == null) return;
		Bukkit.broadcastMessage(message);
	}

	private String buildDeathMessage(Player player) {
		Player killer = player.getKiller();

		if (killer == null) return null;

		// check if a throwable weapon was responsible (killer may have switched items since throwing)
		String throwableName = ThrowableAction.pendingKillerWeapon.remove(player.getUniqueId());

		Weapon weapon;
		if (throwableName != null) {
			weapon = weaponManager.getWeapon(throwableName);
		} else {
			ItemStack heldItem = killer.getInventory().getItemInMainHand();
			weapon = weaponManager.validateAndGetWeapon(killer, heldItem);
		}

		if (weapon == null) return null;

		// prefer weapon-specific death messages; fall back to global config
		String template = weapon.pickDeathMessage().orElseGet(() -> {
			List<String> messages = Messages.DEAD_USING_WEAPON.toStringList();
			return messages.get(new Random().nextInt(messages.size()));
		});

		return ChatUtil.color(template.replace("%killer%", killer.getName())
		                              .replace("%victim%", player.getName())
		                              .replace("%item%", weapon.getName()));
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
