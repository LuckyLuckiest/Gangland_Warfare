package org.luckyraven.gangland.listener.player;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.luckyraven.gangland.core.downed.PlayerUndownedEvent;
import lombok.CustomLog;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.data.economy.BankTierView;
import org.luckyraven.gangland.data.economy.BankTiers;
import org.luckyraven.keystone.bean.Qualifier;
import org.luckyraven.keystone.bean.listener.ListenerHandler;
import org.luckyraven.keystone.npc.NpcSupport;
import org.luckyraven.gangland.core.downed.PlayerDownedEvent;
import org.luckyraven.gangland.core.money.MoneyFormula;
import org.luckyraven.keystone.util.ChatUtil;
import org.luckyraven.keystone.util.NumberUtil;
import org.luckyraven.gangland.data.placeholder.worker.GanglandPlaceholder;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.economy.EconomyHandler;
import org.luckyraven.keystone.economy.bank.Bank;
import org.luckyraven.gangland.file.configuration.Messages;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserManager;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@CustomLog
@ListenerHandler
public class PlayerDeathListener implements Listener {

	private static final long DEATH_DEDUP_WINDOW_MS = 500L;

	private final UserManager<Player>                       userManager;
	private final GanglandPlaceholder                       placeholder;
	private final BankTiers                                 bankTiers;
	private final Map<UUID, Long>                           recentDeaths      = new ConcurrentHashMap<>();
	private final Set<UUID>                                 downedBroadcasted = ConcurrentHashMap.newKeySet();
	/**
	 * Hospital.Enable: the bill taken at a down (clamped to the wallet then, so emptying the wallet while downed
	 * dodges nothing), reported when the player stands up.
	 */
	private final Map<UUID, BigDecimal>                     pendingBills      = new ConcurrentHashMap<>();

	public PlayerDeathListener(@Qualifier("online") UserManager<Player> userManager,
	                           GanglandPlaceholder placeholder,
	                           BankTiers bankTiers) {
		this.userManager       = userManager;
		this.placeholder       = placeholder;
		this.bankTiers         = bankTiers;
	}

	@EventHandler(priority = EventPriority.LOWEST)
	public void onPlayerDeath(PlayerDeathEvent event) {
		if (NpcSupport.isNpc(event.getEntity())) {
			event.setDeathMessage(null);
			return;
		}

		Player player = event.getEntity();
		UUID   uuid   = player.getUniqueId();
		long   now    = System.currentTimeMillis();

		// a downed player who really dies pays once: the bill taken at the down is that payment
		BigDecimal pending = pendingBills.remove(uuid);

		Long lastDeath = recentDeaths.get(uuid);
		if (lastDeath != null && now - lastDeath < DEATH_DEDUP_WINDOW_MS) {
			if (pending != null) {
				User<Player> dying = userManager.getUser(player);
				if (dying != null) report(dying, pending);
			}
			// Always suppress: either the downed handler already broadcast, or the FRESH path
			// already ran for this death (e.g. duplicate PlayerDeathEvent from vanilla Player.attack
			// re-checking HP<=0 after MeleeAction's target.damage() killed the entity).
			downedBroadcasted.remove(uuid);
			event.setDeathMessage(null);
			return;
		}
		pruneRecentDeaths(now);
		recentDeaths.put(uuid, now);

		User<Player> user = userManager.getUser(player);

		if (user == null) return;

		// when a player dies, the death counter increases
		user.setDeaths(user.getDeaths() + 1);

		// punish the player if they die; if commands ran, skip direct money deduction
		if (pending != null) report(user, pending);
		else if (!handleCommandExecution(user, player)) {
			// take money from their balance (NOT THEIR BANK)
			handleMoney(user);
		}

		// change the death message according to the killing method (always runs)
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
		pruneRecentDeaths(now);
		recentDeaths.put(uuid, now);

		User<Player> user = userManager.getUser(player);

		if (user == null) return;

		user.setDeaths(user.getDeaths() + 1);

		if (!handleCommandExecution(user, player)) {
			if (Settings.isHospitalEnabled()) {
				// one bill: taken now (the formula still sees the pre-reset wanted level), reported on getting up
				BigDecimal taken = take(user, quote(user));
				if (taken.signum() > 0) pendingBills.put(uuid, taken);
			} else {
				handleMoney(user);
			}
		}

		broadcastDeathMessage(player);
	}

	/** The ward bill taken at the down is reported when he stands up (a respawn, a release from the ward). */
	@EventHandler(priority = EventPriority.MONITOR)
	public void onPlayerUndowned(PlayerUndownedEvent event) {
		reportPending(event.getPlayer(), "RESPAWN");
	}

	/** Quitting while downed: the bill was already taken at the down; forget the entry. */
	@EventHandler(priority = EventPriority.LOWEST)
	public void onQuit(PlayerQuitEvent event) {
		reportPending(event.getPlayer(), "QUIT");
	}

	private void reportPending(Player player, String at) {
		BigDecimal bill = pendingBills.remove(player.getUniqueId());
		if (bill == null) return;

		User<Player> user = userManager.getUser(player);
		if (user == null) return;

		log.debug("WARD_BILL {} amount={} at={}", player.getName(), bill, at);
		report(user, bill);
	}

	/** US-33/WB-17: entries only ever grew; drop the ones outside the dedup window on every put. */
	private void pruneRecentDeaths(long now) {
		recentDeaths.values().removeIf(at -> now - at >= DEATH_DEDUP_WINDOW_MS);
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

	// package-private: test seam
	void handleMoney(User<Player> user) {
		BigDecimal bill = quote(user);
		if (bill.signum() > 0) log.debug("WARD_BILL {} amount={} at=DEATH", user.getUser().getName(), bill);
		charge(user, bill);
	}

	/**
	 * What dying costs this user right now: the {@code Lose_Money} formula read at this moment, less the bank-account
	 * insurance. Zero for no loss. Moves no money.
	 */
	BigDecimal quote(User<Player> user) {
		// false = dying costs nothing and pays nothing
		if (!Settings.isDeathLoseMoney()) return BigDecimal.ZERO;

		double balance = user.getEconomy().getAmount().doubleValue();
		double deduct = MoneyFormula.evaluate(Settings.getDeathLoseMoneyFormula(), MoneyFormula.userVariables(user),
		                                      balance * 0.15);

		// ignore it if there was no money to be deducted
		if (deduct == 0) return BigDecimal.ZERO;

		// Bank-account insurance: having an account softens the loss by the tier's deathLossDiscount. Applied
		// post-formula so admin-authored Death.Money.Formula expressions don't have to know about the bank.
		double discount = bankInsuranceDiscount(user);
		if (discount > 0) {
			deduct *= Math.max(0D, 1D - discount);
		}
		if (deduct <= 0D) return BigDecimal.ZERO;

		return Currency.of(deduct);
	}

	/** Takes the quoted bill from the wallet (clamped to it, never throws) and tells the player. */
	void charge(User<Player> user, BigDecimal bill) {
		report(user, take(user, bill));
	}

	/** Takes the quoted bill from the wallet, clamped to it; returns what was taken. Never throws. */
	private static BigDecimal take(User<Player> user, BigDecimal bill) {
		if (bill == null || bill.signum() <= 0) return BigDecimal.ZERO;

		return user.withdraw(Currency.of(bill.doubleValue()));
	}

	/** Tells the player what was taken; nothing for nothing. */
	private void report(User<Player> user, BigDecimal taken) {
		if (taken.signum() == 0) return;

		// inform the player
		String amount = NumberUtil.valueFormat(taken.doubleValue());

		if (Settings.isHospitalEnabled()) {
			user.sendMessage(Messages.DEATH_WARD_BILL.toString()
			                                         .replace("%money_symbol%", Settings.getMoneySymbol())
			                                         .replace("%amount%", amount));
			return;
		}

		user.sendMessage(ChatUtil.color("&3Death penalty: &c&l-" + Settings.getMoneySymbol() + amount));
	}

	private double bankInsuranceDiscount(User<Player> user) {
		Bank bank = user.getBank();
		if (bank == null) return 0D;
		BankTierView tier = bankTiers.tierFor(bank);
		return tier == null ? 0D : tier.deathLossDiscount();
	}

	private void changeDeathMessage(PlayerDeathEvent event, Player player) {
		// Citizens NPC killers are not Player instances, so getKiller() returns null and the
		// vanilla "[NpcName] slain" message would show. Suppress it explicitly.
		EntityDamageEvent cause = player.getLastDamageCause();
		if (cause instanceof EntityDamageByEntityEvent byEntity
		    && NpcSupport.isNpc(byEntity.getDamager())) {
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

		// The weapon-owned contributor hook left with the weapon module: Bartizan sets its own weapon-kill death
		// message from its own PlayerDeathEvent listener at EventPriority.HIGH (after this one at LOWEST). The core
		// keeps a generic %killer%/%victim% template so every player kill is still announced - including the
		// downed path, where the lethal damage is cancelled and PlayerDeathEvent never fires (T-C4, gate-C review B1).
		String template = getRandomGlobalMessage(Messages.DEATH_GLOBAL.toStringList());
		if (template == null) return null;

		return ChatUtil.color(template.replace("%killer%", killer.getName())
		                              .replace("%victim%", player.getName()));
	}

	private @Nullable String getRandomGlobalMessage(List<String> globalMessages) {
		if (globalMessages.isEmpty()) return null;
		return globalMessages.get(new Random().nextInt(globalMessages.size()));
	}

}
