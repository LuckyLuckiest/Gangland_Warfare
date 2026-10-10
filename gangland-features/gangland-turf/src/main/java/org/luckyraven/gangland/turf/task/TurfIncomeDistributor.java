package org.luckyraven.gangland.turf.task;

import lombok.CustomLog;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.gangland.core.user.UserLookupContract;
import org.luckyraven.gangland.file.configuration.Settings;
import org.luckyraven.gangland.gang.Gang;
import org.luckyraven.gangland.gang.contract.GangLookupContract;
import org.luckyraven.gangland.turf.capture.TurfOwnership;
import org.luckyraven.gangland.turf.config.TurfMessages;
import org.luckyraven.gangland.turf.config.TurfRules;
import org.luckyraven.gangland.turf.data.Turf;
import org.luckyraven.gangland.turf.events.TurfOwnerChangedEvent;
import org.luckyraven.gangland.turf.log.TurfEventBuffer;
import org.luckyraven.gangland.turf.manager.TurfManager;
import org.luckyraven.gangland.turf.map.TurfHudTask;
import org.luckyraven.gangland.turf.powerups.ActiveBuffManager;
import org.luckyraven.gangland.turf.powerups.EffectType;
import org.luckyraven.keystone.bean.BeanLifecycle;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.LongSupplier;
import java.util.function.Supplier;

/**
 * Periodic task (default every 10 minutes) that pays each turf's income into its owning gang's bank. Only an ACTIVE
 * gang (a member online within {@code Income.Idle_Hours}) is paid; the buff multiplier is capped at
 * {@code Income.Max_Multiplier}; a gang over its turf cap is paid only for its oldest turfs. Each paying turf also
 * grants the gang XP, each online member gets one action-bar line per payout, and the payout is kept for the
 * while-you-were-away digest. Orphaned turfs whose owning gang no longer exists are auto-freed through
 * {@link TurfOwnership}. The timer is stopped on plugin shutdown.
 */
@CustomLog
public final class TurfIncomeDistributor implements BeanLifecycle {

	private static final int    PRIORITY        = 9;
	private static final long   MILLIS_PER_HOUR = 3_600_000L;

	private final JavaPlugin                             plugin;
	private final TurfManager                            turfs;
	private final GangLookupContract                     gangs;
	private final UserLookupContract                     users;
	private final ActiveBuffManager                      buffs;
	private final TurfOwnership                          ownership;
	private final long                                   intervalTicks;
	private final TurfRules                              rules;
	private final TurfEventBuffer                        buffer;
	private final TurfMessages                           text;
	private final TurfHudTask.LineSender                 sender;
	private final Supplier<Collection<? extends Player>> players;
	private final LongSupplier                           clock;

	private BukkitTask task;

	public TurfIncomeDistributor(JavaPlugin plugin,
	                             TurfManager turfs,
	                             GangLookupContract gangs,
	                             UserLookupContract users,
	                             ActiveBuffManager buffs,
	                             TurfOwnership ownership,
	                             long intervalTicks,
	                             TurfRules rules,
	                             TurfEventBuffer buffer,
	                             TurfMessages text,
	                             TurfHudTask.LineSender sender,
	                             Supplier<Collection<? extends Player>> players,
	                             LongSupplier clock) {
		this.plugin        = plugin;
		this.turfs         = turfs;
		this.gangs         = gangs;
		this.users         = users;
		this.buffs         = buffs;
		this.ownership     = ownership;
		this.intervalTicks = intervalTicks;
		this.rules         = rules;
		this.buffer        = buffer;
		this.text          = text;
		this.sender        = sender;
		this.players       = players;
		this.clock         = clock;
	}

	/**
	 * True when a member of {@code gang} was online within {@code Income.Idle_Hours} of {@code now}. A gang nobody has
	 * ever logged in as (lastMemberOnlineAt 0) is never active.
	 */
	public static boolean isActive(Gang gang, long now, TurfRules rules) {
		long last = gang.getLastMemberOnlineAt();
		return last > 0 && now - last <= rules.getIncomeIdleHours() * MILLIS_PER_HOUR;
	}

	public void start() {
		if (task != null) {
			return;
		}
		task = Bukkit.getScheduler().runTaskTimer(plugin, this::distribute, intervalTicks, intervalTicks);
	}

	public void stop() {
		if (task != null) {
			task.cancel();
			task = null;
		}
	}

	@Override
	public void onShutdown() {
		stop();
	}

	/** True when at least one turf has an owner. */
	private boolean hasOwnedTurf() {
		for (Turf turf : turfs.getAll()) {
			if (!turf.isUnclaimed()) {
				return true;
			}
		}
		return false;
	}

	/** Package-private for tests (W2-K). */
	void distribute() {
		// T-196: an empty gang cache with owned turfs means the gang table did not load; releasing now would free every turf.
		if (gangs.getAll().isEmpty() && hasOwnedTurf()) {
			log.warn("No gangs are loaded but turfs are owned - skipping this {} (is the gang table readable?)",
			         "income payout");
			return;
		}
		Map<Integer, Gang>        owners = new TreeMap<>();
		Map<Integer, List<Turf>>  owned  = new TreeMap<>();
		for (Turf turf : new ArrayList<>(turfs.getAll())) {
			if (turf.isUnclaimed()) {
				continue;
			}
			Gang gang = gangs.findById(turf.getOwnerGangId());
			if (gang == null) {
				log.info("Turf {} owned by missing gang {} - auto-releasing", turf.getId(), turf.getOwnerGangId());
				ownership.setOwner(turf, null, TurfOwnerChangedEvent.Cause.ORPHANED);
				continue;
			}
			owners.put(gang.getId(), gang);
			owned.computeIfAbsent(gang.getId(), id -> new ArrayList<>()).add(turf);
		}
		long now = clock.getAsLong();
		for (Map.Entry<Integer, List<Turf>> entry : owned.entrySet()) {
			payGang(owners.get(entry.getKey()), entry.getValue(), now);
		}
	}

	/** Pays one gang for its oldest in-cap turfs, if it is active. */
	private void payGang(Gang gang, List<Turf> owned, long now) {
		if (!isActive(gang, now, rules)) {
			return;
		}
		owned.sort(Comparator.comparingLong(Turf::getLastCaptureTimestamp).thenComparingInt(Turf::getId));
		int        cap   = rules.cap(gang.getLevel().getLevelValue());
		BigDecimal sum   = BigDecimal.ZERO;
		int        paid  = 0;
		for (Turf turf : owned.subList(0, Math.min(cap, owned.size()))) {
			double     boost      = buffs.effectiveMultiplier(turf.getId(), EffectType.INCOME_MULTIPLIER);
			BigDecimal multiplier = BigDecimal.valueOf(Math.min(boost, rules.getMaxIncomeMultiplier()));
			BigDecimal payout     = turf.getIncomeAmount().multiply(multiplier).setScale(2, RoundingMode.HALF_UP);
			gang.getEconomy().depositAmount(payout);
			gangs.addExperience(gang, rules.getGangXpPerPayout());
			sum = sum.add(payout);
			paid++;
		}
		if (sum.signum() <= 0) {
			return;
		}
		buffer.recordIncome(gang.getId(), now, sum);
		String line = text.text("Income.Action_Bar",
		                        "&aTurf income +%money_symbol%%amount% &7(%count% turfs) - bank %money_symbol%%bank%",
		                        "money_symbol", Settings.getMoneySymbol(),
		                        "amount", Settings.formatAmount(sum),
		                        "count", paid,
		                        "bank", Settings.formatAmount(gang.getEconomy().getAmount()));
		for (Player player : players.get()) {
			User<Player> user = users.findByPlayer(player);
			if (user != null && user.hasGang() && user.getGangId() == gang.getId()) {
				sender.send(player, line, PRIORITY);
			}
		}
	}
}
