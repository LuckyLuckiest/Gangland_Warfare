package org.luckyraven.gangland.gang.bounty;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.luckyraven.gangland.core.timer.RepeatingTimer;
import org.luckyraven.gangland.economy.Currency;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Data
public class Bounty {

	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private final Map<CommandSender, BigDecimal> userSetBounty;

	@Setter(AccessLevel.NONE)
	private RepeatingTimer repeatingTimer;

	private BigDecimal amount;
	private BigDecimal baseAmount;
	private double     levelMultiplier;

	public Bounty(BigDecimal baseAmount, double levelMultiplier) {
		this.amount          = Currency.ZERO;
		this.userSetBounty   = new HashMap<>();
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
	}

	public int size() {
		return userSetBounty.size();
	}

	public BigDecimal getSetAmount(CommandSender sender) {
		return userSetBounty.get(sender);
	}

	public void addBounty(CommandSender sender, BigDecimal amount, int userLevel) {
		BigDecimal scaledAmount = calculateLevelScaledBounty(amount, userLevel);

		addBounty(sender, scaledAmount);
	}

	public void addBounty(CommandSender sender, BigDecimal amount) {
		BigDecimal normalised = Currency.of(amount);

		BigDecimal previous = userSetBounty.getOrDefault(sender, Currency.ZERO);
		userSetBounty.put(sender, previous.add(normalised));

		this.amount = this.amount.add(normalised);
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
