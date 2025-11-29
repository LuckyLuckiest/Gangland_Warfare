package me.luckyraven.feature.bounty;

import lombok.AccessLevel;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;
import me.luckyraven.util.timer.RepeatingTimer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

@Data
public class Bounty {

	@Getter(AccessLevel.NONE)
	@Setter(AccessLevel.NONE)
	private final Map<CommandSender, Double> userSetBounty;

	@Setter(AccessLevel.NONE)
	private RepeatingTimer repeatingTimer;

	private double amount;
	private double baseAmount;
	private double levelMultiplier;

	public Bounty(double baseAmount, double levelMultiplier) {
		this.amount          = 0D;
		this.userSetBounty   = new HashMap<>();
		this.baseAmount      = baseAmount;
		this.levelMultiplier = levelMultiplier;
	}

	public RepeatingTimer createTimer(JavaPlugin plugin, long seconds, Consumer<RepeatingTimer> timer) {
		stopTimer();

		this.repeatingTimer = new RepeatingTimer(plugin, seconds * 20L, timer);

		return repeatingTimer;
	}

	public boolean hasBounty() {
		return amount != 0D;
	}

	public void resetBounty() {
		this.amount = 0D;

		if (this.repeatingTimer != null) {
			this.repeatingTimer.stop();
		}

		this.userSetBounty.clear();
	}

	public int size() {
		return userSetBounty.size();
	}

	public double getSetAmount(CommandSender sender) {
		return userSetBounty.get(sender);
	}

	public void addBounty(CommandSender sender, double amount, int userLevel) {
		double scaledAmount = calculateLevelScaledBounty(amount, userLevel);

		addBounty(sender, scaledAmount);
	}

	public void addBounty(CommandSender sender, double amount) {
		if (userSetBounty.containsKey(sender)) {
			Double value          = userSetBounty.get(sender);
			double primitiveValue = value == null ? 0D : value;

			userSetBounty.put(sender, primitiveValue + amount);
		} else {
			userSetBounty.put(sender, amount);
		}

		this.amount += amount;
	}

	public double calculateLevelScaledBounty(double baseAmount, int userLevel) {
		double levelAdjustment = userLevel * levelMultiplier / 10;
		return baseAmount * (1 + levelAdjustment);
	}

	public double getAutoBountyIncrease(int userLevel, int wantedLevel) {
		double baseBounty = baseAmount * wantedLevel;

		return calculateLevelScaledBounty(baseBounty, userLevel);
	}

	public void removeBounty(CommandSender sender) {
		double amount = userSetBounty.get(sender);

		userSetBounty.remove(sender);

		this.amount = Math.max(0D, this.amount - amount);
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
		return String.format("Bounty{amount=%.2f}", amount);
	}

}
