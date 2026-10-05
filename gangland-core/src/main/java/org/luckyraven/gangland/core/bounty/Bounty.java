package org.luckyraven.gangland.core.bounty;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.Nullable;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.timer.RepeatingTimer;
import org.luckyraven.keystone.economy.Currency;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Consumer;

@Data
public class Bounty {

	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private final Map<String, BigDecimal> userSetBounty;

	/**
	 * What each contributor actually <em>paid</em> for their entry in {@link #userSetBounty}. The posted figure is
	 * level-scaled ({@link #calculateLevelScaledBounty(BigDecimal, int)}); the paid figure is not, so a refund must
	 * never be read out of {@code userSetBounty}. Keeping both is the single source of truth for WB-01.
	 */
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private final Map<String, BigDecimal> userPaidBounty;

	@Setter(AccessLevel.NONE)
	private RepeatingTimer repeatingTimer;

	private BigDecimal amount;
	private BigDecimal baseAmount;
	private double     levelMultiplier;

	public Bounty(BigDecimal baseAmount, double levelMultiplier) {
		this.amount          = Currency.ZERO;
		this.userSetBounty   = new LinkedHashMap<>();
		this.userPaidBounty  = new LinkedHashMap<>();
		this.baseAmount      = Currency.of(baseAmount);
		this.levelMultiplier = levelMultiplier;
	}

	public RepeatingTimer createTimer(JavaPlugin plugin, long seconds, Consumer<RepeatingTimer> timer) {
		stopTimer();

		this.repeatingTimer = new RepeatingTimer(plugin, seconds * 20L, timer);

		return repeatingTimer;
	}

	public boolean hasBounty() {
		return amount.signum() != 0;
	}

	public void resetBounty() {
		this.amount = Currency.ZERO;

		stopTimer();

		this.userSetBounty.clear();
		this.userPaidBounty.clear();
	}

	public int size() {
		return userSetBounty.size();
	}

	public BigDecimal getSetAmount(CommandSender sender) {
		return userSetBounty.get(posterId(sender));
	}

	/**
	 * The amount the sender actually paid for their contribution — what a refund must return. Falls back to the
	 * posted figure for entries added before a paid figure was ever recorded.
	 *
	 * @param sender the contributor
	 *
	 * @return the paid amount, or {@code null} when the sender has no ledger entry at all
	 */
	public BigDecimal getPaidAmount(CommandSender sender) {
		return userPaidBounty.getOrDefault(posterId(sender), userSetBounty.get(posterId(sender)));
	}

	public void addBounty(CommandSender sender, BigDecimal amount, int userLevel) {
		BigDecimal paid = Currency.of(amount);

		recordBounty(sender, calculateLevelScaledBounty(paid, userLevel), paid);
	}

	public void addBounty(CommandSender sender, BigDecimal amount) {
		BigDecimal normalised = Currency.of(amount);

		recordBounty(sender, normalised, normalised);
	}

	/**
	 * Books one contribution: {@code posted} goes on the target's head, {@code paid} is what the contributor was
	 * charged for it. The two differ whenever the bounty is level-scaled.
	 */
	private void recordBounty(CommandSender sender, BigDecimal posted, BigDecimal paid) {
		String id = posterId(sender);

		userSetBounty.merge(id, posted, BigDecimal::add);
		userPaidBounty.merge(id, paid, BigDecimal::add);

		this.amount = this.amount.add(posted);
	}

	public BigDecimal calculateLevelScaledBounty(BigDecimal baseAmount, int userLevel) {
		double factor = 1 + userLevel * levelMultiplier / 10.0;
		return Currency.multiply(baseAmount, factor);
	}

	public BigDecimal getAutoBountyIncrease(int userLevel, int wantedLevel) {
		BigDecimal baseBounty = Currency.multiply(baseAmount, wantedLevel);

		return calculateLevelScaledBounty(baseBounty, userLevel);
	}

	public void removeBounty(CommandSender sender) {
		String     id      = posterId(sender);
		BigDecimal removed = userSetBounty.remove(id);
		userPaidBounty.remove(id);

		if (removed == null) return;

		BigDecimal next = this.amount.subtract(removed);
		this.amount = next.signum() < 0 ? Currency.ZERO : next;
	}

	public boolean containsBounty(CommandSender sender) {
		return userSetBounty.containsKey(posterId(sender));
	}

	public void stopTimer() {
		if (repeatingTimer == null) return;

		this.repeatingTimer.stop();
		this.repeatingTimer = null;
	}

	/**
	 * A stable ledger key: a relogged player is a new {@code Player} object with the same UUID, so the object itself
	 * can never find its own entry again (WB-12).
	 */
	public static String posterId(CommandSender sender) {
		if (sender instanceof Player player) return player.getUniqueId().toString();
		if (sender instanceof ConsoleCommandSender) return "console";

		return String.valueOf(sender.getName());
	}

	/** The escrow players actually put up: the sum of the PAID figures. */
	public BigDecimal getPostedAmount() {
		return userPaidBounty.values().stream().reduce(Currency.ZERO, BigDecimal::add);
	}

	/** The server-made part of the total (auto bounty, kill bounty, timer growth). */
	public BigDecimal getNotoriety() {
		BigDecimal rest = amount.subtract(getPostedAmount());

		return rest.signum() < 0 ? Currency.ZERO : rest;
	}

	public void addNotoriety(BigDecimal x) {
		if (x.signum() > 0) this.amount = this.amount.add(x);
	}

	/** Takes the posted escrow off the head (it is being paid out); notoriety stays. */
	public BigDecimal claimPosted() {
		BigDecimal posted = getPostedAmount();
		BigDecimal rest   = amount.subtract(posted);

		this.amount = rest.signum() < 0 ? Currency.ZERO : rest;
		this.userSetBounty.clear();
		this.userPaidBounty.clear();

		return posted;
	}

	/** {@code "id=posted:paid"} joined by {@code ;}, empty when there is no ledger. */
	public String serializeLedger() {
		StringBuilder out = new StringBuilder();

		userSetBounty.forEach((id, posted) -> {
			if (out.length() > 0) out.append(';');

			out.append(id).append('=').append(posted.toPlainString()).append(':')
			   .append(userPaidBounty.getOrDefault(id, posted).toPlainString());
		});

		return out.toString();
	}

	/**
	 * Rebuilds the ledger; call after {@link #setAmount}. {@code null} is a row saved before the ledger existed, so
	 * the whole bounty counts as posted once; bad entries are skipped.
	 */
	public void restoreLedger(@Nullable String serialized) {
		userSetBounty.clear();
		userPaidBounty.clear();

		if (serialized == null) {
			if (amount.signum() > 0) {
				userSetBounty.put("legacy", amount);
				userPaidBounty.put("legacy", amount);
			}
			return;
		}

		for (String entry : serialized.split(";")) {
			int eq    = entry.indexOf('=');
			int colon = entry.indexOf(':', eq + 1);

			if (eq <= 0 || colon < 0) continue;

			try {
				BigDecimal posted = new BigDecimal(entry.substring(eq + 1, colon));
				BigDecimal paid   = new BigDecimal(entry.substring(colon + 1));

				userSetBounty.put(entry.substring(0, eq), posted);
				userPaidBounty.put(entry.substring(0, eq), paid);
			} catch (NumberFormatException ignored) {
				// skipped: a damaged entry must not cost the rest of the ledger
			}
		}
	}

	@Override
	public String toString() {
		return "Bounty{amount=" + amount.toPlainString() + "}";
	}

}
