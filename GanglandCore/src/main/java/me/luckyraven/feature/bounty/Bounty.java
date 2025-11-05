package me.luckyraven.feature.bounty;

import lombok.Getter;
import lombok.Setter;
import me.luckyraven.timer.RepeatingTimer;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class Bounty {

	private final Map<CommandSender, Double> userSetBounty;

	private @Getter
	@Setter         double         amount;
	private @Getter RepeatingTimer repeatingTimer;

	public Bounty() {
		this.amount        = 0D;
		this.userSetBounty = new HashMap<>();
	}

	public RepeatingTimer createTimer(JavaPlugin plugin, long seconds, Consumer<RepeatingTimer> timer) {
		this.repeatingTimer = new RepeatingTimer(plugin, seconds * 20L, timer);
		return repeatingTimer;
	}

	public boolean hasBounty() {
		return amount != 0D;
	}

	public void resetBounty() {
		this.amount = 0D;
		this.userSetBounty.clear();
	}

	public int size() {
		return userSetBounty.size();
	}

	public double getSetAmount(CommandSender sender) {
		return userSetBounty.get(sender);
	}

	public void addBounty(CommandSender sender, double amount) {
		if (userSetBounty.containsKey(sender)) {
			Double value          = userSetBounty.get(sender);
			double primitiveValue = value == null ? 0D : value;
			userSetBounty.put(sender, primitiveValue + amount);
		} else userSetBounty.put(sender, amount);
		this.amount += amount;
	}

	public void removeBounty(CommandSender sender) {
		double amount = userSetBounty.get(sender);
		userSetBounty.remove(sender);
		this.amount = Math.max(0D, this.amount - amount);
	}

	public boolean containsBounty(CommandSender sender) {
		return userSetBounty.containsKey(sender);
	}

	@Override
	public String toString() {
		return String.format("Bounty{amount=%.2f}", amount);
	}

}
