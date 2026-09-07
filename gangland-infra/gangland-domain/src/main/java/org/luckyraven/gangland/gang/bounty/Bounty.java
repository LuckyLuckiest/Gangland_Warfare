package org.luckyraven.gangland.gang.bounty;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.keystone.timer.RepeatingTimer;
import org.luckyraven.keystone.economy.Currency;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Data
public class Bounty {

	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private final Map<CommandSender, BigDecimal> userSetBounty;

	/**
	 * What each contributor actually <em>paid</em> for their entry in {@link #userSetBounty}. The posted figure is
	 * level-scaled ({@link #calculateLevelScaledBounty(BigDecimal, int)}); the paid figure is not, so a refund must
	 * never be read out of {@code userSetBounty}. Keeping both is the single source of truth for WB-01.
	 */
	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private final Map<CommandSender, BigDecimal> userPaidBounty;

	@Setter(AccessLevel.NONE)
	private RepeatingTimer repeatingTimer;

	private BigDecimal amount;
	private BigDecimal baseAmount;
	private double     levelMultiplier;

	public Bounty(BigDecimal baseAmount, double levelMultiplier) {
		this.amount          = Currency.ZERO;
		this.userSetBounty   = new HashMap<>();
		this.userPaidBounty  = new HashMap<>();
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
		return userSetBounty.get(sender);
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
		return userPaidBounty.getOrDefault(sender, userSetBounty.get(sender));
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
		BigDecimal previousPosted = userSetBounty.getOrDefault(sender, Currency.ZERO);
		userSetBounty.put(sender, previousPosted.add(posted));

		BigDecimal previousPaid = userPaidBounty.getOrDefault(sender, Currency.ZERO);
		userPaidBounty.put(sender, previousPaid.add(paid));

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
		BigDecimal removed = userSetBounty.remove(sender);
		userPaidBounty.remove(sender);

		if (removed == null) return;

		BigDecimal next = this.amount.subtract(removed);
		this.amount = next.signum() < 0 ? Currency.ZERO : next;
	}

	public boolean containsBounty(CommandSender sender) {
		return userSetBounty.containsKey(sender);
	}

	public void stopTimer() {
		if (repeatingTimer == null) return;

		this.repeatingTimer.stop();
		this.repeatingTimer = null;
	}

	@Override
	public String toString() {
		return "Bounty{amount=" + amount.toPlainString() + "}";
	}

}
