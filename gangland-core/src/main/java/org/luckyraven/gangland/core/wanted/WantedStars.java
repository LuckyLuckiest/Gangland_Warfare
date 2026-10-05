package org.luckyraven.gangland.core.wanted;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.Nullable;
import org.luckyraven.gangland.core.events.wanted.WantedEvent;
import org.luckyraven.gangland.core.money.MoneyFormula;
import org.luckyraven.gangland.core.user.User;
import org.luckyraven.keystone.economy.Currency;
import org.luckyraven.keystone.timer.Timer;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BooleanSupplier;

/**
 * The one place stars are raised, dropped and restored (CONTRACTS C3). Every call names a {@link WantedCause}, which
 * reaches the wanted events through {@link Wanted#setLevel(int, WantedCause)}; raises (re)start the Repeating_Timer
 * safety-net clock, a SYNC timer; drops charge the star-drop price only after the level change succeeded.
 * <p>
 * Also the holder for the module-installed {@link WantedDecayPolicy} (cops-n-crooks' evasion clock) and the HUD's
 * star-chat suppressor. One instance, the core bean {@code DataConfig.wantedStars(..)}.
 */
public final class WantedStars {

	private final JavaPlugin     plugin;
	private final WantedSettings settings;

	private volatile @Nullable WantedDecayPolicy policy;
	private volatile @Nullable BooleanSupplier   starChatSuppressor;

	public WantedStars(JavaPlugin plugin, WantedSettings settings) {
		this.plugin   = plugin;
		this.settings = settings;
	}

	/** Installs the decay policy; {@code null} uninstalls it. */
	public void installDecayPolicy(@Nullable WantedDecayPolicy policy) {
		this.policy = policy;
	}

	/** True while an installed policy drives this player's decay, so the safety-net tick does nothing. */
	public boolean isDecayHandled(Wanted wanted) {
		WantedDecayPolicy current = policy;
		Player            owner   = wanted.getOwner();

		return current != null && owner != null && current.handlesDecay(owner, wanted);
	}

	/** Hides the star chat line while {@code whileTrue} answers true (the HUD's star card); {@code null} removes it. */
	public void suppressStarChat(@Nullable BooleanSupplier whileTrue) {
		this.starChatSuppressor = whileTrue;
	}

	public boolean isStarChat() {
		BooleanSupplier suppressor = starChatSuppressor;

		return suppressor == null || !suppressor.getAsBoolean();
	}

	/**
	 * THE star-raising method. Adds {@code stars} (> 0) with {@code origin}, clamped to the maximum, then - while the
	 * player is wanted and the timer is enabled - (re)starts the safety-net clock at the new level's interval. At the
	 * maximum nothing is added but the clock still restarts, as handleWanted does today; a cancelled change starts
	 * nothing. Main thread only.
	 *
	 * @return the stars actually added: 0 when {@code stars <= 0}, already at the maximum, or the change was cancelled
	 */
	public int raise(WantedContext context, int stars, WantedCause origin) {
		if (stars <= 0) return 0;

		Wanted wanted = context.getWanted();
		int    before = wanted.getLevel();

		if (before < wanted.getMaxLevel()) {
			wanted.setLevel(before + stars, origin);
			if (wanted.getLevel() == before) return 0;
		}

		startDecayClock(context);

		return wanted.getLevel() - before;
	}

	/**
	 * Login restore. Any thread: hops to the main thread first. Sets the level with {@link WantedCause#RESTORE}, then
	 * starts the clock when the level is above 0, the owner is online and the timer is enabled.
	 */
	public void restore(WantedContext context, int level) {
		if (!Bukkit.isPrimaryThread()) {
			Bukkit.getScheduler().runTask(plugin, () -> restore(context, level));
			return;
		}

		Wanted wanted = context.getWanted();
		wanted.setLevel(level, WantedCause.RESTORE);

		Player owner = wanted.getOwner();
		if (wanted.getLevel() > 0 && owner != null && owner.isOnline()) startDecayClock(context);
	}

	/**
	 * Lowers by {@code stars} with {@code cause}. The level changes first; a cancelled change charges and sends
	 * nothing. Then the star-drop price is withdrawn once, the decreased message for the NEW level is sent, then the
	 * money-loss line when anything was charged; the clock stops when the level reached 0. Off the main thread it
	 * re-schedules itself there and returns 0.
	 *
	 * @return the stars actually dropped
	 */
	public int drop(WantedContext context, int stars, WantedCause cause) {
		if (!Bukkit.isPrimaryThread()) {
			Bukkit.getScheduler().runTask(plugin, () -> drop(context, stars, cause));
			return 0;
		}

		Wanted wanted = context.getWanted();
		int    before = wanted.getLevel();
		int    target = Math.max(0, before - Math.max(0, stars));

		if (target == before) return 0;

		wanted.setLevel(target, cause);

		int after = wanted.getLevel();
		if (after == before) return 0;

		BigDecimal price = Currency.ZERO;
		for (int level = before; level > after; level--) {
			price = price.add(starPrice(context, level));
		}

		BigDecimal charged = price.signum() != 0 ? context.withdraw(price) : Currency.ZERO;

		String message = settings.getWantedDecreasedMessageTemplate()
		                         .replace("%level%", String.valueOf(after))
		                         .replace("%stars%", Wanted.buildStars(after, wanted.getMaxLevel()));
		context.sendMessage(message);

		if (charged.signum() != 0) {
			context.sendMessage(settings.formatMoneyLoss(charged));
		}

		if (after == 0) wanted.stopTimer();

		return before - after;
	}

	/**
	 * Starts (replacing any running one) the safety-net clock when the timer is enabled and the player is wanted: a
	 * SYNC timer firing a sync {@link WantedEvent}, since every tick touches events, the economy and messages.
	 *
	 * @return the started timer, or {@code null} when none was started
	 */
	public @Nullable Timer startDecayClock(WantedContext context) {
		Wanted wanted = context.getWanted();
		if (!settings.isTimerEnabled() || !wanted.isWanted()) return null;

		Timer timer = new WantedExecutor(plugin, new WantedEvent(false, wanted), context, settings, this).createTimer();
		timer.start(false);

		return timer;
	}

	/**
	 * The price of the star that falls from {@code level}: nothing unless {@code Take_Money.Enable}, else the admin
	 * formula, falling back to Amount x Multiplier^level when it is broken.
	 */
	private BigDecimal starPrice(WantedContext context, int level) {
		if (!settings.isTakeMoneyEnabled()) return Currency.ZERO;

		BigDecimal amount = settings.getTakeMoneyAmount();
		if (amount.signum() <= 0) return Currency.ZERO;

		double multiplier = settings.getTakeMoneyMultiplier();
		double fallback   = Currency.toDouble(amount) * Math.pow(multiplier, level);

		Map<String, Double> variables = context instanceof User<?> user ? MoneyFormula.userVariables(user) : new HashMap<>();
		variables.put("amount", Currency.toDouble(amount));
		variables.put("multiplier", multiplier);
		variables.put("wanted", (double) level);

		return Currency.of(MoneyFormula.evaluate(settings.getTakeMoneyFormula(), variables, fallback));
	}
}
