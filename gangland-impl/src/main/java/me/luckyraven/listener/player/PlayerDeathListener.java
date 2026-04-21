package me.luckyraven.listener.player;

import me.luckyraven.copsncrooks.npc.banker.tier.BankTier;
import me.luckyraven.copsncrooks.npc.banker.tier.BankTierRegistry;
import me.luckyraven.core.autowire.bean.Qualifier;
import me.luckyraven.core.datastructure.ScientificCalculator;
import me.luckyraven.core.downed.PlayerDownedEvent;
import me.luckyraven.core.listener.ListenerHandler;
import me.luckyraven.core.utilities.ChatUtil;
import me.luckyraven.core.utilities.NumberUtil;
import me.luckyraven.data.account.user.User;
import me.luckyraven.data.account.user.UserManager;
import me.luckyraven.data.placeholder.worker.GanglandPlaceholder;
import me.luckyraven.economy.bank.Bank;
import me.luckyraven.economy.bank.Currency;
import me.luckyraven.economy.bank.EconomyHandler;
import me.luckyraven.file.configuration.Messages;
import me.luckyraven.file.configuration.Settings;
import me.luckyraven.weapon.Weapon;
import me.luckyraven.weapon.WeaponManager;
import me.luckyraven.weapon.types.throwable.ThrowableAction;
import net.citizensnpcs.api.CitizensAPI;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@ListenerHandler
public class PlayerDeathListener implements Listener {

	private static final long DEATH_DEDUP_WINDOW_MS = 500L;

	private final UserManager<Player> userManager;
	private final WeaponManager       weaponManager;
	private final GanglandPlaceholder placeholder;
	private final BankTierRegistry    bankTierRegistry;
	private final Map<UUID, Long>     recentDeaths      = new ConcurrentHashMap<>();
	private final Set<UUID>           downedBroadcasted = ConcurrentHashMap.newKeySet();

	public PlayerDeathListener(@Qualifier("online") UserManager<Player> userManager,
	                           WeaponManager weaponManager,
	                           GanglandPlaceholder placeholder,
	                           BankTierRegistry bankTierRegistry) {
		this.userManager      = userManager;
		this.weaponManager    = weaponManager;
		this.placeholder      = placeholder;
		this.bankTierRegistry = bankTierRegistry;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerDeath(PlayerDeathEvent event) {
		if (CitizensAPI.getNPCRegistry().isNPC(event.getEntity())) {
			event.setDeathMessage(null);
			return;
		}

		Player player = event.getEntity();
		UUID   uuid   = player.getUniqueId();
		long   now    = System.currentTimeMillis();

		Long lastDeath = recentDeaths.get(uuid);
		if (lastDeath != null && now - lastDeath < DEATH_DEDUP_WINDOW_MS) {
			// Always suppress: either the downed handler already broadcast, or the FRESH path
			// already ran for this death (e.g. duplicate PlayerDeathEvent from vanilla Player.attack
			// re-checking HP<=0 after MeleeAction's target.damage() killed the entity).
			downedBroadcasted.remove(uuid);
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
		if (economy.getAmount().doubleValue() <= Settings.getDeathThreshold()) return true;

		if (Settings.isDeathMoneyCommandEnabled()) {
			for (String executable : Settings.getDeathMoneyCommandExecutables()) {
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

		// Bank-account insurance: having an account softens the loss by the tier's deathLossDiscount. Applied
		// post-formula so admin-authored Death.Money.Formula expressions don't have to know about the bank.
		double discount = bankInsuranceDiscount(user);
		if (discount > 0) {
			deduct *= Math.max(0D, 1D - discount);
		}
		if (deduct <= 0D) return;

		if (Settings.isDeathLoseMoney()) {
			type = "&c&l-";
			economy.withdrawAmount(Currency.of(deduct));
		} else {
			type = "&a&l+";
			economy.depositAmount(Currency.of(deduct));
		}

		// inform the player
		String info    = type + Settings.getMoneySymbol() + NumberUtil.valueFormat(deduct);
		String message = "&3Death penalty: " + info;

		user.sendMessage(ChatUtil.color(message));
	}

	private double bankInsuranceDiscount(User<Player> user) {
		Bank bank = user.getBank();
		if (bank == null) return 0D;
		BankTier tier = bankTierRegistry.get(bank.getTierId());
		if (tier == null) tier = bankTierRegistry.first();
		return tier == null ? 0D : tier.deathLossDiscount();
	}

	private void changeDeathMessage(PlayerDeathEvent event, Player player) {
		// Citizens NPC killers are not Player instances, so getKiller() returns null and the
		// vanilla "[NpcName] slain" message would show. Suppress it explicitly.
		EntityDamageEvent cause = player.getLastDamageCause();
		if (cause instanceof EntityDamageByEntityEvent byEntity
		    && CitizensAPI.getNPCRegistry().isNPC(byEntity.getDamager())) {
			event.setDeathMessage(null);
			return;
		}

		String message = buildDeathMessage(player);
		if (message == null) return;
		event.setDeathMessage(message);
	}

	private void broadcastDeathMessage(Player player) {
		String message = buildDeathMessage(player);
		if (message == null) return;
		downedBroadcasted.add(player.getUniqueId());
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

		List<String> globalMessages = Messages.DEAD_USING_WEAPON.toStringList();

		// no weapon found — fall back to the global death messages if available
		if (weapon == null) {
			String template = getRandomGlobalMessage(globalMessages);
			if (template == null) return null;
			// use the throwable's name if we at least know which weapon it was
			String itemName = throwableName != null ? throwableName : "";
			return ChatUtil.color(template.replace("%killer%", killer.getName())
			                              .replace("%victim%", player.getName())
			                              .replace("%item%", itemName));
		}

		// prefer weapon-specific death messages; fall back to global config
		String template = weapon.pickDeathMessage().orElseGet(() -> getRandomGlobalMessage(globalMessages));

		if (template == null) return null;

		return ChatUtil.color(template.replace("%killer%", killer.getName())
		                              .replace("%victim%", player.getName())
		                              .replace("%item%", weapon.getDisplayName()));
	}

	private @Nullable String getRandomGlobalMessage(List<String> globalMessages) {
		if (globalMessages.isEmpty()) return null;
		return globalMessages.get(new Random().nextInt(globalMessages.size()));
	}

	private double amountDeduction(User<Player> user) {
		Map<String, Double> variables = new HashMap<>();

		variables.put("balance", user.getEconomy().getAmount().doubleValue());
		variables.put("level", (double) user.getLevel().getLevelValue());
		variables.put("experience", user.getLevel().getExperience());
		variables.put("bounty", user.getBounty().getAmount().doubleValue());
		variables.put("wanted", (double) user.getWanted().getLevel());

		String formula = Settings.getDeathLoseMoneyFormula();

		ScientificCalculator calculator = new ScientificCalculator(formula, variables);

		return calculator.evaluate();
	}

}
